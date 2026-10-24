import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'Home', component: () => import('../views/Home.vue') },
  { path: '/app', name: 'WebApp', component: () => import('../views/WebApp.vue') }
  ,{ path: '/verify', name: 'Verify', component: () => import('../views/VerifyCode.vue') }
  ,{ path: '/faq', name: 'FAQ', component: () => import('../views/FAQ.vue') }
  ,{ path: '/features', name: 'Features', component: () => import('../views/Features.vue') }
  ,{ path: '/compare', name: 'Compare', component: () => import('../views/Compare.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
