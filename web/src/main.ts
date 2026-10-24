import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import './styles.css'
import ToastGlobal from './components/Toast.vue'

// 生产环境静默 debug 日志（大量 console.debug 调用点无需逐个改）
if (import.meta.env.PROD) {
  console.debug = () => {}
}

// 全局挂载 Toast（独立实例，始终存在）
document.body.insertAdjacentHTML('beforeend', '<div id="app-toast"></div>')
createApp(ToastGlobal).mount('#app-toast')

createApp(App).use(router).use(i18n).mount('#app')

// PWA Service Worker 注册由 vite-plugin-pwa（injectRegister: 'inline'）自动完成，
// 此处不再手动注册，避免双注册。更新检查交给 registerType: 'autoUpdate'。
