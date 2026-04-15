import { encodeMessage, decodeMessage } from '../proto'

let socket = null
let pendingQueue = []
let handlers = { onopen: () => {}, onmessage: () => {}, onclose: () => {}, onerror: () => {} }

// Note: browsers cannot set custom headers on WebSocket handshake. We pass token as query `?token=`.
export function connect(wsUrl, token, { onopen, onmessage, onclose, onerror } = {}) {
  if (socket && socket.readyState === WebSocket.OPEN) return socket
  handlers = { onopen: onopen || handlers.onopen, onmessage: onmessage || handlers.onmessage, onclose: onclose || handlers.onclose, onerror: onerror || handlers.onerror }
  const url = token ? `${wsUrl}?token=${encodeURIComponent(token)}` : wsUrl
  socket = new WebSocket(url)
  socket.binaryType = 'arraybuffer'

  socket.addEventListener('open', (e) => {
    handlers.onopen(e)
    // flush queue (buffers)
    pendingQueue.forEach(p => socket.send(p))
    pendingQueue = []
  })

  socket.addEventListener('message', async (e) => {
    const data = e.data
    if (data instanceof ArrayBuffer || data instanceof Blob) {
      let buf
      if (data instanceof Blob) buf = await data.arrayBuffer()
      else buf = data
      try {
        const msg = await decodeMessage(buf)
        handlers.onmessage(msg)
      } catch (err) {
        // could not parse protobuf, pass raw
        handlers.onmessage(buf)
      }
    } else {
      let parsed = data
      try { parsed = JSON.parse(data) } catch (_) {}
      handlers.onmessage(parsed)
    }
  })

  socket.addEventListener('close', (e) => handlers.onclose(e))
  socket.addEventListener('error', (e) => handlers.onerror(e))
  return socket
}

export async function sendWrapper(wrapperObj) {
  try {
    const buffer = await encodeMessage(wrapperObj)
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(buffer)
      return true
    }
    pendingQueue.push(buffer)
    return false
  } catch (e) {
    console.error('encode error', e)
    return false
  }
}

export function close() {
  if (socket) {
    socket.close()
    socket = null
  }
}

export function readyState() {
  return socket ? socket.readyState : WebSocket.CLOSED
}

export default { connect, sendWrapper, close, readyState }
