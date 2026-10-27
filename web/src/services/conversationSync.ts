import type { Id } from '../types/chat'
import type { OfflineMessage, SyncResult } from '../types/api'

interface SyncPorts {
  capture(): { key: string; isCurrent(): boolean }
  cursor(conversation: string): Id
  fetch(conversation: string, after: Id): Promise<SyncResult>
  apply(conversation: string, messages: OfflineMessage[]): Promise<void>
  commit(conversation: string, cursor: Id): void
}

function sequence(value: Id): bigint {
  if ((typeof value === 'number' && !Number.isSafeInteger(value)) || !/^\d+$/.test(String(value))) {
    throw new Error('Invalid sequence: use an exact decimal string')
  }
  return BigInt(value)
}

/** One drain per session/conversation. Only a committed local batch may advance its cursor. */
export class ConversationSync {
  private flights = new Map<string, { target: bigint; promise: Promise<void> }>()
  constructor(private readonly ports: SyncPorts) {}

  run(conversation: string, serverSeqId: Id): Promise<void> {
    let target: bigint
    try { target = sequence(serverSeqId) } catch (error) { return Promise.reject(error) }
    const session = this.ports.capture(), key = `${session.key}:${conversation}`
    const existing = this.flights.get(key)
    if (existing) { if (target > existing.target) existing.target = target; return existing.promise }
    const flight = { target, promise: Promise.resolve() }
    flight.promise = Promise.resolve().then(async () => {
      let after = String(this.ports.cursor(conversation))
      if (sequence(after) >= flight.target) return
      while (session.isCurrent()) {
        const page = await this.ports.fetch(conversation, after)
        if (!session.isCurrent()) return
        if (!page.messages.length) {
          if (page.hasMore) throw new Error('Empty sync page with more history')
          return // Kafka/DB visibility may lag a hint. A later hint retries from the same cursor.
        }
        const next = String(page.nextSeqId)
        const last = page.messages[page.messages.length - 1].seqId
        let previous = sequence(after)
        for (const message of page.messages) {
          if (message.seqId == null || sequence(message.seqId) <= previous) throw new Error('Unordered sync cursor')
          previous = sequence(message.seqId)
        }
        if (sequence(page.nextSeqId) <= sequence(after) || last == null || sequence(page.nextSeqId) !== sequence(last)) {
          throw new Error('Sync cursor did not advance to the final message')
        }
        await this.ports.apply(conversation, page.messages)
        if (!session.isCurrent()) return
        this.ports.commit(conversation, next)
        after = next
        if (!page.hasMore && BigInt(after) >= flight.target) return
      }
    }).finally(() => { if (this.flights.get(key) === flight) this.flights.delete(key) })
    this.flights.set(key, flight)
    return flight.promise
  }
}
