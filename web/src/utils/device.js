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
 * 判断当前设备类型
 * Web 前端统一返回 'web'，用于 per-device sync cursor
 */
export function getDeviceType() {
  return 'web'
}

/**
 * 获取或设置当前设备类型（持久化到 localStorage）
 * 用于 sync API 传递正确的 deviceType
 */
export function getOrSetDeviceType() {
  const key = 'charroom_deviceType'
  let deviceType = localStorage.getItem(key)
  if (!deviceType) {
    deviceType = getDeviceType()
    localStorage.setItem(key, deviceType)
  }
  return deviceType
}
