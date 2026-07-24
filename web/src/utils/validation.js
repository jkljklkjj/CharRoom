/**
 * 表单验证工具
 */

/**
 * 密码强度校验
 * @param {string} password - 密码
 * @returns {{ valid: boolean, message: string }}
 */
export function validatePassword(password) {
  if (!password) {
    return { valid: false, message: '请输入密码' }
  }

  if (password.length < 8) {
    return { valid: false, message: '密码长度至少 8 位' }
  }

  if (password.length > 64) {
    return { valid: false, message: '密码长度不能超过 64 位' }
  }

  if (!/[a-z]/.test(password)) {
    return { valid: false, message: '密码必须包含小写字母' }
  }

  if (!/[A-Z]/.test(password)) {
    return { valid: false, message: '密码必须包含大写字母' }
  }

  if (!/[0-9]/.test(password)) {
    return { valid: false, message: '密码必须包含数字' }
  }

  return { valid: true, message: '' }
}

/**
 * 邮箱格式校验
 * @param {string} email - 邮箱
 * @returns {{ valid: boolean, message: string }}
 */
export function validateEmail(email) {
  if (!email) {
    return { valid: false, message: '请输入邮箱' }
  }

  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(email)) {
    return { valid: false, message: '邮箱格式不正确' }
  }

  return { valid: true, message: '' }
}

/**
 * 用户名校验
 * @param {string} username - 用户名
 * @returns {{ valid: boolean, message: string }}
 */
export function validateUsername(username) {
  if (!username) {
    return { valid: false, message: '请输入用户名' }
  }

  if (username.length < 2) {
    return { valid: false, message: '用户名长度至少 2 位' }
  }

  if (username.length > 20) {
    return { valid: false, message: '用户名长度不能超过 20 位' }
  }

  if (!/^[a-zA-Z0-9_一-龥]+$/.test(username)) {
    return { valid: false, message: '用户名只能包含字母、数字、下划线和中文' }
  }

  return { valid: true, message: '' }
}
