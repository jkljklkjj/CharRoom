import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN.json'

export type AppLocale = 'zh-CN' | 'en' | 'ja'

const SUPPORTED: readonly AppLocale[] = ['zh-CN', 'en', 'ja']

/** 默认语言随包内联；en/ja 按需加载，避免把三份语言包都塞进入口 chunk */
const LAZY_LOADERS: Record<Exclude<AppLocale, 'zh-CN'>, () => Promise<{ default: Record<string, unknown> }>> = {
  en: () => import('./locales/en.json'),
  ja: () => import('./locales/ja.json')
}

const loaded = new Set<AppLocale>(['zh-CN'])

function detectLocale(): AppLocale {
  // 服务端渲染没有 localStorage/navigator，固定站点默认语言，保证 HTML 与客户端 hydrate 一致
  if (typeof window === 'undefined') return 'zh-CN'
  try {
    const saved = localStorage.getItem('locale')
    if (saved && (SUPPORTED as readonly string[]).includes(saved)) return saved as AppLocale
  } catch {
    // 隐私模式/禁用 storage：退回浏览器语言
  }
  const nav = typeof navigator === 'undefined' ? '' : navigator.language || ''
  return nav.startsWith('zh') ? 'zh-CN' : nav.startsWith('ja') ? 'ja' : 'en'
}

const i18n = createI18n({
  locale: detectLocale(),
  fallbackLocale: 'zh-CN',
  messages: { 'zh-CN': zhCN },
  legacy: false,
  globalInjection: true
})

export const initialLocale = i18n.global.locale.value as AppLocale

/**
 * 确保目标语言的语言包已加载（幂等）。
 * 切换语言前必须先 await，否则页面会短暂显示 key 或回退到中文。
 */
export async function ensureLocale(locale: string): Promise<void> {
  const target = (SUPPORTED as readonly string[]).includes(locale) ? (locale as AppLocale) : 'zh-CN'
  if (loaded.has(target)) return
  const module = await LAZY_LOADERS[target as Exclude<AppLocale, 'zh-CN'>]()
  // 语言包 key 集合必须与 zh-CN 同构；JSON 推断出的类型比 vue-i18n 期望的窄，此处显式对齐
  i18n.global.setLocaleMessage(target, module.default as unknown as typeof zhCN)
  loaded.add(target)
}

export default i18n
