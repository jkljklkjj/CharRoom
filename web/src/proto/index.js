import protobuf from 'protobufjs'

let rootPromise = null
let protoWorker = null
let workerId = 0
const workerCallbacks = new Map()
let workerReady = false

/**
 * 尝试初始化 Web Worker。
 * 不支持 Worker 的环境回退到主线程解码。
 */
function getWorker() {
  if (protoWorker) return protoWorker
  try {
    protoWorker = new Worker('/proto/worker.js')
    protoWorker.onmessage = (e) => {
      const { type, id, result, error } = e.data
      if (type === 'init') { workerReady = true; return }
      if (type === 'error') { console.warn('[ProtoWorker]', error); return }
      const cb = workerCallbacks.get(id)
      if (cb) { cb(result); workerCallbacks.delete(id) }
    }
    protoWorker.postMessage({ type: 'init', id: -1 })
    return protoWorker
  } catch (e) {
    console.warn('[ProtoWorker] Worker 不可用，回退主线程', e.message)
    protoWorker = null
    return null
  }
}

function workerEncode(wrapperObj) {
  return new Promise((resolve) => {
    const id = ++workerId
    workerCallbacks.set(id, resolve)
    getWorker().postMessage({ type: 'encode', id, payload: wrapperObj })
  })
}

function workerDecode(arrayBuffer) {
  return new Promise((resolve) => {
    const id = ++workerId
    workerCallbacks.set(id, resolve)
    // 使用 transferable 传输 ArrayBuffer，避免拷贝
    getWorker().postMessage({ type: 'decode', id, payload: arrayBuffer }, [arrayBuffer])
  })
}

// ── 公共 API ──────────────────────────────────────────

export function loadProto() {
  if (!rootPromise) {
    rootPromise = protobuf.load('/proto/message.proto')
  }
  return rootPromise
}

export async function encodeMessage(wrapperObj) {
  // 尝试 Worker，失败回退主线程
  if (getWorker() && workerReady) {
    try {
      return await workerEncode(wrapperObj)
    } catch (_) { /* fallthrough */ }
  }
  // 主线程解码
  const root = await loadProto()
  const Wrapper = root.lookupType('com.chatlite.proto.MessageWrapper')
  const err = Wrapper.verify(wrapperObj)
  if (err) throw Error(err)
  const message = Wrapper.create(wrapperObj)
  const buffer = Wrapper.encode(message).finish()
  return buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.length)
}

export async function decodeMessage(arrayBuffer) {
  // 尝试 Worker，失败回退主线程
  if (getWorker() && workerReady) {
    try {
      return await workerDecode(arrayBuffer)
    } catch (_) { /* fallthrough */ }
  }
  // 主线程解码
  const root = await loadProto()
  const Wrapper = root.lookupType('com.chatlite.proto.MessageWrapper')
  const uint8 = new Uint8Array(arrayBuffer)
  const msg = Wrapper.decode(uint8)
  const obj = Wrapper.toObject(msg, { longs: String, enums: String, defaults: true })
  return obj
}

export default { loadProto, encodeMessage, decodeMessage }
