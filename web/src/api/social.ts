import { API_BASE, safeFetch } from './http'
import type { SocialProfile, SocialEdit, SocialDirectory, DailyQuestion, EncounterState, EncounterAction } from '../types/social'

async function request<T>(path: string, method = 'GET', body?: unknown, signal?: AbortSignal): Promise<T> {
  const result = await safeFetch<T>(`${API_BASE}/social${path}`, {
    method, signal, headers: { 'Content-Type': 'application/json' },
    ...(body === undefined ? {} : { body: JSON.stringify(body) })
  })
  if (!result.ok || result.body?.code !== 0 || result.body.data === undefined) {
    throw new Error(result.body?.message || `HTTP ${result.status}`)
  }
  return result.body.data
}
export const getSocialProfile = (id: string, signal?: AbortSignal) => request<SocialProfile>(`/profiles/${encodeURIComponent(id)}`, 'GET', undefined, signal)
export const saveSocialProfile = (profile: SocialEdit) => request<SocialProfile>('/profile', 'PUT', profile)
export function discoverPeople(filters: { q?: string; tag?: string; channel?: string; cursor?: number }, signal?: AbortSignal) {
  const query = new URLSearchParams(Object.entries(filters).map(([key, value]) => [key, String(value)]))
  return request<SocialDirectory>(`/discovery?${query}`, 'GET', undefined, signal)
}
export const sendSocialFriendRequest = (targetId: string, reason: string) => request<boolean>('/friend-requests', 'POST', { targetId, reason })
export const blockSocialUser = (targetId: string) => request<boolean>(`/blocks/${encodeURIComponent(targetId)}`, 'POST')
export const getBlockedUsers = () => request<Array<{ id: number; username: string }>>('/blocks')
export const unblockSocialUser = (targetId: number) => request<boolean>(`/blocks/${targetId}`, 'DELETE')
export const reportSocialUser = (input: { targetId?: number; roomId?: string; reason: string }) => request<boolean>('/reports', 'POST', input)
export const getDailyQuestion = (cursor = '', signal?: AbortSignal) => request<DailyQuestion>(`/daily?cursor=${encodeURIComponent(cursor)}`, 'GET', undefined, signal)
export const answerDailyQuestion = (day: string, content: string) => request<boolean>('/daily', 'PUT', { day, content })
export const getEncounter = (after = 0, signal?: AbortSignal) => request<EncounterState>(`/encounter?after=${after}`, 'GET', undefined, signal)
export const actEncounter = (action: EncounterAction, signal?: AbortSignal) => request<EncounterState>('/encounter', 'POST', action, signal)
