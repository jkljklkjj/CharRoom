import type {MessageStatus} from '../types/chat'

export const SEND_INDICATOR_DELAY_MS=4000
export const SEND_FAILURE_DELAY_MS=10000
export type SendIndicatorPhase='sending'|'failed'|null

/** Display deadlines are independent of the durable outbox's retry schedule. */
export function messageSendState(status:MessageStatus|undefined,startedAt:number,now:number):{phase:SendIndicatorPhase;nextAt:number|null} {
  if(status==='failed')return {phase:'failed',nextAt:null}
  if(status!=='sending')return {phase:null,nextAt:null}
  if(now<startedAt+SEND_INDICATOR_DELAY_MS)return {phase:null,nextAt:startedAt+SEND_INDICATOR_DELAY_MS}
  if(now<startedAt+SEND_FAILURE_DELAY_MS)return {phase:'sending',nextAt:startedAt+SEND_FAILURE_DELAY_MS}
  return {phase:'failed',nextAt:null}
}
