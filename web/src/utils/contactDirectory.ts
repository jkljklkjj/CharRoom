import type { ConversationState, Group, Id, User } from '../types/chat'
import {ConversationId} from './conversationId'
import { AGENT_USER_ID } from '../constants'

export type SidebarTab = 'messages' | 'contacts'
export const contactName = (user: User): string => user.name || user.username || user.account || String(user.id)

/** Read the raw server ID only at an API/transport boundary. */
export const chatTargetId = (id: Id): string => ConversationId.parse(id).id

export function sidebarUsers(users: readonly User[], groups: readonly Group[], selfId?: Id): User[] {
  const merged = new Map(users.map(user => [ConversationId.forContact(user).key, user]))
  for (const group of groups) {
    const id = String(group.id)
    const key = ConversationId.group(id).key
    const existing = merged.get(key)
    merged.set(key, { ...existing, id, conversationKind: 'group', username: group.name || group.groupName || existing?.username || String(group.id) })
  }
  const self = selfId == null ? '' : String(selfId)
  // 自己和群组可能数字同号：按会话类型区分，群组同名 ID 不能被当成本人过滤掉
  return [...merged.values()].filter(user => user.conversationKind === 'group' || String(user.id) !== self)
}

/**
 * 搜索用的文本归一化：NFKC 把全角字符（２、Ａ、全角空格）折成半角，
 * 再去首尾空白、转小写。不做这一步时，昵称「昵称２」用 "2" 搜不到、大小写/全角
 * 输入也被当成不同关键字。
 */
const normalizeSearchText = (value: string): string => value.normalize('NFKC').toLocaleLowerCase().trim()

/**
 * 联系人搜索只匹配昵称（含群名），不再把账号/ID 当模糊关键字，
 * 避免输入数字时意外命中一串无关账号。
 */
function matchesKeyword(user: User, keyword: string): boolean {
  return !keyword || [user.name, user.username]
    .some(value => value != null && normalizeSearchText(String(value)).includes(keyword))
}

export function filterContacts(users: User[], query: string): User[] {
  const keyword = normalizeSearchText(query)
  return keyword ? users.filter(user => matchesKeyword(user, keyword)) : users
}

export interface ContactDirectory { friends: User[]; groups: User[] }
export function filterContactDirectory(directory: ContactDirectory, query: string): ContactDirectory {
  if (!query.trim()) return directory
  return { friends: filterContacts(directory.friends, query), groups: filterContacts(directory.groups, query) }
}

export function contactDirectory(users: readonly User[], selfId: Id, locale: string): ContactDirectory {
  const collator = new Intl.Collator(locale, { sensitivity: 'base', numeric: true })
  const sorted = users.filter(user => user.conversationKind==='group' || (Number(user.id) !== AGENT_USER_ID && String(user.id) !== String(selfId)))
    .sort((a, b) => collator.compare(contactName(a), contactName(b)) || collator.compare(String(a.id), String(b.id)))
  return { friends: sorted.filter(user => user.conversationKind!=='group'), groups: sorted.filter(user => user.conversationKind==='group') }
}

/** Compute identities and activity once, outside the O(n log n) comparator. Pinned rows always lead. */
export function sortConversationUsers(users: readonly User[], activity: (key: string) => ConversationState | undefined,
                                      pinned: (key: string) => boolean = () => false): User[] {
  return users.map(user => {
    const key = ConversationId.forContact(user).key
    const summary = activity(key)
    return { user, pinned: pinned(key) ? 1 : 0, time: summary?.lastIncomingMessageTime || 0, unread: summary?.unreadCount || 0 }
  }).sort((a, b) => b.pinned - a.pinned || b.time - a.time || b.unread - a.unread).map(row => row.user)
}
