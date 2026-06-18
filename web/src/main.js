import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './styles.css'
import ToastGlobal from './components/Toast.vue'

// 全局挂载 Toast（独立实例，始终存在）
document.body.insertAdjacentHTML('beforeend', '<div id="app-toast"></div>')
createApp(ToastGlobal).mount('#app-toast')

const app = createApp(App).use(router).mount('#app')

// PWA Service Worker 注册
if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js')
      .then(registration => {
        console.log('✅ PWA ServiceWorker 注册成功:', registration.scope)

        // 自动检查更新
        registration.addEventListener('updatefound', () => {
          const newWorker = registration.installing
          newWorker.addEventListener('statechange', () => {
            if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
              // 有新版本可用，可以提示用户刷新
              console.log('🔄 PWA 有新版本可用')
            }
          })
        })
      })
      .catch(error => {
        console.error('❌ PWA ServiceWorker 注册失败:', error)
      })
  })
}
