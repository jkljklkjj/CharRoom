import type { Id, TimeValue, User } from './chat'
export interface ApiBody<T> { data?: T; code?: number; message?: string }
export interface FetchResult<T> { ok: boolean; status: number; body: ApiBody<T> | null }
export interface Tokens { accessToken: string; refreshToken: string }
export type LoginResult = ({ ok: true } & Tokens) | { ok: false; error: string }
export interface OfflineMessage {
  id?: string
  messageId?: string
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
