/**
 * 客服 AI 副驾 API（坐席工作台）。
 * 说明见 docs/copilot/AI-COPILOT.md。
 */
import { safeFetch, API_BASE } from './http'

interface CopilotOptions { method?: string; headers?: Record<string, string>; body?: string }

async function call<T = unknown>(path: string, options: CopilotOptions = {}): Promise<T | null> {
  const res = await safeFetch<T>(`${API_BASE}/copilot${path}`, options)
  if (!res.ok) return null
  return (res.body?.data ?? res.body ?? null) as T | null
}

function json(method: string, payload: unknown): CopilotOptions {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }
}

// 租户
export function bootstrapOrg(name: string) {
  return call('/org/bootstrap', json('POST', { name }))
}
export function getCopilotMe() {
  return call('/me')
}
export function addCopilotMember(userId: number, role: string, seatName?: string) {
  return call('/members', json('POST', { userId, role, seatName }))
}

// 会话
export function listCopilotConversations(status?: string) {
  return call(`/conversations${status ? `?status=${encodeURIComponent(status)}` : ''}`)
}
export function listMyCopilotConversations() {
  return call('/conversations/mine')
}
export function openCopilotConversation(customerId: number) {
  return call('/conversations', json('POST', { customerId }))
}
export function getCopilotConversation(id: string | number) {
  return call(`/conversations/${id}`)
}
export function claimCopilotConversation(id: string | number) {
  return call(`/conversations/${id}/claim`, { method: 'POST' })
}
export function transferCopilotConversation(id: string | number, toSeatId: number) {
  return call(`/conversations/${id}/transfer`, json('POST', { toSeatId }))
}
export function closeCopilotConversation(id: string | number) {
  return call(`/conversations/${id}/close`, { method: 'POST' })
}
export function sendCopilotCustomerMessage(id: string | number, content: string) {
  return call(`/conversations/${id}/customer-message`, json('POST', { content }))
}
export function sendCopilotMessage(id: string | number, content: string, suggestionId?: string | number) {
  return call(`/conversations/${id}/send`, json('POST', { content, suggestionId }))
}

// 建议
export function listCopilotSuggestions(id: string | number, kind?: string) {
  return call(`/conversations/${id}/suggestions${kind ? `?kind=${encodeURIComponent(kind)}` : ''}`)
}
export function copilotFeedback(id: string | number, adopted: boolean) {
  return call(`/suggestions/${id}/feedback`, json('POST', { adopted }))
}

// 知识库
export function listCopilotKnowledge() {
  return call('/knowledge')
}
export function createCopilotKnowledge(title: string, content: string, tags?: string[]) {
  return call('/knowledge', json('POST', { title, content, tags }))
}
export function deleteCopilotKnowledge(id: string | number) {
  return call(`/knowledge/${id}`, { method: 'DELETE' })
}

// 指标与审计
export function getCopilotMetrics() {
  return call('/metrics')
}
export function listCopilotAudit() {
  return call('/audit')
}

// 渠道（H5 / 企业微信）
export function listCopilotChannels() {
  return call('/channels')
}
export function createCopilotChannel(
  name: string,
  type: string,
  welcomeMessage?: string,
  autoReply?: boolean,
  settings?: unknown,
) {
  return call('/channels', json('POST', { name, type, welcomeMessage, autoReply, settings }))
}
export function updateCopilotChannel(id: string | number, payload: Record<string, unknown>) {
  return call(`/channels/${id}`, json('PUT', payload))
}
export function deleteCopilotChannel(id: string | number) {
  return call(`/channels/${id}`, { method: 'DELETE' })
}
