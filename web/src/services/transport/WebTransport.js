/**
 * WebTransport — 基于 WebTransport (QUIC) 的传输实现。
 *
 * **多流设计**：
 * - Control Stream: 登录/登出/心跳/ACK（高优先级，需可靠低延迟）
 * - Chat Streams: per-conversation 双向流（聊天消息）
 * - Incoming Streams: 服务端主动推送
 * - Datagram: typing/在线状态/临时反应（不可靠但最低延迟）
 */
import { ChatTransport } from './ChatTransport'

const STREAM_TYPE = {
  CONTROL: 'control',
  CHAT: 'chat',
}

export class WebTransport extends ChatTransport {
  constructor() {
    super()
    this._transport = null
    this._url = ''
    this._controlStream = null
    this._controlWriter = null
    this._chatStreams = new Map()
    this._incomingReader = null
    this._datagramWriter = null
    this._readerActive = false
    this._closedCalled = false
  }

  _safeOnClose() {
    if (this._closedCalled) return
    this._closedCalled = true
    if (this._onclose) this._onclose()
  }

  async connect(url, token) {
    this._url = url
    this._closedCalled = false

    if (typeof globalThis.WebTransport === 'undefined') {
      throw new Error('浏览器不支持 WebTransport API')
    }

    try {
      this._transport = new globalThis.WebTransport(url)

      this._transport.closed.then((info) => {
        console.debug('WebTransport.closed 触发:', info)
        this._readerActive = false
        this._safeOnClose()
      }).catch((e) => {
        console.debug('WebTransport.closed 异常:', e)
        this._readerActive = false
        this._safeOnClose()
      })

      const TIMEOUT_MS = 15000
      await Promise.race([
        this._transport.ready,
        new Promise((_, reject) =>
          setTimeout(() => reject(new Error('WebTransport 连接超时')), TIMEOUT_MS)
        ),
      ])
      console.debug('WebTransport 连接成功:', url)

      await this._createControlStream()
      this._connected = true

      this._startListeningIncomingStreams()
      this._initDatagram()  // Datagram 初始化

      if (this._onopen) this._onopen()
    } catch (e) {
      console.error('WebTransport 连接失败:', e)
      throw e
    }
  }

  send(data, opts = {}) {
    const streamType = opts.streamType || STREAM_TYPE.CONTROL
    const conversationId = opts.conversationId || ''

    if (streamType === STREAM_TYPE.CHAT && conversationId) {
      return this._sendToChatStream(conversationId, data)
    }
    return this._sendToControlStream(data)
  }

  close() {
    this._readerActive = false
    this._controlWriter = null
    this._controlStream = null

    for (const [convId, stream] of this._chatStreams) {
      try { stream.writer.close() } catch (_) {}
    }
    this._chatStreams.clear()

    if (this._datagramWriter) {
      try { this._datagramWriter.close() } catch (_) {}
      this._datagramWriter = null
    }

    if (this._transport) {
      this._transport.close()
      this._transport = null
    }
    this._connected = false
    this._safeOnClose()
    console.debug('WebTransport 已关闭')
  }

  isConnected() {
    return this._connected && this._transport !== null
  }

  // ── Datagram 通道 ──────────────────────────────

  async _initDatagram() {
    if (!this._transport || !this._transport.datagrams) {
      console.debug('WebTransport Datagram 不可用')
      return
    }
    try {
      this._datagramWriter = this._transport.datagrams.writable.getWriter()
      this._startDatagramReading()
      console.debug('Datagram 通道就绪')
    } catch (e) {
      console.debug('Datagram 初始化失败:', e.message)
    }
  }

  /**
   * 发送 Datagram（不可靠、无序）。
   * 适用于 typing、在线状态、临时反应等低优先级场景。
   * Datagram 比 Stream 延迟更低，但不保证到达。
   *
   * @param {Uint8Array} data - 数据
   * @param {string} [type] - 类型：'typing' | 'presence' | 'reaction'
   */
  sendDatagram(data, type = '') {
    if (!this._datagramWriter) return false

    const typeMap = { typing: 0x01, presence: 0x02, reaction: 0x03 }
    const typeByte = typeMap[type] || 0x00
    const payload = new Uint8Array(data.byteLength + 1)
    payload[0] = typeByte
    payload.set(new Uint8Array(data), 1)

    try {
      this._datagramWriter.write(payload)
      return true
    } catch (e) {
      console.debug('Datagram 发送失败:', e.message)
      return false
    }
  }

  async _startDatagramReading() {
    try {
      const reader = this._transport.datagrams.readable.getReader()
      while (this._readerActive) {
        const { value, done } = await reader.read()
        if (done) break
        if (value && value.length > 0) {
          const typeByte = value[0]
          const payload = value.slice(1)
          const typeNames = { 0x01: 'typing', 0x02: 'presence', 0x03: 'reaction' }
          const typeName = typeNames[typeByte] || ''
          if (this._ondatagram) {
            this._ondatagram(this._toArrayBuffer(payload), typeName)
          }
        }
      }
    } catch (e) {
      if (this._readerActive) {
        console.debug('Datagram 读取结束:', e.message)
      }
    }
  }

  hasDatagram() {
    return this._datagramWriter !== null && this._datagramWriter !== undefined
  }

  // ── 控制流 ─────────────────────────────────────

  async _createControlStream() {
    this._controlStream = await this._transport.createBidirectionalStream()
    this._controlWriter = this._controlStream.writable.getWriter()
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
      this._safeOnClose()
    }
  }

  // ── 会话流 ──────────────────────────────────────

  _sendToChatStream(conversationId, data) {
    const entry = this._chatStreams.get(conversationId)
    if (entry && entry.writer) {
      entry.writer.write(data).catch(e => {
        console.error('会话流发送异常:', e)
        if (this._onerror) this._onerror(e)
      })
      return true
    }
    this._createChatStream(conversationId, data).catch(e =>
      console.error('创建会话流失败:', e)
    )
    return true
  }

  async _createChatStream(conversationId, firstData) {
    const stream = await this._transport.createBidirectionalStream()
    const writer = stream.writable.getWriter()
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
      console.debug('会话流读取结束:', e.message)
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
        this._handleIncomingStream(stream)
      }
    } catch (e) {
      console.debug('入站流监听结束:', e.message)
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
      console.debug('入站流处理结束:', e.message)
    }
  }

  // ── 工具方法 ────────────────────────────────────

  _toArrayBuffer(uint8Array) {
    return uint8Array.buffer.slice(
      uint8Array.byteOffset,
      uint8Array.byteOffset + uint8Array.byteLength
    )
  }
}
