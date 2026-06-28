<template>
  <div class="app-wrap">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-wrap">
      <div class="loading-spinner"></div>
      <p>{{ $t('app.loading') }}</p>
    </div>

    <LoginRegister v-else-if="!store.state.token || !store.state.loginValid" @logged="onLogged" />

    <div v-else class="app-main" :class="{ 'mobile-view': isMobile }">
      <!-- 移动端视图 -->
      <template v-if="isMobile">
        <!-- 好友列表视图 -->
        <SidebarUsers
          class="sidebar"
          v-if="currentView === 'list'"
          @user-selected="onUserSelected"
          @open-settings="showSettings = true"
        />

        <!-- 聊天视图 -->
        <div class="chat-view" v-if="currentView === 'chat'">
          <!-- 移动端顶部栏 -->
          <div class="mobile-header">
            <button class="back-btn" @click="backToList">{{ $t('app.back') }}</button>
            <div class="chat-title">{{ currentChatName }}</div>
          </div>
          <ChatWindow class="chat" />
        </div>
      </template>

      <!-- 桌面端视图 -->
      <template v-else>
        <SidebarUsers class="sidebar" @open-settings="showSettings = true" />
        <ChatWindow class="chat" />
      </template>
    </div>

    <div v-if="showSettings" class="settings-overlay" @click.self="closeSettings">
      <div class="settings-dialog">
        <div class="settings-header">
          <h3>{{ $t('app.settings.title') }}</h3>
          <button class="close-settings" @click="closeSettings">×</button>
        </div>
        <div class="settings-body">
          <div class="settings-item">
            <div class="transport-info">
              <label>{{ $t('app.settings.transportLabel') }}</label>
              <div class="transport-badge wt">{{ $t('app.settings.transportValue') }}</div>
            </div>
          </div>
          <div class="settings-item">
            <label>{{ $t('app.settings.themeLabel') }}</label>
            <div class="theme-options">
              <button :class="{ active: theme === 'auto' }" @click="setTheme('auto')">{{ $t('app.settings.themeAuto') }}</button>
              <button :class="{ active: theme === 'light' }" @click="setTheme('light')">{{ $t('app.settings.themeLight') }}</button>
              <button :class="{ active: theme === 'dark' }" @click="setTheme('dark')">{{ $t('app.settings.themeDark') }}</button>
            </div>
          </div>
          <div class="settings-item">
            <label>{{ $t('app.settings.languageLabel') }}</label>
            <div class="theme-options">
              <button :class="{ active: locale === 'zh-CN' }" @click="switchLang('zh-CN')">{{ $t('app.settings.languageZh') }}</button>
              <button :class="{ active: locale === 'en' }" @click="switchLang('en')">{{ $t('app.settings.languageEn') }}</button>
              <button :class="{ active: locale === 'ja' }" @click="switchLang('ja')">日本語</button>
            </div>
          </div>
          <button class="quota-btn" @click="showQuota = true">{{ $t('app.settings.tokenQuota') }}</button>
          <button class="logout-btn" @click="logout">{{ $t('app.settings.logout') }}</button>
        </div>
      </div>
    </div>
  </div>
  <TokenQuotaDialog v-if="showQuota" @close="showQuota = false" />
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import LoginRegister from '../components/LoginRegister.vue'
import SidebarUsers from '../components/SidebarUsers.vue'
import TokenQuotaDialog from '../components/TokenQuotaDialog.vue'
import ChatWindow from '../components/ChatWindow.vue'
import { useStore } from '../store'
import chatSocket from '../services/chatSocket'
import siteConfig from '../siteConfig'
import api from '../api'

function normalizeTimestamp(raw) {
  if (raw == null || raw === '') return new Date().toISOString()
  if (typeof raw === 'number') return new Date(raw).toISOString()
  if (typeof raw === 'string') {
    const numeric = Number(raw)
    if (!Number.isNaN(numeric) && String(numeric) === raw.trim()) {
      return new Date(numeric).toISOString()
    }
    const parsed = new Date(raw)
    if (!Number.isNaN(parsed.getTime())) return parsed.toISOString()
  }
  return new Date().toISOString()
}

