import type { Group, Id, User } from '../types/chat'
import { AGENT_USER_ID } from '../constants'

export type SidebarTab = 'messages' | 'contacts'
export const contactName = (user: User): string => user.name || user.username || user.account || String(user.id)

/** Keep the negative sidebar ID separate from the positive group ID used on the wire. */
export const chatTargetId = (id: Id): string => String(id).replace(/^-/, '')

export function sidebarUsers(users: readonly User[], groups: readonly Group[]): User[] {
  const merged = new Map(users.map(user => [String(user.id), user]))
  for (const group of groups) {
    const id = -Math.abs(Number(group.id))
    if (!Number.isSafeInteger(id) || id === 0) continue
    const existing = merged.get(String(id))
    merged.set(String(id), { ...existing, id, username: group.name || group.groupName || existing?.username || String(group.id) })
  }
  return [...merged.values()]
}

export function matchesContact(user: User, query: string): boolean {
  const keyword = query.trim().toLocaleLowerCase()
  return !keyword || [user.name, user.username, user.account, chatTargetId(user.id)].some(value => value?.toLocaleLowerCase().includes(keyword))
}

export function contactDirectory(users: readonly User[], selfId: Id, query: string, locale: string) {
  const collator = new Intl.Collator(locale, { sensitivity: 'base', numeric: true })
  const sorted = users.filter(user => Number(user.id) !== AGENT_USER_ID && String(user.id) !== String(selfId) && matchesContact(user, query))
    .sort((a, b) => collator.compare(contactName(a), contactName(b)) || collator.compare(String(a.id), String(b.id)))
  return { friends: sorted.filter(user => Number(user.id) > 0), groups: sorted.filter(user => Number(user.id) < 0) }
}
