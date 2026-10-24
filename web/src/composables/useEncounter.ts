import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import store from '../store'
import { actEncounter, getEncounter } from '../api/social'
import type { EncounterState, EncounterAction } from '../types/social'

export function useEncounter() {
  const room = ref<EncounterState>({ state: 'IDLE', messages: [] })
  const error = ref(''), busy = ref(false)
  let timer: ReturnType<typeof setTimeout> | undefined
  let controller = new AbortController(), generation = 0, revision = 0, disposed = false
  function merge(next: EncounterState) {
    const previous = next.roomId && next.roomId === room.value.roomId && next.reason !== 'BLOCKED' ? room.value.messages : []
    const messages = new Map(previous.map(message => [message.seq, message]))
    for (const message of next.messages || []) messages.set(message.seq, message)
    room.value = { ...next, messages: [...messages.values()].sort((a, b) => a.seq - b.seq) }
  }
  async function poll() {
    if (busy.value) { timer = setTimeout(poll, 1200); return }
    const current = generation
    const requestRevision = revision
    try {
      const last = room.value.messages.at(-1)?.seq || 0
      const next = await getEncounter(last, controller.signal)
      if (!disposed && current === generation && requestRevision === revision) { merge(next); error.value = '' }
    } catch (failure) { if (!disposed && current === generation && requestRevision === revision) error.value = String((failure as Error).message) }
    finally { if (!disposed && current === generation) timer = setTimeout(poll, error.value ? 4000 : 1200) }
  }
  async function action(input: EncounterAction) {
    if (busy.value) return
    const current = generation
    revision++
    busy.value = true; error.value = ''
    try {
      const next = await actEncounter({ ...input, roomId: input.roomId ?? room.value.roomId }, controller.signal)
      if (!disposed && current === generation) merge(next)
      return current === generation ? next : undefined
    } catch (failure) {
      if (!disposed && current === generation) error.value = String((failure as Error).message)
      return undefined
    } finally { if (current === generation) busy.value = false }
  }
  function reset() {
    generation++; controller.abort(); controller = new AbortController(); clearTimeout(timer)
    room.value = { state: 'IDLE', messages: [] }; error.value = ''; busy.value = false
    if (!disposed && store.state.token) void poll()
  }
  watch(() => String(store.state.accountId), reset)
  onMounted(() => { void poll() })
  onBeforeUnmount(() => { disposed = true; generation++; clearTimeout(timer); controller.abort() })
  return { room, error, busy, action }
}
