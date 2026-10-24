import type { TokenQuota, TokenPrice, Purchase, PurchaseStatus } from '../types/api'
/**
 * Token 配额 & 购买 API
 * 从 api/index.ts 提取，单一职责
 */
import { safeFetch, API_BASE } from './http'

/** 查询当前用户配额信息 */
export async function getTokenQuota() {
  const res = await safeFetch<TokenQuota>(`${API_BASE}/agent/quota`, { method: 'GET' })
  return res.ok ? (res.body?.data || null) : null
}

/** 获取 Token 价格列表 */
export async function getTokenPrices() {
  const res = await safeFetch<TokenPrice[]>(`${API_BASE}/agent/quota/prices`, { method: 'GET' })
  return res.ok ? (res.body?.data || null) : null
}

/** 发起购买（返回 purchaseId） */
export async function purchaseTokens(amountFen: number) {
  const res = await safeFetch<Purchase>(`${API_BASE}/agent/quota/purchase`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ amount: amountFen })
  })
  return res.ok ? (res.body?.data || null) : null
}

/** 查询订单状态（只读）。服务端在 pending 时主动查单并入账，客户端不直接改余额。 */
export async function getPurchaseStatus(purchaseId: string) {
  const res = await safeFetch<PurchaseStatus>(`${API_BASE}/agent/quota/purchase/${purchaseId}`, {
    method: 'GET'
  })
  return res.ok ? (res.body?.data || null) : null
}