const store = useStore()
const router = useRouter()
const { t, locale } = useI18n()

// 移动端适配
const isMobile = ref(window.innerWidth < 768)
const currentView = ref('list') // 'list' | 'chat'
const currentChatName = ref('')
const showSettings = ref(false)
const showQuota = ref(false)
const loading = ref(true) // 登录状态验证中

// 主题设置
const theme = ref(localStorage.getItem('theme') || 'auto') // auto, light, dark

function setTheme(newTheme) {
  theme.value = newTheme
  localStorage.setItem('theme', newTheme)
  applyTheme(newTheme)
}

function switchLang(lang) {
  locale.value = lang
  localStorage.setItem('locale', lang)
  document.documentElement.lang = lang
}

function applyTheme(theme) {
  if (theme === 'dark') {
    document.documentElement.setAttribute('data-theme', 'dark')
  } else if (theme === 'light') {
    document.documentElement.setAttribute('data-theme', 'light')
  } else {
    // 自动跟随系统
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    document.documentElement.setAttribute('data-theme', prefersDark ? 'dark' : 'light')
  }
}

function handleResize() {
  isMobile.value = window.innerWidth < 768
  if (!isMobile.value) {
    currentView.value = 'list'
  }
}

/** 处理 WebSocket 收到的消息 */
function handleIncomingMessage(msg) {
  try {
    if (msg.type === 'chat' && msg.chat) {
      store.addMessage({
        user: String(msg.chat.userId || 'unknown'),
        text: msg.chat.content,
        time: normalizeTimestamp(msg.chat.timestamp),
        targetId: msg.chat.targetClientId
      })
    } else if (msg.type === 'group_chat' && msg.groupChat) {
      store.addGroupMessage({
        user: String(msg.groupChat.userId),
        text: msg.groupChat.content,
        time: normalizeTimestamp(msg.groupChat.timestamp),
        groupId: msg.groupChat.targetClientId
      })
    } else if (msg.clientId !== undefined && msg.online !== undefined) {
      store.updateUserOnlineStatus(parseInt(msg.clientId), msg.online)
    }
  } catch (e) {
    console.error('处理消息失败', e)
  }
}

/** 登录后拉取所有会话的离线消息 */
async function syncAllConversations() {
  const friends = store.state.users
  if (!friends || friends.length === 0) return
  console.log("📡 拉取离线消息，共", friends.length, "个私聊会话")
  for (const friend of friends) {
    const ids = [Number(store.state.accountId), Number(friend.id)].sort((a, b) => a - b)
    const convId = 'user:' + ids[0] + ':' + ids[1]
    const seqId = store.getConversationSeqId(convId) || 0
    try {
      const result = await api.syncMessages(convId, seqId, 100)
      if (result.messages && result.messages.length > 0) {
        console.log("📩 会话", convId, "拉取到", result.messages.length, "条新消息")
        for (const msg of result.messages) {
          store.addMessage({
            user: String(msg.senderId === store.state.accountId ? 'you' : msg.senderId),
            text: msg.message,
            time: msg.timestamp,
            targetId: String(msg.senderId === store.state.accountId ? msg.receiverId : msg.senderId),
            seqId: msg.seqId
          })
        }
        store.setConversationSeqId(convId, result.nextSeqId)
      }
    } catch (e) {
      console.warn("⚠️ 拉取会话", convId, "失败:", e.message)
    }
  }

  // 同步群聊消息
  const groups = store.state.groups
  if (groups && groups.length > 0) {
    console.log("📡 拉取群聊消息，共", groups.length, "个群")
    for (const group of groups) {
      const convId = 'group:' + group.id
      const seqId = store.getConversationSeqId(convId) || 0
      try {
        const result = await api.syncMessages(convId, seqId, 50)
        if (result.messages && result.messages.length > 0) {
          console.log("📩 群聊", convId, "拉取到", result.messages.length, "条新消息")
          for (const msg of result.messages) {
            store.addGroupMessage({
              user: String(msg.senderId),
              text: msg.message,
              time: msg.timestamp,
              groupId: String(group.id),
              seqId: msg.seqId
            })
          }
          store.setConversationSeqId(convId, result.nextSeqId)
        }
      } catch (e) {
        console.warn("⚠️ 拉取群聊", convId, "失败:", e.message)
      }
    }
  }

  console.log("✅ 离线消息拉取完成")
}

