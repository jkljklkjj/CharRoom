/**
 * 设备相关工具函数
 */

/**
 * 获取或生成本地设备 ID（持久化到 localStorage）
 */
export function getDeviceId() {
  const key = 'charroom_device_id'
  let id = localStorage.getItem(key)
  if (!id) {
    id = crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36) + Math.random().toString(36).slice(2, 10)
    localStorage.setItem(key, id)
  }
  return id
}

/**
 * 设备类型，Web 前端固定为 'web'
 */
export const DEVICE_TYPE = 'web'

/**
 * 获取设备类型
 */
export function getDeviceType() {
  return DEVICE_TYPE
}
