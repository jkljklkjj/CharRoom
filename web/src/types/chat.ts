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
  /** 服务端回传的客户端 ID（若有）。写入持久别名后，归并不再依赖文本与时间窗。 */
  clientMessageId?: string
  messageType?: 0 | 1 | 2
  fileUrl?: string
  fileName?: string
  fileSize?: number
  seqId?: Id
  isSent?: MessageStatus
  sendStartedAt?: number
  done?: boolean
  replyTo?: { sender: string; content: string; messageId?: string }
}

export interface User {
  id: Id
  conversationKind?: 'private' | 'group'
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
  directoryPhase: import('../services/contactDirectorySync').DirectoryPhase
  historyHasNewer: boolean
  historyFollowingLatest: boolean
  historyLoading: boolean
  historyError: boolean
  historySyncing: boolean
  historySyncError: boolean
  users: User[]
  groups: Group[]
  messages: ChatMessage[]
  groupMessages: ChatMessage[]
  conversationStates: Record<string, ConversationState>
  conversationSeqIds: Record<string, Id>
  /** 会话列表偏好：只影响展示，不删除 IndexedDB 中的聊天记录 */
  pinnedConversations: string[]
  hiddenConversations: string[]
  token: string
  refreshToken: string
  accountId: string
  pendingRegister: PendingRegister | null
  selectedChatId: import('../utils/conversationId').ConversationKey | null
  loginValid: boolean
  hasMoreMessages: boolean
  hasMoreGroupMessages: boolean
}
