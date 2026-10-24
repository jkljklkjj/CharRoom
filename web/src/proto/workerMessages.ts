import type { MessageWrapper } from './messages'
export type WorkerRequest =
  | { type: 'init'; id: number }
  | { type: 'encode'; id: number; payload: MessageWrapper }
  | { type: 'decode'; id: number; payload: ArrayBuffer }
export type WorkerReply =
  | { type: 'init'; id: number }
  | { type: 'encode'; id: number; result: ArrayBuffer }
  | { type: 'decode'; id: number; result: MessageWrapper }
  | { type: 'error'; id: number; error: string }
