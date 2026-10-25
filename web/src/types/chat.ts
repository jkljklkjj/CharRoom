/** UI/store contracts. Protocol wire objects are generated separately from .proto. */
export type Id = string | number
export type MessageStatus = 'sending' | 'sent' | 'failed' | boolean
export type TimeValue = string | number

export interface ChatMessage {
  user: string
  text: string
  targetId?: Id
  groupId?: Id
  time?: TimeValue
  timestamp?: TimeValue
  messageId?: string
  seqId?: Id
  isSent?: MessageStatus
  done?: boolean
  replyTo?: { sender: string; content: string; messageId?: string }
}

export interface User {
  id: Id
  username?: string
  name?: string
  account?: string
  online?: boolean
  status?: string
  avatarUrl?: string
  avatarKey?: string
  signature?: string
  phone?: string
  email?: string
}
export interface Group {
  id: Id
  name?: string
  groupName?: string
}
export interface ConversationState {
  lastIncomingMessageTime: number
  unreadCount: number
  /** 最新一条消息的预览文本（联系人列表第二行）；无历史消息时为空 */
  lastMessagePreview?: string
}
export type ConversationPatch = Partial<ConversationState> & { unreadDelta?: number }
export interface PendingRegister { email: string; password: string; username?: string }
export interface ChatState {
  users: User[]
  groups: Group[]
  messages: ChatMessage[]
  groupMessages: ChatMessage[]
  conversationStates: Record<string, ConversationState>
  conversationSeqIds: Record<string, Id>
  token: string
  refreshToken: string
  accountId: string
  pendingRegister: PendingRegister | null
  selectedChatId: Id | null
  loginValid: boolean
  hasMoreMessages: boolean
  hasMoreGroupMessages: boolean
}
