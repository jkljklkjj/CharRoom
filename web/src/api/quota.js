/**
 * Token 配额 & 购买 API
 * 从 api/index.js 提取，单一职责
 */
import { safeFetch, API_BASE } from './index.js'

/** 查询当前用户配额信息 */
export async function getTokenQuota() {
  const res = await safeFetch(`${API_BASE}/agent/quota`, { method: 'GET' })
  return res.ok ? (res.body?.data || null) : null
}

/** 获取 Token 价格列表 */
export async function getTokenPrices() {
  const res = await safeFetch(`${API_BASE}/agent/quota/prices`, { method: 'GET' })
  return res.ok ? (res.body?.data || null) : null
}

/** 发起购买（返回 purchaseId） */
export async function purchaseTokens(amountFen) {
  const res = await safeFetch(`${API_BASE}/agent/quota/purchase`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ amount: amountFen })
  })
  return res.ok ? (res.body?.data || res.body) : null
}

/** 确认购买完成 */
export async function confirmPurchase(purchaseId) {
  const res = await safeFetch(`${API_BASE}/agent/quota/purchase/confirm?purchaseId=${purchaseId}`, {
    method: 'POST'
  })
  return res.ok
}
