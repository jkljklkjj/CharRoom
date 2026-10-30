import type { Id, TimeValue, User } from './chat'
export interface ApiBody<T> { data?: T; code?: number; message?: string }
export interface FetchResult<T> { ok: boolean; status: number; body: ApiBody<T> | null; retryAfterSeconds?: number }
export interface Tokens { accessToken: string; refreshToken: string }
export type LoginResult = ({ ok: true } & Tokens) | { ok: false; error: string }
export interface OfflineMessage {
  messageType?: number
  fileUrl?: string
  fileName?: string
  fileSize?: number
  replyToMessageId?: string
  replyToContent?: string
  replyToSender?: string
  id?: string
  messageId?: string
  /** 客户端生成的消息 ID；服务端落库后随同步/历史回传，用于按身份归并本地乐观行。 */
  clientMessageId?: string | null
  senderId: Id
  receiverId: Id
  message: string
  timestamp: TimeValue
  conversationId?: string
  seqId?: Id
}
export interface SyncResult { messages: OfflineMessage[]; nextSeqId: Id; hasMore: boolean }
export interface FriendRequest extends User { requesterId?: Id; reason?: string }
export interface FriendNotification { accepterId: Id }
export interface UserProfile { username?: string; phone?: string; signature?: string; password?: string }
export interface TokenQuota {
  balanceFen?: number
  weeklyCostFen?: number
  freeRemainingFen?: number
  paidBalanceFen?: number
  totalUsedFen?: number
  freeTotalFen?: number
}
export interface TokenPrice { model: string; inputPrice?: number; outputPrice?: number }
export interface Purchase { purchaseId: string; codeUrl?: string }
/** 充值订单状态（只读）。paid=true 表示支付方已确认且已入账。 */
export interface PurchaseStatus {
  purchaseId: string
  status: string
  amountFen?: number
  remainingFen?: number
  paid: boolean
}

export interface RecentHistoryRequest { windowEnd?: number; afterTimestamp?: number; afterId?: string; limit?: number }
export interface RecentHistoryPage {
  messages: OfflineMessage[]; windowEnd: number; nextTimestamp: number | null; nextId: string | null; hasMore: boolean
}
