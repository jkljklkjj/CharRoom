/**
 * Protobuf 编解码 Worker。
 * 将 protobuf 序列化/反序列化移出主线程，避免阻塞 UI。
 *
 * 消息格式:
 *   { type: 'encode', id: 1, payload: wrapperObj } → { type: 'encode', id: 1, result: ArrayBuffer }
 *   { type: 'decode', id: 2, payload: ArrayBuffer } → { type: 'decode', id: 2, result: Object }
 */

let root = null
let Wrapper = null

async function loadProto() {
  if (root) return
  // Worker 中无法使用 import，用 importScripts 同步加载 protobuf 库
  // 实际项目中 protobufjs 的 WASM 版本在 Worker 中加载更复杂
  // 这里用简化方案：在初始化时加载 proto 定义
  try {
    const response = await fetch('/proto/message.proto')
    const text = await response.text()
    // protobufjs 在 worker 中需要动态加载
    // 使用 CDN 版或 bundled 版本
    self.importScripts('/proto/protobuf.min.js')
    root = protobuf.parse(text).root
    Wrapper = root.lookupType('com.chatlite.proto.MessageWrapper')
  } catch (e) {
    self.postMessage({ type: 'error', error: e.message })
  }
}

// 初始化
loadProto()

self.onmessage = async function(e) {
  const { type, id, payload, transfer } = e.data

  try {
    if (type === 'encode') {
      if (!Wrapper) await loadProto()
      const err = Wrapper.verify(payload)
      if (err) throw new Error(err)
      const message = Wrapper.create(payload)
      const buffer = Wrapper.encode(message).finish()
      const ab = buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.length)
      self.postMessage({ type: 'encode', id, result: ab }, [ab])
    } else if (type === 'decode') {
      if (!Wrapper) await loadProto()
      const uint8 = new Uint8Array(payload)
      const msg = Wrapper.decode(uint8)
      const obj = Wrapper.toObject(msg, { longs: String, enums: String, defaults: true })
      self.postMessage({ type: 'decode', id, result: obj })
    } else if (type === 'init') {
      await loadProto()
      self.postMessage({ type: 'init', id, result: true })
    }
  } catch (e) {
    self.postMessage({ type: 'error', id, error: e.message })
  }
}
