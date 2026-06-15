<template>
  <div class="app-wrap">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-wrap">
      <div class="loading-spinner"></div>
      <p>正在验证登录状态...</p>
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
            <button class="back-btn" @click="backToList">← 返回</button>
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
          <h3>设置</h3>
          <button class="close-settings" @click="closeSettings">×</button>
        </div>
        <div class="settings-body">
          <div class="settings-item">
            <div class="transport-info">
              <label>传输协议</label>
              <div class="transport-badge" :class="currentTransport">
                {{ currentTransport === 'wt' ? 'WebTransport (QUIC)' : 'WebSocket (WSS)' }}
              </div>
              <button class="transport-toggle" @click="toggleTransport">
                切换为 {{ currentTransport === 'wt' ? 'WebSocket' : 'WebTransport' }}
              </button>
              <div v-if="wtSupported" class="wt-supported">✅ 浏览器支持 WebTransport</div>
              <div v-else class="wt-unsupported">⚠️ 浏览器不支持 WebTransport</div>
            </div>
          </div>
          <div class="settings-item">
            <label>主题设置</label>
            <div class="theme-options">
              <button :class="{ active: theme === 'auto' }" @click="setTheme('auto')">自动</button>
              <button :class="{ active: theme === 'light' }" @click="setTheme('light')">浅色</button>
              <button :class="{ active: theme === 'dark' }" @click="setTheme('dark')">深色</button>
            </div>
          </div>
          <button class="logout-btn" @click="logout">退出登录</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import LoginRegister from '../components/LoginRegister.vue'
import SidebarUsers from '../components/SidebarUsers.vue'
import ChatWindow from '../components/ChatWindow.vue'
import { useStore } from '../store'
import chatSocket, { setTransportType, getTransportType } from '../services/chatSocket'
import { configureTransport, isWebTransportSupported } from '../services/transport/TransportFactory'
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

const WS_URL = import.meta.env.VITE_WS_URL || (location.origin.replace(/^http/, 'ws') + '/ws')

const store = useStore()

// 移动端适配
const isMobile = ref(window.innerWidth < 768)
const currentView = ref('list') // 'list' | 'chat'
const currentChatName = ref('')
const showSettings = ref(false)
const loading = ref(true) // 登录状态验证中

// 传输层状态
const currentTransport = ref('ws')
const wtSupported = ref(false)

// 主题设置
const theme = ref(localStorage.getItem('theme') || 'auto') // auto, light, dark

function setTheme(newTheme) {
  theme.value = newTheme
  localStorage.setItem('theme', newTheme)
  applyTheme(newTheme)
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
    } else if (msg.type === 'groupChat' && msg.groupChat) {
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

/** 建立 WebSocket 连接 */
function connectChat(token, accountId) {
  chatSocket.connect(WS_URL, token, String(accountId), {
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

  const friends = await api.getFriends()
  store.setUsers(friends || [])

  connectChat(accessToken, userRes.id)
  return true
}

/** 登录成功回调 */
async function onLogged(tokens) {
  const accessToken = tokens?.accessToken || ''
  const refreshToken = tokens?.refreshToken || ''
  if (!accessToken) return
  await initUserSession(accessToken, refreshToken)
}

// 切换传输协议
function toggleTransport() {
  const newType = currentTransport.value === 'wt' ? 'ws' : 'wt'
  setTransportType(newType)
  currentTransport.value = newType
  chatSocket.close()
  if (store.state.token && store.state.accountId) {
    connectChat(store.state.token, store.state.accountId)
  }
}

// 选中用户，切换到聊天视图
function onUserSelected(user) {
  if (isMobile.value) {
    currentChatName.value = user.name || user.account || user.username || `用户 ${user.id}`
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
  // 初始化传输配置
  wtSupported.value = isWebTransportSupported()
  const transportConfig = siteConfig.TRANSPORT || {}
  configureTransport({
    preferWebTransport: transportConfig.preferWebTransport !== false,
  })
  setTransportType(wtSupported.value && transportConfig.preferWebTransport !== false ? 'auto' : 'ws')
  currentTransport.value = wtSupported.value ? 'wt' : 'ws'
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

.app-wrap{height:100vh;display:flex;flex-direction:column}
.app-main{display:flex;flex:1;min-height:0}
.sidebar{width:260px;border-right:1px solid rgba(0,0,0,0.04);background:var(--bg)}
.chat{flex:1;display:flex;flex-direction:column;min-width:0}

/* 移动端适配 */
@media (max-width: 768px) {
  .app-main.mobile-view {
    display: block;
    position: relative;
    width: 100%;
    height: 100%;
  }

  /* 移动端下的sidebar占满全屏 */
  .app-main.mobile-view .sidebar {
    width: 100%;
    height: 100%;
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
  margin-bottom: 8px;
}

.transport-badge.wt {
  background: #d1fae5;
  color: #065f46;
}

.transport-badge.ws {
  background: #e0e7ff;
  color: #3730a3;
}

.transport-toggle {
  display: block;
  margin: 8px 0;
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px solid var(--surface-border);
  background: var(--panel);
  color: var(--text-primary);
  cursor: pointer;
  font-size: 13px;
}

.transport-toggle:hover {
  opacity: 0.8;
}

.wt-supported {
  font-size: 12px;
  color: #059669;
  margin-top: 4px;
}

.wt-unsupported {
  font-size: 12px;
  color: #d97706;
  margin-top: 4px;
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
