export const MAX_FRAME_SIZE = 65536
export function frame(data: ArrayBuffer | Uint8Array): ArrayBuffer {
  const bytes = data instanceof Uint8Array ? data : new Uint8Array(data)
  if (!bytes.length || bytes.length > MAX_FRAME_SIZE) throw new Error('Invalid frame length')
  const result = new Uint8Array(4 + bytes.length)
  new DataView(result.buffer).setUint32(0, bytes.length)
  result.set(bytes, 4)
  return result.buffer
}
/** Fixed-size header/payload accumulation, independently owned by every readable stream. */
export class FrameDecoder {
  private header = new Uint8Array(4)
  private headerSize = 0
  private payload: Uint8Array<ArrayBuffer> | null = null
  private size = 0
  push(chunk: Uint8Array): ArrayBuffer[] {
    const result: ArrayBuffer[] = []
    let offset = 0
    while (offset < chunk.length) {
      if (!this.payload) {
        const length = Math.min(4 - this.headerSize, chunk.length - offset)
        this.header.set(chunk.subarray(offset, offset + length), this.headerSize)
        this.headerSize += length; offset += length
        if (this.headerSize !== 4) continue
        const size = new DataView(this.header.buffer).getUint32(0)
        if (!size || size > MAX_FRAME_SIZE) throw new Error('Invalid frame length')
        this.payload = new Uint8Array(size); this.size = 0
      }
      const length = Math.min(this.payload.length - this.size, chunk.length - offset)
      this.payload.set(chunk.subarray(offset, offset + length), this.size)
      offset += length; this.size += length
      if (this.size === this.payload.length) {
        result.push(this.payload.buffer); this.payload = null; this.headerSize = 0
      }
    }
    return result
  }
  finish() { if (this.headerSize || this.payload) throw new Error('Stream ended with truncated frame') }
}
