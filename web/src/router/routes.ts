import type { RouteRecordRaw } from 'vue-router'

/** 路由表：客户端与 SSR 共用（页面组件全部懒加载） */
export const routes: RouteRecordRaw[] = [
  { path: '/', name: 'Home', component: () => import('../views/Home.vue') },
  { path: '/admin', name: 'Admin', component: () => import('../views/AdminConsole.vue') },
  { path: '/app', name: 'WebApp', component: () => import('../views/WebApp.vue') }
  ,{ path: '/verify', name: 'Verify', component: () => import('../views/VerifyCode.vue') }
  ,{ path: '/reset', name: 'ResetPassword', component: () => import('../views/ResetPassword.vue') }
  ,{ path: '/faq', name: 'FAQ', component: () => import('../views/FAQ.vue') }
  ,{ path: '/features', name: 'Features', component: () => import('../views/Features.vue') }
  ,{ path: '/compare', name: 'Compare', component: () => import('../views/Compare.vue') }
  ,{ path: '/copilot', name: 'Copilot', component: () => import('../views/CopilotConsole.vue') }
  ,{ path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('../views/NotFound.vue') }
]
