import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import WebApp from '../views/WebApp.vue'
import Verify from '../views/VerifyCode.vue'

const routes = [
  { path: '/', name: 'Home', component: Home },
  { path: '/app', name: 'WebApp', component: WebApp }
  ,{ path: '/verify', name: 'Verify', component: Verify }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