/** 建立 WebTransport 连接 */
function connectChat(token, accountId) {
  const transportConfig = siteConfig.TRANSPORT || {}
  const host = 'quic.chatlite.xin'
  const port = transportConfig.port || 8080
  chatSocket.connect(host, port, token, String(accountId), {
    onmessage: handleIncomingMessage,
    onAuthFailed: () => logout()
  })
}

/** 清除本地登录状态 */
function clearAuth() {
  localStorage.removeItem('charroom_token')
  localStorage.removeItem('charroom_refreshToken')
  localStorage.removeItem('charroom_accountId')
  store.setToken('')
  store.setRefreshToken('')
  store.setAccountId('')
  store.setLoginValid(false)
}

function logout() {
  chatSocket.close()
  store.clearAll()
  clearAuth()
  store.setSelectedChat(null)
  showSettings.value = false
}

/** 保存登录凭证到本地 */
function persistTokens(accessToken, refreshToken) {
  try {
    localStorage.setItem('charroom_token', accessToken)
    localStorage.setItem('charroom_refreshToken', refreshToken || '')
  } catch (_e) { /* ignore */ }
}

/** 获取当前用户信息并标记登录有效 */
async function initUserSession(accessToken, refreshToken) {
  store.setToken(accessToken)
  store.setRefreshToken(refreshToken || '')

  const userRes = await api.getCurrentUser()
  if (!userRes?.id) {
    clearAuth()
    return false
  }

  store.setAccountId(userRes.id)
  store.setLoginValid(true)
  persistTokens(accessToken, refreshToken)
  localStorage.setItem('charroom_accountId', String(userRes.id))

  // 好友列表增量更新：先展示缓存，再静默 merge
  const cached = store.loadCachedUsers()
  if (cached.length > 0) {
    store.mergeUsers(cached)  // 即时呈现缓存
  }

  const friends = await api.getFriends()
  store.mergeUsers(friends || [])   // 静默 merge，不重建 DOM
  store.cacheUsers(friends || [])   // 缓存到 localStorage

  // 获取用户的群聊列表
  const myGroups = await api.getMyGroups()
  store.setGroups(myGroups || [])

  connectChat(accessToken, userRes.id)
  syncAllConversations()
  return true
}

/** 登录成功回调 */
async function onLogged(tokens) {
  const accessToken = tokens?.accessToken || ''
  const refreshToken = tokens?.refreshToken || ''
  if (!accessToken) return
  await initUserSession(accessToken, refreshToken)
}

// 选中用户，切换到聊天视图
function onUserSelected(user) {
  if (isMobile.value) {
    currentChatName.value = user.name || user.account || user.username || t('chat.userPrefix', { id: user.id })
    currentView.value = 'chat'
  }
}

function backToList() {
  currentView.value = 'list'
  store.setSelectedChat(null)
}

function closeSettings() {
  showSettings.value = false
}

