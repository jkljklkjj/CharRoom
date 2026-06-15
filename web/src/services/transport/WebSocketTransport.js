/**
 * WebSocketTransport — 基于 WebSocket 的传输实现。
 *
 * 封装标准 WebSocket API，作为 QUIC/WebTransport 不可用时的降级方案。
 * 使用二进制 ArrayBuffer 传输，与现有 protobuf 消息格式兼容。
 */
import { ChatTransport } from './ChatTransport'

export class WebSocketTransport extends ChatTransport {
  constructor() {
    super()
    this._ws = null
    this._url = ''
  }

  async connect(url, token) {
    this._url = url

    return new Promise((resolve, reject) => {
      try {
        const ws = new WebSocket(url)
        ws.binaryType = 'arraybuffer'
        this._ws = ws

        ws.addEventListener('open', () => {
          console.log('✅ WebSocketTransport 连接成功')
          this._connected = true
          if (this._onopen) this._onopen()
          resolve()
        })

        ws.addEventListener('message', (event) => {
          if (this._onmessage) {
            this._onmessage(event.data)
          }
        })

        ws.addEventListener('close', (event) => {
          console.log('❌ WebSocketTransport 关闭:', event.code, event.reason)
          this._connected = false
          if (this._onclose) this._onclose(event)
        })

        ws.addEventListener('error', (event) => {
          console.error('💥 WebSocketTransport 错误:', event)
          if (this._onerror) this._onerror(event)
          if (!this._connected) reject(event)
        })
      } catch (e) {
        reject(e)
      }
    })
  }

  send(data) {
    if (this._ws && this._ws.readyState === WebSocket.OPEN) {
      const raw = data instanceof ArrayBuffer ? data : data.buffer
      this._ws.send(raw)
      return true
    }
    console.warn('WebSocketTransport 发送失败: 连接未就绪')
    return false
  }

  close() {
    if (this._ws) {
      this._ws.close()
      this._ws = null
    }
    this._connected = false
  }

  isConnected() {
    return this._ws !== null && this._ws.readyState === WebSocket.OPEN
  }
}
