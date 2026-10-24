import type { BinaryData, SendOptions } from '../../types/transport'
/** Event contract shared by transport implementations and the chat service. */
export abstract class ChatTransport {
  protected _onopen: (() => void) | null = null
  protected _onmessage: ((data: ArrayBuffer) => void) | null = null
  protected _onclose: ((event?: unknown) => void) | null = null
  protected _onerror: ((error: unknown) => void) | null = null
  protected _connected = false
  set onopen(fn: (() => void) | null) { this._onopen = fn }
  set onmessage(fn: ((data: ArrayBuffer) => void) | null) { this._onmessage = fn }
  set onclose(fn: ((event?: unknown) => void) | null) { this._onclose = fn }
  set onerror(fn: ((error: unknown) => void) | null) { this._onerror = fn }
  get connected() { return this._connected }
  abstract connect(url: string, token: string): Promise<void>
  /** 发送二进制消息。返回 Promise<boolean>，resolve 表示 write 真实完成状态。 */
  abstract send(data: BinaryData, opts?: SendOptions): boolean | Promise<boolean>
  abstract close(): void
  isConnected() { return this._connected }
}
