import { loadProto, encode, decode } from './codec'
import type { WorkerRequest, WorkerReply } from './workerMessages'

// Keep DOM and WebWorker global library definitions separate.
const scope = self as unknown as {
  onmessage: ((event: MessageEvent<WorkerRequest>) => Promise<void>) | null
  postMessage: (reply: WorkerReply, transfer?: Transferable[]) => void
}
scope.onmessage = async ({ data: request }) => {
  try {
    if (request.type === 'init') {
      await loadProto()
      scope.postMessage({ type: 'init', id: request.id })
    } else if (request.type === 'encode') {
      const result = await encode(request.payload)
      scope.postMessage({ type: 'encode', id: request.id, result }, [result])
    } else {
      const result = await decode(request.payload)
      scope.postMessage({ type: 'decode', id: request.id, result })
    }
  } catch (error) {
    scope.postMessage({ type: 'error', id: request.id, error: error instanceof Error ? error.message : String(error) })
  }
}