// 页面加载时恢复登录状态
onMounted(async () => {
  window.addEventListener('resize', handleResize)

  // 初始化主题
  applyTheme(theme.value)
  // 监听系统主题变化
  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  const handleSystemThemeChange = () => {
    if (theme.value === 'auto') {
      applyTheme('auto')
    }
  }
  mediaQuery.addEventListener('change', handleSystemThemeChange)
  onUnmounted(() => {
    mediaQuery.removeEventListener('change', handleSystemThemeChange)
  })

  const savedToken = localStorage.getItem('charroom_token')
  const savedRefreshToken = localStorage.getItem('charroom_refreshToken') || ''
  const savedAccountId = localStorage.getItem('charroom_accountId')

  if (!savedToken || !savedAccountId) {
    loading.value = false
    return
  }

  store.setToken(savedToken)
  store.setRefreshToken(savedRefreshToken)
  store.setAccountId(savedAccountId)

  // 验证 token，失效则尝试刷新
  let activeToken = savedToken
  let validated = await api.validateToken()

  if (!validated && savedRefreshToken) {
    const refreshed = await api.refreshToken(savedRefreshToken)
    if (refreshed?.accessToken) {
      activeToken = refreshed.accessToken
      store.setToken(refreshed.accessToken)
      store.setRefreshToken(refreshed.refreshToken || '')
      persistTokens(refreshed.accessToken, refreshed.refreshToken)
      validated = await api.validateToken()
    }
  }

  if (validated) {
    await initUserSession(activeToken, store.state.refreshToken)
  } else {
    clearAuth()
    router.push('/')
  }

  loading.value = false

  // 页面关闭时发送登出消息
  window.addEventListener('beforeunload', () => {
    if (store.state.token) {
      chatSocket.sendWrapper({
        type: 'logout',
        logout: { userId: String(store.state.accountId) }
      }).catch(() => {})
    }
  })
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.loading-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: #f8f9fa;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #ff7a33;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.loading-wrap p {
  margin-top: 16px;
  color: #666;
  font-size: 14px;
}

.app-wrap{height:100dvh;display:flex;flex-direction:column}
.app-main{display:flex;flex:1;min-height:0}
.sidebar{width:260px;border-right:1px solid rgba(0,0,0,0.04);background:var(--bg)}
.chat{flex:1;display:flex;flex-direction:column;min-width:0}

/* 移动端适配 */
@media (max-width: 768px) {
  .app-main.mobile-view {
    display: block;
    position: relative;
    width: 100%;
    height: 100dvh;
  }

  /* 移动端下的sidebar占满全屏 */
  .app-main.mobile-view .sidebar {
    width: 100%;
    height: 100dvh;
    border-right: none;
  }

  /* 移动端聊天视图 */
  .app-main.mobile-view .chat-view {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: var(--bg);
    display: flex;
    flex-direction: column;
    z-index: 10;
  }

  /* 移动端顶部栏 */
  .mobile-header {
    display: flex;
    align-items: center;
    padding: 12px 16px;
    border-bottom: 1px solid rgba(0,0,0,0.04);
    background: var(--bg);
  }

  .back-btn {
    background: transparent;
    border: none;
    font-size: 18px;
    padding: 4px 8px;
    margin-right: 16px;
    cursor: pointer;
    border-radius: 4px;
  }

  .back-btn:hover {
    background: rgba(0,0,0,0.05);
  }

  .chat-title {
    font-weight: 600;
    font-size: 16px;
  }

  /* 移动端聊天窗口占满剩余空间 */
  .app-main.mobile-view .chat {
    flex: 1;
    height: calc(100% - 57px); /* 减去顶部栏高度 */
  }
}

.settings-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.settings-dialog {
  width: min(360px, calc(100% - 40px));
  background: var(--bg);
  border-radius: 16px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.18);
  overflow: hidden;
}

.settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
}

.settings-header h3 {
  margin: 0;
  font-size: 16px;
}

.close-settings {
  border: none;
  background: transparent;
  font-size: 20px;
  cursor: pointer;
}

.settings-body {
  padding: 20px;
}

.logout-btn {
  width: 100%;
  padding: 12px 16px;
  border: none;
  border-radius: 10px;
  background: #ff5f5f;
  color: white;
  font-weight: 600;
  cursor: pointer;
}

.logout-btn:hover {
  background: #e04747;
}

/* 传输层 UI */
.transport-info label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  color: var(--muted);
}

.transport-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  background: #d1fae5;
  color: #065f46;
}

/* 主题设置样式 */
.settings-item {
  margin-bottom: 20px;
}
.settings-item label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  color: var(--muted);
}
.theme-options {
  display: flex;
  gap: 8px;
}
.theme-options button {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--panel);
  color: var(--text-primary);
  cursor: pointer;
  transition: all 0.2s;
}
.theme-options button.active {
  border-color: var(--accent-2);
  background: var(--accent-2);
  color: white;
}
.theme-options button:hover {
  opacity: 0.9;
}
</style>
