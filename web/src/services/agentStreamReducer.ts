import type { AgentStreamChunk } from '../proto/messages'
import { AGENT_STREAM_TYPE as TYPE } from '../proto/constants'
export interface AgentStreamState { text: string; done: boolean }
/** Stream content is a sequence of deltas; terminal packets never replace its accumulated text. */
export class AgentStreamReducer {
  private sessions = new Map<string, { state: AgentStreamState; seen: Set<number> }>()
  clear() { this.sessions.clear() }
  accept(chunk: AgentStreamChunk): AgentStreamState | null {
    const id = String(chunk.requestId || '')
    if (!id) return null
    const session = this.sessions.get(id) || { state: { text: '', done: false }, seen: new Set<number>() }
    if (session.state.done) {
      return chunk.type === TYPE.STREAM_DONE || chunk.type === TYPE.STREAM_ERROR ? { ...session.state } : null
    }
    if (chunk.seq != null && session.seen.has(chunk.seq)) return null
    if (chunk.seq != null) session.seen.add(chunk.seq)
    let delta = ''
    if (chunk.type === TYPE.STREAM_TEXT) delta = chunk.text || ''
    else if (chunk.type === TYPE.STREAM_TOOL_CALL) delta = `\n\n🔧 ${chunk.toolCall?.name || ''}`
    else if (chunk.type === TYPE.STREAM_TOOL_RESULT) delta = `\n${chunk.toolResult?.success ? '✅' : '❌'} ${chunk.toolResult?.result || ''}`
    else if (chunk.type === TYPE.STREAM_ERROR) delta = `\n\n❌ ${chunk.error?.message || 'Agent error'}`
    session.state = { text: session.state.text + delta, done: chunk.type === TYPE.STREAM_DONE || chunk.type === TYPE.STREAM_ERROR }
    this.sessions.set(id, session)
    if (this.sessions.size > 200) this.sessions.delete(this.sessions.keys().next().value!)
    return { ...session.state }
  }
}
