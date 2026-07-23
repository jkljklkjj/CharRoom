/**
 * Toast 通知服务
 * 替代 window.$toast 全局模式，提供更可靠的 toast 通知
 */

let _toastRef = null
const _pendingQueue = []

/**
 * 注册 toast 组件引用
 */
export function registerToast(toastRef) {
  _toastRef = toastRef
  // 处理排队的通知
  while (_pendingQueue.length > 0) {
    const { type, message } = _pendingQueue.shift()
    _toastRef[type]?.(message)
  }
}

/**
 * 显示 toast 通知
 */
function showToast(type, message) {
  if (_toastRef) {
    _toastRef[type]?.(message)
  } else {
    // 组件未就绪，加入队列
    _pendingQueue.push({ type, message })
  }
}

export const toast = {
  success: (msg) => showToast('success', msg),
  error: (msg) => showToast('error', msg),
  info: (msg) => showToast('info', msg),
  warning: (msg) => showToast('warning', msg)
}

export default toast
