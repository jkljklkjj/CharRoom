import type { MessageWrapper } from './messages'
import type { WorkerRequest, WorkerReply } from './workerMessages'
let mainCodec: Promise<typeof import('./codec')> | null = null
function fallbackCodec() {
  mainCodec ??= import('./codec').catch(error => { mainCodec = null; throw error })
  return mainCodec
}
export async function loadProto() { return (await fallbackCodec()).loadProto() }

const WORKER_TIMEOUT_MS = 5000
let protoWorker: Worker | null = null
let workerReady = false
let workerInitFailed = false
let nextId = 0
type WorkerResult = ArrayBuffer | MessageWrapper
const pending = new Map<number, {
  resolve: (result: WorkerResult) => void
  reject: (error: Error) => void
  timer: ReturnType<typeof setTimeout>
}>()
let initTimer: ReturnType<typeof setTimeout> | undefined

function failWorker(error: Error) {
  workerInitFailed = true
  workerReady = false
  clearTimeout(initTimer)
  protoWorker?.terminate()
  protoWorker = null
  for (const request of pending.values()) {
    clearTimeout(request.timer)
    request.reject(error)
  }
  pending.clear()
}
function initWorker() {
  try {
    protoWorker = new Worker(new URL('./worker.ts', import.meta.url), { type: 'module' })
    protoWorker.onmessage = (event: MessageEvent<WorkerReply>) => {
      const reply = event.data
      if (reply.type === 'init') { clearTimeout(initTimer); workerReady = true; return }
      if (reply.type === 'error') { failWorker(new Error(reply.error)); return }
      const request = pending.get(reply.id)
      if (!request) return
      clearTimeout(request.timer)
      pending.delete(reply.id)
      request.resolve(reply.result)
    }
    protoWorker.onerror = event => failWorker(new Error(event.message || 'Protobuf worker failed'))
    protoWorker.onmessageerror = () => failWorker(new Error('Invalid protobuf worker reply'))
    initTimer = setTimeout(() => failWorker(new Error('Protobuf worker initialization timed out')), WORKER_TIMEOUT_MS)
    protoWorker.postMessage({ type: 'init', id: 0 } satisfies WorkerRequest)
  } catch (error) { failWorker(error instanceof Error ? error : new Error(String(error))) }
}
if (typeof Worker !== 'undefined') initWorker()
function requestWorker(request: WorkerRequest, transfer: Transferable[] = []): Promise<WorkerResult> {
  return new Promise((resolve, reject) => {
    if (!protoWorker || !workerReady) { reject(new Error('Worker not ready')); return }
    const timer = setTimeout(() => failWorker(new Error('Protobuf worker request timed out')), WORKER_TIMEOUT_MS)
    pending.set(request.id, { resolve, reject, timer })
    try { protoWorker.postMessage(request, transfer) }
    catch (error) { failWorker(error instanceof Error ? error : new Error(String(error))) }
  })
}
export async function encodeMessage(wrapper: MessageWrapper): Promise<ArrayBuffer> {
  if (workerReady) {
    try {
      const result = await requestWorker({ type: 'encode', id: ++nextId, payload: wrapper })
      if (result instanceof ArrayBuffer) return result
      throw new Error('Invalid encoded result')
    } catch { /* use the shared codec on the main thread */ }
  }
  return (await fallbackCodec()).encode(wrapper)
}
export async function decodeMessage(buffer: ArrayBuffer): Promise<MessageWrapper> {
  if (workerReady) {
    // Retain the original input for fallback after rejection, crash, or timeout.
    const transferred = buffer.slice(0)
    try {
      const result = await requestWorker({ type: 'decode', id: ++nextId, payload: transferred }, [transferred])
      if (!(result instanceof ArrayBuffer)) return result
      throw new Error('Invalid decoded result')
    } catch { /* use the retained input on the main thread */ }
  }
  return (await fallbackCodec()).decode(buffer)
}
export function getWorkerStatus() { return { ready: workerReady, failed: workerInitFailed } }
export default { loadProto, encodeMessage, decodeMessage, getWorkerStatus }
