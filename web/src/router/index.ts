import { createRouter, createWebHistory, createMemoryHistory } from 'vue-router'
import { routes } from './routes'

// SSR（Node）没有 window.history，用内存路由；客户端保持浏览器历史
const history = typeof window === 'undefined' ? createMemoryHistory() : createWebHistory()

const router = createRouter({
  history,
  routes
})

export default router
