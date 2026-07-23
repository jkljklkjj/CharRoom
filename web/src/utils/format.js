/**
 * 格式化工具函数
 */

/**
 * 获取名称首字母缩写（最多 2 个字符）
 * @param {string} name - 用户名
 * @returns {string} 首字母缩写
 */
export function initials(name) {
  if (!name) return 'U'
  return name.split(' ').map(s => s[0]).slice(0, 2).join('').toUpperCase()
}

/**
 * 获取用户头像 URL（带缓存失效参数）
 * @param {object} user - 用户对象
 * @returns {string|null} 头像 URL
 */
export function avatarSrc(user) {
  if (!user || !user.avatarUrl) return null
  const v = user.avatarKey
  return v ? (user.avatarUrl + (user.avatarUrl.includes('?') ? '&v=' : '?v=') + encodeURIComponent(v)) : user.avatarUrl
}
