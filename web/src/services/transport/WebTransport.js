/**
 * WebTransport — 基于 WebTransport (QUIC) 的传输实现。
 *
 * **多流设计**：
 * - Control Stream: 登录/登出/心跳/ACK（高优先级，需可靠低延迟）
 * - Chat Streams: per-conversation 双向流（聊天消息，与服务端 QuicChatStreamHandler 对应）
 * - Incoming Streams: 服务端主动推送（如新消息通知）
 *
 * WebTransport URL 格式: https://host:port/.well-known/webtransport
 */
import { ChatTransport } from './ChatTransport'

// 流类型常量
const STREAM_TYPE = {
  CONTROL: 'control',   // 控制流
  CHAT: 'chat',         // 私有/群聊会话流
}

export class WebTransport extends ChatTransport {
  constructor() {
    super()
    this._transport = null
    this._url = ''

    // 控制流（登录/心跳/ACK）
    this._controlStream = null
    this._controlWriter = null

    // 会话流池：conversationId → { writer, reader }
    this._chatStreams = new Map()

    // 入站流读取器
    this._incomingReader = null

    this._readerActive = false
  }

  /**
   * 建立 WebTransport 连接。
   * 连接成功后创建控制流 + 启动入站流监听。
   */
  async connect(url, token) {
    this._url = url

    if (typeof globalThis.WebTransport === 'undefined') {
      throw new Error('浏览器不支持 WebTransport API')
    }

    try {
      this._transport = new globalThis.WebTransport(url)

      // QUIC 握手 + HTTP/3，15s 超时
      const TIMEOUT_MS = 15000
      await Promise.race([
        this._transport.ready,
        new Promise((_, reject) =>
          setTimeout(() => reject(new Error('WebTransport 连接超时')), TIMEOUT_MS)
        ),
      ])
      console.log('✅ WebTransport 连接成功:', url)

      // 创建控制流
      await this._createControlStream()

      this._connected = true

      // 启动入站流监听（服务端推送）
      this._startListeningIncomingStreams()

      if (this._onopen) this._onopen()
    } catch (e) {
      console.error('❌ WebTransport 连接失败:', e)
      throw e
    }
  }

  /**
   * 发送消息，自动按类型路由到对应流：
   * - ack / heartbeat → 控制流
   * - chat / groupChat / agent_chat → 会话流（按 conversationId）
   *
   * @param {ArrayBuffer|Uint8Array} data - protobuf 编码后的消息
   * @param {object} [opts] - 可选参数
   * @param {string} [opts.conversationId] - 会话 ID，用于会话流路由
   * @param {string} [opts.streamType] - 流类型：'control' | 'chat'
   */
  send(data, opts = {}) {
    const streamType = opts.streamType || STREAM_TYPE.CONTROL
    const conversationId = opts.conversationId || ''

    if (streamType === STREAM_TYPE.CHAT && conversationId) {
      return this._sendToChatStream(conversationId, data)
    }
    return this._sendToControlStream(data)
  }

  /**
   * 关闭连接：关闭所有流和传输层。
   */
  close() {
    this._readerActive = false
    this._controlWriter = null
    this._controlStream = null

    // 关闭所有会话流
    for (const [convId, stream] of this._chatStreams) {
      try { stream.writer.close() } catch (_) {}
    }
    this._chatStreams.clear()

    if (this._transport) {
      this._transport.close()
      this._transport = null
    }
    this._connected = false
    console.log('WebTransport 已关闭')
  }

  isConnected() {
    return this._connected && this._transport !== null
  }

  // ── 控制流 ─────────────────────────────────────

  async _createControlStream() {
    this._controlStream = await this._transport.createBidirectionalStream()
    this._controlWriter = this._controlStream.writable.getWriter()

    // 启动控制流读取循环
    this._readerActive = true
    this._startControlStreamReading()
  }

  _sendToControlStream(data) {
    if (!this._controlWriter) return false
    this._controlWriter.write(data).catch(e => {
      console.error('控制流发送异常:', e)
      if (this._onerror) this._onerror(e)
    })
    return true
  }

  async _startControlStreamReading() {
    try {
      const reader = this._controlStream.readable.getReader()
      while (this._readerActive) {
        const { value, done } = await reader.read()
        if (done) break
        if (value && this._onmessage) {
          this._onmessage(this._toArrayBuffer(value))
        }
      }
    } catch (e) {
      if (this._readerActive) {
        console.error('控制流读取异常:', e)
        if (this._onerror) this._onerror(e)
      }
    } finally {
      this._readerActive = false
      if (this._onclose) this._onclose()
    }
  }

  // ── 会话流 ──────────────────────────────────────

  /**
   * 向指定会话发送消息。如果会话流不存在则创建新流。
   *
   * WebTransport 协议：服务端 `QuicWebTransportHandler` 直接处理
   * protobuf MessageWrapper，无需额外 init 帧。
   */
  _sendToChatStream(conversationId, data) {
    const entry = this._chatStreams.get(conversationId)
    if (entry && entry.writer) {
      entry.writer.write(data).catch(e => {
        console.error(`会话流(${conversationId})发送异常:`, e)
        if (this._onerror) this._onerror(e)
      })
      return true
    }

    // 创建新会话流并立即发送
    this._createChatStream(conversationId, data).catch(e =>
      console.error(`创建会话流(${conversationId})失败:`, e)
    )
    return true
  }

  async _createChatStream(conversationId, firstData) {
    const stream = await this._transport.createBidirectionalStream()
    const writer = stream.writable.getWriter()

    // 直接发 protobuf 消息（WebTransport 无需 init 帧，后端通吃）
    if (firstData) await writer.write(firstData)

    const entry = { stream, writer }
    this._chatStreams.set(conversationId, entry)
    this._readChatStream(conversationId, stream)
  }

  async _readChatStream(conversationId, stream) {
    try {
      const reader = stream.readable.getReader()
      while (true) {
        const { value, done } = await reader.read()
        if (done) break
        if (value && this._onmessage) {
          this._onmessage(this._toArrayBuffer(value))
        }
      }
    } catch (e) {
      console.debug(`会话流(${conversationId})读取结束:`, e.message)
    } finally {
      this._chatStreams.delete(conversationId)
    }
  }

  // ── 入站流（服务端推送） ─────────────────────────

  async _startListeningIncomingStreams() {
    try {
      this._incomingReader = this._transport.incomingBidirectionalStreams.getReader()
      while (true) {
        const { value: stream, done } = await this._incomingReader.read()
        if (done) break
        // 服务端推流 — 可能是新聊天消息或批量通知
        this._handleIncomingStream(stream)
      }
    } catch (e) {
      console.debug('WebTransport 入站流监听结束:', e.message)
    }
  }

  async _handleIncomingStream(stream) {
    try {
      const reader = stream.readable.getReader()
      while (true) {
        const { value, done } = await reader.read()
        if (done) break
        if (value && this._onmessage) {
          this._onmessage(this._toArrayBuffer(value))
        }
      }
    } catch (e) {
      console.debug('WebTransport 入站流处理结束:', e.message)
    }
  }

  // ── 工具方法 ────────────────────────────────────

  /** 将 Uint8Array 转为 ArrayBuffer */
  _toArrayBuffer(uint8Array) {
    return uint8Array.buffer.slice(
      uint8Array.byteOffset,
      uint8Array.byteOffset + uint8Array.byteLength
    )
  }
}
