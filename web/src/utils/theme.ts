/**
 * 全局主题管理（auto / light / dark）
 *
 * 集中管理 data-theme 的应用，供 App.vue（所有页面）与 WebApp.vue（设置面板）共用。
 * 解决旧实现只在 /app 页设置 data-theme，导致其他页面继承陈旧主题状态的问题。
 */
import { ref } from 'vue'

const STORAGE_KEY = 'theme'

/** 当前主题选择：auto / light / dark（ref，供设置面板高亮当前项） */
export const theme = ref(localStorage.getItem(STORAGE_KEY) || 'auto')

/** 根据选择（或系统偏好）应用 data-theme 到 <html> */
export function applyTheme(mode?: string) {
  const m = mode || theme.value
  if (m === 'dark') {
    document.documentElement.setAttribute('data-theme', 'dark')
  } else if (m === 'light') {
    document.documentElement.setAttribute('data-theme', 'light')
  } else {
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    document.documentElement.setAttribute('data-theme', prefersDark ? 'dark' : 'light')
  }
}

/** 用户显式选择主题并持久化 */
export function setTheme(mode: string) {
  theme.value = mode
  localStorage.setItem(STORAGE_KEY, mode)
  applyTheme(mode)
}

let initialized = false

/**
 * 全局初始化：应用当前主题，并监听系统主题变化（auto 模式下跟随）。
 * 幂等，可在 App.vue 与 WebApp.vue 重复调用。
 */
export function initTheme() {
  if (initialized) return
  initialized = true
  applyTheme(theme.value)
  const mq = window.matchMedia('(prefers-color-scheme: dark)')
  const handler = () => {
    if (theme.value === 'auto') applyTheme('auto')
  }
  if (mq.addEventListener) {
    mq.addEventListener('change', handler)
  } else {
    // 兼容旧版 Safari
    ;(mq as unknown as { addListener: (cb: () => void) => void }).addListener(handler)
  }
}
