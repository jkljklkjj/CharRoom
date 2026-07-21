import protobuf from 'protobufjs'

let rootPromise = null
let protoWorker = null
let workerId = 0
const workerCallbacks = new Map()
let workerReady = false
let workerInitFailed = false

/**
 * 初始化 Web Worker（仅执行一次）。
 * Worker 是主要的编解码路径，主线程仅在 Worker 不可用时作为 fallback。
 */
function initWorker() {
  if (protoWorker || workerInitFailed) return
  try {
    protoWorker = new Worker('/proto/worker.js')
    protoWorker.onmessage = (e) => {
      const { type, id, result, error } = e.data
      if (type === 'init') { workerReady = true; return }
      if (type === 'error') {
        console.warn('[ProtoWorker] init error:', error)
        workerInitFailed = true
        workerReady = false
        return
      }
      const cb = workerCallbacks.get(id)
      if (cb) { cb(result); workerCallbacks.delete(id) }
    }
    protoWorker.onerror = (e) => {
      console.warn('[ProtoWorker] worker error:', e.message)
      workerInitFailed = true
      workerReady = false
    }
    protoWorker.postMessage({ type: 'init', id: -1 })
  } catch (e) {
    console.warn('[ProtoWorker] Worker 不可用:', e.message)
    workerInitFailed = true
  }
}

// 启动时立即初始化 Worker
if (typeof Worker !== 'undefined') {
  initWorker()
}

function workerEncode(wrapperObj) {
  return new Promise((resolve, reject) => {
    if (!protoWorker || !workerReady) { reject(new Error('worker not ready')); return }
    const id = ++workerId
    workerCallbacks.set(id, resolve)
    protoWorker.postMessage({ type: 'encode', id, payload: wrapperObj })
  })
}

function workerDecode(arrayBuffer) {
  return new Promise((resolve, reject) => {
    if (!protoWorker || !workerReady) { reject(new Error('worker not ready')); return }
    const id = ++workerId
    workerCallbacks.set(id, resolve)
    // 使用 transferable 传输 ArrayBuffer，避免拷贝
    protoWorker.postMessage({ type: 'decode', id, payload: arrayBuffer }, [arrayBuffer])
  })
}

// ── 主线程 fallback（仅在 Worker 不可用时使用） ──────

async function loadProto() {
  if (!rootPromise) {
    rootPromise = protobuf.load('/proto/message.proto')
  }
  return rootPromise
}

async function mainThreadEncode(wrapperObj) {
  const root = await loadProto()
  const Wrapper = root.lookupType('com.chatlite.proto.MessageWrapper')
  const err = Wrapper.verify(wrapperObj)
  if (err) throw Error(err)
  const message = Wrapper.create(wrapperObj)
  const buffer = Wrapper.encode(message).finish()
  return buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.length)
}

async function mainThreadDecode(arrayBuffer) {
  const root = await loadProto()
  const Wrapper = root.lookupType('com.chatlite.proto.MessageWrapper')
  const uint8 = new Uint8Array(arrayBuffer)
  const msg = Wrapper.decode(uint8)
  return Wrapper.toObject(msg, { longs: String, enums: String, defaults: true })
}

// ── 公共 API ──────────────────────────────────────────

/**
 * 编码 MessageWrapper → ArrayBuffer。
 * 优先使用 Web Worker，fallback 到主线程。
 */
export async function encodeMessage(wrapperObj) {
  if (workerReady) {
    try { return await workerEncode(wrapperObj) } catch (_) { /* fallback */ }
  }
  return mainThreadEncode(wrapperObj)
}

/**
 * 解码 ArrayBuffer → MessageWrapper 对象。
 * 优先使用 Web Worker（transferable 零拷贝），fallback 到主线程。
 */
export async function decodeMessage(arrayBuffer) {
  if (workerReady) {
    try { return await workerDecode(arrayBuffer) } catch (_) { /* fallback */ }
  }
  return mainThreadDecode(arrayBuffer)
}

/**
 * 获取 Worker 状态（调试用）。
 */
export function getWorkerStatus() {
  return { ready: workerReady, failed: workerInitFailed }
}

export default { loadProto, encodeMessage, decodeMessage, getWorkerStatus }
