/**
 * SSR 入口：构建期在 Node 里把 index 路由渲染成 HTML。
 *
 * 与客户端共用 App.vue / routes / i18n，唯一的差别是路由用内存 history。
 * 语言固定为站点默认的 zh-CN（SSR 没有 localStorage/navigator），
 * 客户端 hydrate 时也用 zh-CN，挂载完成后再切到用户语言，避免 hydration 不匹配。
 */
import { createSSRApp } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { renderToString } from '@vue/server-renderer'
import type { SSRContext } from '@vue/server-renderer'
import App from './App.vue'
import { routes } from './router/routes'
import i18n from './i18n'

export async function render(url: string, context: SSRContext = {}): Promise<string> {
  const app = createSSRApp(App)
  const router = createRouter({ history: createMemoryHistory(), routes })
  app.use(router)
  app.use(i18n)
  await router.push(url)
  await router.isReady()
  return await renderToString(app, context)
}
