import type { Id, User } from '../types/chat'
/**
 * 格式化工具函数
 */

/**
 * 获取名称首字母缩写（最多 2 个字符）
 * @param {string} name - 用户名
 * @returns {string} 首字母缩写
 */
export function initials(name: Id | undefined) {
  if (!name) return 'U'
  return String(name).split(' ').map(s => s[0]).slice(0, 2).join('').toUpperCase()
}

/**
 * 获取用户头像 URL（带缓存失效参数）
 * @param {object} user - 用户对象
 * @returns {string|null} 头像 URL
 */
export function avatarSrc(user: User | null | undefined) {
  if (!user || !user.avatarUrl) return ''
  const v = user.avatarKey
  return v ? (user.avatarUrl + (user.avatarUrl.includes('?') ? '&v=' : '?v=') + encodeURIComponent(v)) : user.avatarUrl
}

export const PREVIEW_MAX_LENGTH = 60

/**
 * 会话预览文本：压平换行与连续空白并截断，用于联系人列表第二行展示最新消息。
 * 存储与渲染共用同一处理，保证 IndexedDB 元数据与界面一致。
 */
export function conversationPreviewText(
  message: { text?: string | null } | null | undefined,
  maxLength = PREVIEW_MAX_LENGTH
): string {
  const text = typeof message?.text === 'string' ? message.text.replace(/\s+/g, ' ').trim() : ''
  return text.length > maxLength ? text.slice(0, maxLength) + '…' : text
}
