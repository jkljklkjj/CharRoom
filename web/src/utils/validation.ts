/**
 * 表单验证工具。
 *
 * <p>密码规则必须与后端 `com.chatlite.util.PasswordValidator` 以及原生客户端保持一致：
 * 只要求长度 8–64 位，<b>不</b>要求大写字母、小写字母、数字或特殊符号。</p>
 *
 * <p>校验失败时返回的是 i18n key（不是成品文案），调用方用 `t()` 翻译，
 * 保证三种语言下的提示一致。</p>
 */

export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_LENGTH = 64

/** 密码校验：message 为 i18n key（通过时为空字符串）。 */
export function validatePassword(password: string) {
  if (!password || !password.trim()) {
    return { valid: false, message: 'validation.passwordRequired' }
  }

  if (password.length < PASSWORD_MIN_LENGTH) {
    return { valid: false, message: 'validation.passwordTooShort' }
  }

  if (password.length > PASSWORD_MAX_LENGTH) {
    return { valid: false, message: 'validation.passwordTooLong' }
  }

  return { valid: true, message: '' }
}

/** 邮箱格式校验：message 为 i18n key（通过时为空字符串）。 */
export function validateEmail(email: string) {
  const value = (email || '').trim()
  if (!value) {
    return { valid: false, message: 'validation.emailRequired' }
  }

  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(value)) {
    return { valid: false, message: 'validation.emailInvalid' }
  }

  return { valid: true, message: '' }
}

/** 用户名校验（当前 UI 未使用，保留给后续资料编辑复用）。 */
export function validateUsername(username: string) {
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
