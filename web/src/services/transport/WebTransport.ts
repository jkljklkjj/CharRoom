import { ChatTransport } from './ChatTransport'
import type { BinaryData, SendOptions } from '../../types/transport'
import { FrameDecoder, frame } from './framing'
type Writer = WritableStreamDefaultWriter<Uint8Array<ArrayBuffer>>
/** Each readable stream owns its decoder; each conversation has one stream creation in flight. */
export class WebTransport extends ChatTransport {
  private transport: globalThis.WebTransport | null = null
  private control: Writer | null = null
  private streams = new Map<string, Promise<Writer>>()
  private readers = new Set<ReadableStreamDefaultReader<Uint8Array<ArrayBuffer>>>()
  private writers = new Set<Writer>()
  private generation = 0
  private framed = false
  private closed = true
  private incoming: ReadableStreamDefaultReader<WebTransportBidirectionalStream> | null = null
  async connect(url: string, _token: string) {
    this.close()
    const generation = ++this.generation
    this.closed = false
    this.framed = new URL(url).searchParams.get('framing') === 'length-v1'
    const transport = new globalThis.WebTransport(url)
    this.transport = transport
    const current = () => generation === this.generation && this.transport === transport
    let timer: ReturnType<typeof setTimeout> | undefined
    void transport.closed.then(() => { if (current()) this.close() }, error => { if (current()) { this._onerror?.(error); this.close() } })
    try {
      await Promise.race([transport.ready, new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('WebTransport timeout')), 15000) })])
      if (!current()) return
      this.control = await this.createStream(transport, generation)
      if (!current()) return
      this._connected = true
      void this.listenIncoming(transport, generation)
      this._onopen?.()
    } catch (error) { if (current()) this.close(); throw error }
    finally { clearTimeout(timer) }
  }
  private async createStream(transport: globalThis.WebTransport, generation: number) {
    const stream = await transport.createBidirectionalStream()
    if (generation !== this.generation) { void stream.readable.cancel().catch(() => {}); void stream.writable.abort().catch(() => {}); throw new Error('Obsolete connection') }
    const writer = stream.writable.getWriter()
    this.writers.add(writer)
    void this.read(stream.readable, generation)
    return writer
  }
  async send(data: BinaryData, options: SendOptions = {}): Promise<boolean> {
    const transport = this.transport, generation = this.generation
    if (!transport || !this._connected) return false
    const bytes = data instanceof Uint8Array ? Uint8Array.from(data) : new Uint8Array(data)
    const payload = this.framed ? new Uint8Array(frame(bytes)) : bytes
    const key = options.streamType === 'chat' ? options.conversationId : ''
    try {
      let writer = this.control
      if (key) {
        let pending = this.streams.get(key)
        if (!pending) {
          pending = this.createStream(transport, generation)
          this.streams.set(key, pending)
          void pending.catch(() => { if (this.streams.get(key) === pending) this.streams.delete(key) })
        }
        writer = await pending
      }
      if (!writer || generation !== this.generation) return false
      await writer.write(payload)
      return generation === this.generation
    } catch (error) {
      if (key) this.streams.delete(key)
      if (generation === this.generation) this._onerror?.(error)
      return false
    }
  }
  private async read(stream: ReadableStream<Uint8Array<ArrayBuffer>>, generation: number) {
    const reader = stream.getReader(), decoder = new FrameDecoder()
    this.readers.add(reader)
    try {
      while (generation === this.generation) {
        const { value, done } = await reader.read()
        if (generation !== this.generation) return
        if (done) { if (this.framed) decoder.finish(); return }
        for (const bytes of this.framed ? decoder.push(value) : [Uint8Array.from(value).buffer]) this._onmessage?.(bytes)
      }
    } catch (error) { if (generation === this.generation) this._onerror?.(error) }
    finally { this.readers.delete(reader); reader.releaseLock() }
  }
  private async listenIncoming(transport: globalThis.WebTransport, generation: number) {
    const reader = transport.incomingBidirectionalStreams.getReader()
    this.incoming = reader
    try {
      while (generation === this.generation) {
        const { value, done } = await reader.read()
        if (done || generation !== this.generation) return
        void this.read(value.readable, generation)
      }
    } catch (error) { if (generation === this.generation) this._onerror?.(error) }
    finally { reader.releaseLock() }
  }
  close() {
    this.generation++
    this._connected = false
    for (const reader of this.readers) void reader.cancel().catch(() => {})
    for (const writer of this.writers) void writer.abort().catch(() => {})
    this.readers.clear(); this.writers.clear(); this.streams.clear(); this.control = null
    void this.incoming?.cancel().catch(() => {}); this.incoming = null
    this.transport?.close(); this.transport = null
    if (!this.closed) { this.closed = true; this._onclose?.() }
  }
}
