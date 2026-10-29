import { createApp, createSSRApp } from 'vue'
import App from './App.vue'
import router from './router'
import i18n, { ensureLocale, initialLocale } from './i18n'
import './styles.css'
import ToastGlobal from './components/Toast.vue'

// 生产环境静默 debug 日志（大量 console.debug 调用点无需逐个改）
if (import.meta.env.PROD) {
  console.debug = () => {}
}

// 全局挂载 Toast（独立实例，始终存在）
document.body.insertAdjacentHTML('beforeend', '<div id="app-toast"></div>')
createApp(ToastGlobal).mount('#app-toast')

const SSR_LOCALE = 'zh-CN'

async function bootstrap() {
  const container = document.getElementById('app')
  const hydrating = Boolean(container?.hasAttribute('data-ssr'))

  if (!hydrating) {
    // 入口只内联 zh-CN；其它语言先加载语言包，避免首屏闪中文/显示 key
    if (initialLocale !== SSR_LOCALE) {
      try {
        await ensureLocale(initialLocale)
      } catch (error) {
        console.warn('[i18n] 语言包加载失败，回退中文', error)
      }
    }
    createApp(App).use(router).use(i18n).mount('#app')
    return
  }

  // 预渲染产物：必须与 SSR 时的状态完全一致才能复用 DOM。
  // SSR 固定用 zh-CN 渲染，这里也用 zh-CN hydrate，完成后再切到用户语言。
  // locale 的静态类型由 messages 推断为 'zh-CN'，运行时允许切到 en/ja
  const globalLocale = i18n.global.locale as unknown as { value: string }
  globalLocale.value = SSR_LOCALE
  // 必须先用 app.use(router)：初始导航是在插件安装时触发的，
  // 在此之前 await router.isReady() 永远不 resolve，挂载根本不会发生。
  const app = createSSRApp(App)
  app.use(router).use(i18n)
  await router.isReady()
  app.mount('#app')

  if (initialLocale !== SSR_LOCALE) {
    try {
      await ensureLocale(initialLocale)
      globalLocale.value = initialLocale
      document.documentElement.lang = initialLocale
    } catch (error) {
      console.warn('[i18n] 语言包加载失败，保持中文', error)
    }
  }
}

void bootstrap()

// PWA Service Worker 注册由 vite-plugin-pwa（injectRegister: 'inline'）自动完成，
// 此处不再手动注册，避免双注册。更新检查交给 registerType: 'autoUpdate'。
