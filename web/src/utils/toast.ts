export type ToastKind = 'success' | 'error' | 'info' | 'warning'
export type ToastApi = Record<ToastKind, (message: string, duration?: number) => void> & {
  show?: (message: string, type?: ToastKind, duration?: number) => void
}
let toastRef: ToastApi | null = null
const pending: { type: ToastKind; message: string }[] = []
export function registerToast(value: ToastApi) {
  toastRef = value
  for (const { type, message } of pending.splice(0)) value[type](message)
}
function showToast(type: ToastKind, message: string) {
  if (toastRef) toastRef[type](message)
  else pending.push({ type, message })
}
export const toast = {
  success: (message: string) => showToast('success', message),
  error: (message: string) => showToast('error', message),
  info: (message: string) => showToast('info', message),
  warning: (message: string) => showToast('warning', message)
}
export default toast
