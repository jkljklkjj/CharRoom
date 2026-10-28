import type { SocialProfile } from '../types/social'

/**
 * “我的名片”本地缓存：进入标签页时先渲染缓存，再后台刷新，
 * 避免每次打开都白屏等待接口。仅缓存 relation=SELF 的本人资料，
 * 按账号隔离，退出登录时由 auth.clearAll 清理。
 */
const KEY_PREFIX = 'charroom_social_profile_'
const CACHE_VERSION = 2
const MAX_AGE_MS=24*60*60*1000

export function ownProfileCacheKey(accountId: string | number) {
  return `${KEY_PREFIX}${accountId}`
}

export function loadOwnProfileCache(accountId: string | number): SocialProfile | null {
  if (!accountId) return null
  try {
    const raw = localStorage.getItem(ownProfileCacheKey(accountId))
    if (!raw) return null
    const parsed = JSON.parse(raw)
    const profile = parsed?.profile as SocialProfile | undefined
    if (!profile || parsed?.version !== CACHE_VERSION || typeof parsed.savedAt!=='number' || Date.now()-parsed.savedAt>=MAX_AGE_MS || parsed.savedAt>Date.now()) return null
    // 只接受本账号的本人资料，防止键错位或旧数据串号
    if (profile.relation !== 'SELF' || String(profile.id) !== String(accountId) || typeof profile.username!=='string') return null
    return profile
  } catch (_) {
    return null
  }
}

export function saveOwnProfileCache(accountId: string | number, profile: SocialProfile | null | undefined) {
  if (!accountId || !profile || profile.relation !== 'SELF' || String(profile.id)!==String(accountId)) return
  try {
    localStorage.setItem(ownProfileCacheKey(accountId), JSON.stringify({ version: CACHE_VERSION, savedAt:Date.now(), profile }))
  } catch (_) {
    // localStorage 满/被禁用时静默跳过，缓存失败不应影响名片本身
  }
}

export function clearOwnProfileCache(accountId: string | number) {
  try {
    localStorage.removeItem(ownProfileCacheKey(accountId))
  } catch (_) {}
}
