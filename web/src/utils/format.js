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
