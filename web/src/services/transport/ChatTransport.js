/**
 * ChatTransport — 聊天传输层抽象基类。
 *
 * 定义统一的传输接口，由具体实现类提供不同的底层协议支持：
 * - WebSocketTransport: 现有 WSS 实现（降级方案）
 * - WebTransportTransport: 基于 WebTransport/QUIC 的实现（首选方案）
 *
 * 使用方式：
 *   const transport = TransportFactory.create();
 *   transport.onmessage = (data) => { ... };
 *   await transport.connect(url, token);
 *   transport.send(encodedMessage);
 */
export class ChatTransport {
  constructor() {
    this._onopen = null
    this._onmessage = null
    this._onclose = null
    this._onerror = null
    this._connected = false
  }

  // ── 事件回调 ──────────────────────────────────

  set onopen(fn) { this._onopen = fn }
  set onmessage(fn) { this._onmessage = fn }
  set onclose(fn) { this._onclose = fn }
  set onerror(fn) { this._onerror = fn }

  get connected() { return this._connected }

  // ── 需子类实现的接口 ──────────────────────────

  /**
   * 建立连接。
   * @param {string} url - 连接 URL
   * @param {string} token - 认证 token
   * @returns {Promise<void>}
   */
  async connect(url, token) {
    throw new Error('connect() must be implemented by subclass')
  }

  /**
   * 发送二进制消息。
   * @param {ArrayBuffer|Uint8Array} data - protobuf 编码后的消息
   */
  send(data) {
    throw new Error('send() must be implemented by subclass')
  }

  /**
   * 关闭连接。
   */
  close() {
    throw new Error('close() must be implemented by subclass')
  }

  /**
   * 连接是否就绪。
   * @returns {boolean}
   */
  isConnected() {
    return this._connected
  }
}
