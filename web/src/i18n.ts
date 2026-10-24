import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN.json'
import en from './locales/en.json'
import ja from './locales/ja.json'

const i18n = createI18n({
  locale: localStorage.getItem('locale') ||
    (navigator.language.startsWith('zh') ? 'zh-CN' :
     navigator.language.startsWith('ja') ? 'ja' : 'en'),
  fallbackLocale: 'zh-CN',
  messages: { 'zh-CN': zhCN, en, ja },
  legacy: false,
  globalInjection: true,
})

export default i18n
