import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import WebApp from '../views/WebApp.vue'
import Verify from '../views/VerifyCode.vue'
import FAQ from '../views/FAQ.vue'
import Features from '../views/Features.vue'
import Compare from '../views/Compare.vue'

const routes = [
  { path: '/', name: 'Home', component: Home },
  { path: '/app', name: 'WebApp', component: WebApp }
  ,{ path: '/verify', name: 'Verify', component: Verify }
  ,{ path: '/faq', name: 'FAQ', component: FAQ }
  ,{ path: '/features', name: 'Features', component: Features }
  ,{ path: '/compare', name: 'Compare', component: Compare }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
