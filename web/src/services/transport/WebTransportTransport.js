/**
 * WebTransportTransport — 基于 WebTransport (QUIC) 的传输实现。
 *
 * 使用浏览器 WebTransport API 通过 QUIC 连接，支持：
 * - 双向流（BidirectionalStream）用于控制流 + 聊天消息
 * - 自动流管理（连接时创建一条双向流）
 * - protobuf 二进制数据直接传输
 *
 * WebTransport URL 格式: https://host:port/.well-known/webtransport
 * 浏览器使用 QUIC + HTTP/3 (ALPN "h3") 自动协商。
 *
 * @see https://developer.mozilla.org/en-US/docs/Web/API/WebTransport
 */
import { ChatTransport } from './ChatTransport'

export class WebTransportTransport extends ChatTransport {
  constructor() {
    super()
    this._transport = null
    this._controlStream = null      // 主双向流
    this._streamWriter = null       // 主流写入端
    this._streamReader = null       // 主流读取端
    this._readerActive = false      // 读取循环是否运行
    this._url = ''
  }

  /**
   * 建立 WebTransport 连接。
   * 连接成功后创建一条双向流用于消息收发。
   *
   * @param {string} url - WebTransport URL (https://host:port/.well-known/webtransport)
   * @param {string} token - 认证 token（由上层在连接后发送 login 消息）
   */
  async connect(url, token) {
    this._url = url

    if (typeof WebTransport === 'undefined') {
      throw new Error('浏览器不支持 WebTransport API')
    }

    try {
      // 创建 WebTransport 连接
      this._transport = new WebTransport(url)

      // 等待连接就绪（QUIC 握手 + HTTP/3 协商完成），15s 超时
      const TIMEOUT_MS = 15000
      const timeoutPromise = new Promise((_, reject) =>
        setTimeout(() => reject(new Error('WebTransport 连接超时')), TIMEOUT_MS)
      )
      await Promise.race([this._transport.ready, timeoutPromise])
      console.log('✅ WebTransportTransport 连接成功:', url)

      // 创建主双向流（用于控制消息 + 聊天消息）
      this._controlStream = await this._transport.createBidirectionalStream()
      this._streamWriter = this._controlStream.writable.getWriter()
      this._streamReader = this._controlStream.readable.getReader()

      this._connected = true

      // 启动读取循环
      this._readerActive = true
      this._startReading()

      // 启动入站流监听（服务端推送）
      this._startListeningIncomingStreams()

      if (this._onopen) this._onopen()
    } catch (e) {
      console.error('❌ WebTransportTransport 连接失败:', e)
      throw e
    }
  }

  /**
   * 发送二进制消息（protobuf 编码后）。
   * @param {ArrayBuffer|Uint8Array} data - 消息数据
   */
  send(data) {
    if (!this._streamWriter) {
      console.warn('WebTransportTransport 发送失败: 无可用流')
      return false
    }

    try {
      this._streamWriter.write(data)
      return true
    } catch (e) {
      console.error('WebTransportTransport 发送异常:', e)
      if (this._onerror) this._onerror(e)
      return false
    }
  }

  /**
   * 关闭 WebTransport 连接。
   */
  close() {
    this._readerActive = false
    this._streamWriter = null
    this._streamReader = null
    this._controlStream = null

    if (this._transport) {
      this._transport.close()
      this._transport = null
    }
    this._connected = false
    console.log('WebTransportTransport 已关闭')
  }

  isConnected() {
    return this._connected && this._transport !== null
  }

  // ── 内部方法 ──────────────────────────────────

  /**
   * 持续读取主双向流的消息。
   */
  async _startReading() {
    try {
      while (this._readerActive && this._streamReader) {
        const { value, done } = await this._streamReader.read()
        if (done) break
        if (value && this._onmessage) {
          // value 是 Uint8Array，转换为 ArrayBuffer 以兼容现有处理逻辑
          const buffer = value.buffer.slice(
            value.byteOffset,
            value.byteOffset + value.byteLength
          )
          this._onmessage(buffer)
        }
      }
    } catch (e) {
      if (this._readerActive) {
        console.error('WebTransportTransport 读取异常:', e)
        if (this._onerror) this._onerror(e)
      }
    } finally {
      this._readerActive = false
      // 通知上层连接已关闭（触发重连）
      if (this._onclose) this._onclose()
    }
  }

  /**
   * 监听服务器主动发起的入站流。
   * 服务端可以在新流上推送消息（如系统通知）。
   */
  async _startListeningIncomingStreams() {
    try {
      const incomingReader = this._transport.incomingBidirectionalStreams.getReader()
      while (true) {
        const { value: stream, done } = await incomingReader.read()
        if (done) break
        // 服务端推送的流 — 读取数据并触发 onmessage
        this._handleIncomingStream(stream)
      }
    } catch (e) {
      console.debug('WebTransportTransport 入站流监听结束:', e.message)
    }
  }

  /**
   * 处理服务端推送的入站双向流。
   * @param {WebTransportBidirectionalStream} stream
   */
  async _handleIncomingStream(stream) {
    try {
      const reader = stream.readable.getReader()
      while (true) {
        const { value, done } = await reader.read()
        if (done) break
        if (value && this._onmessage) {
          const buffer = value.buffer.slice(
            value.byteOffset,
            value.byteOffset + value.byteLength
          )
          this._onmessage(buffer)
        }
      }
    } catch (e) {
      console.debug('WebTransportTransport 入站流处理结束:', e.message)
    }
  }
}
