import type { Group, Id, User } from '../types/chat'
import {ConversationId} from './conversationId'
import { AGENT_USER_ID } from '../constants'

export type SidebarTab = 'messages' | 'contacts'
export const contactName = (user: User): string => user.name || user.username || user.account || String(user.id)

/** Read the raw server ID only at an API/transport boundary. */
export const chatTargetId = (id: Id): string => ConversationId.parse(id).id

export function sidebarUsers(users: readonly User[], groups: readonly Group[]): User[] {
  const merged = new Map(users.map(user => [ConversationId.forContact(user).key, user]))
  for (const group of groups) {
    const id = String(group.id)
    const key = ConversationId.group(id).key
    const existing = merged.get(key)
    merged.set(key, { ...existing, id, conversationKind: 'group', username: group.name || group.groupName || existing?.username || String(group.id) })
  }
  return [...merged.values()]
}

export function matchesContact(user: User, query: string): boolean {
  const keyword = query.trim().toLocaleLowerCase()
  return !keyword || [user.name, user.username, user.account, String(user.id)].some(value => value?.toLocaleLowerCase().includes(keyword))
}

export function contactDirectory(users: readonly User[], selfId: Id, query: string, locale: string) {
  const collator = new Intl.Collator(locale, { sensitivity: 'base', numeric: true })
  const sorted = users.filter(user => (user.conversationKind==='group' || (Number(user.id) !== AGENT_USER_ID && String(user.id) !== String(selfId))) && matchesContact(user, query))
    .sort((a, b) => collator.compare(contactName(a), contactName(b)) || collator.compare(String(a.id), String(b.id)))
  return { friends: sorted.filter(user => user.conversationKind!=='group'), groups: sorted.filter(user => user.conversationKind==='group') }
}
