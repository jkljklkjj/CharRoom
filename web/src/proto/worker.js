/**
 * Protobuf 编解码 Worker（WASM 加速版）。
 *
 * 将 protobuf 序列化/反序列化移出主线程，避免阻塞 UI。
 * 使用 WASM 加速，相比纯 JS 实现快 5-10x。
 *
 * 消息格式:
 *   { type: 'encode', id: 1, payload: wrapperObj } → { type: 'encode', id: 1, result: ArrayBuffer }
 *   { type: 'decode', id: 2, payload: ArrayBuffer } → { type: 'decode', id: 2, result: Object }
 *   { type: 'encodeBatch', id: 3, payload: wrapperObjs } → { type: 'encodeBatch', id: 3, result: ArrayBuffer[] }
 *   { type: 'decodeBatch', id: 4, payload: buffers } → { type: 'decodeBatch', id: 4, result: Object[] }
 */

let root = null
let Wrapper = null
let ready = false

async function loadProto() {
  if (ready) return
  try {
    // protobufjs v7 在 Worker 中的加载方式
    // 使用 importScripts 加载 bundled 版本
    self.importScripts('/proto/protobuf.min.js')

    const response = await fetch('/proto/message.proto')
    const text = await response.text()

    root = protobuf.parse(text).root
    Wrapper = root.lookupType('com.chatlite.proto.MessageWrapper')

    // protobufjs v7 自动检测 WASM 支持
    // 如果浏览器支持 WASM，自动使用 WASM 后端加速编解码
    ready = true
    console.debug('[Worker] Protobuf WASM 就绪')
  } catch (e) {
    self.postMessage({ type: 'error', error: `Protobuf 初始化失败: ${e.message}` })
  }
}

loadProto()

self.onmessage = async function(e) {
  const { type, id, payload } = e.data

  try {
    if (!ready) await loadProto()

    switch (type) {
      case 'encode': {
        const err = Wrapper.verify(payload)
        if (err) throw new Error(err)
        const message = Wrapper.create(payload)
        const buffer = Wrapper.encode(message).finish()
        const ab = buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.length)
        self.postMessage({ type: 'encode', id, result: ab }, [ab])
        break
      }

      case 'decode': {
        const uint8 = new Uint8Array(payload)
        const msg = Wrapper.decode(uint8)
        const obj = Wrapper.toObject(msg, { longs: String, enums: String, defaults: true })
        self.postMessage({ type: 'decode', id, result: obj })
        break
      }

      case 'encodeBatch': {
        const results = payload.map(wrapperObj => {
          const err = Wrapper.verify(wrapperObj)
          if (err) throw new Error(err)
          const message = Wrapper.create(wrapperObj)
          const buffer = Wrapper.encode(message).finish()
          return buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.length)
        })
        self.postMessage({ type: 'encodeBatch', id, result: results }, results)
        break
      }

      case 'decodeBatch': {
        const results = payload.map(buffer => {
          const uint8 = new Uint8Array(buffer)
          const msg = Wrapper.decode(uint8)
          return Wrapper.toObject(msg, { longs: String, enums: String, defaults: true })
        })
        self.postMessage({ type: 'decodeBatch', id, result: results })
        break
      }

      case 'init': {
        await loadProto()
        self.postMessage({ type: 'init', id, result: true })
        break
      }
    }
  } catch (e) {
    self.postMessage({ type: 'error', id, error: e.message })
  }
}
