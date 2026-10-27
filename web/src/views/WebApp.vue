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
          v-model:tab="sidebarTab" v-model:query="sidebarQueries[sidebarTab]"
          class="sidebar"
          v-if="currentView === 'list'"
          @user-selected="onUserSelected"
          @open-settings="showSettings = true"
          @open-discovery="showSocial = true"
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
        <SidebarUsers v-model:tab="sidebarTab" v-model:query="sidebarQueries[sidebarTab]" class="sidebar" @open-settings="showSettings = true" @open-discovery="showSocial = true" />
        <ChatWindow class="chat" />
      </template>
    </div>

    <div v-if="showSettings" class="settings-overlay" @click.self="closeSettings">
      <div ref="settingsDialog" class="settings-dialog" role="dialog" aria-modal="true" :aria-label="$t('app.settings.title')" tabindex="-1">
        <div class="settings-header">
          <h3>{{ $t('app.settings.title') }}</h3>
          <button class="close-settings" :aria-label="$t('social.close')" @click="closeSettings">×</button>
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
          <div class="settings-divider"></div>
          <button class="settings-action-btn" @click="openProfileEdit">
            <span class="action-icon">👤</span>
            <span class="action-text">{{ $t('app.settings.editProfile') }}</span>
            <span class="action-arrow">›</span>
          </button>
          <button class="settings-action-btn" @click="showQuota = true">
            <span class="action-icon">📊</span>
            <span class="action-text">{{ $t('app.settings.tokenQuota') }}</span>
            <span class="action-arrow">›</span>
          </button>
          <div class="settings-divider"></div>
          <button class="settings-action-btn danger" @click="showLogoutConfirm = true">
            <span class="action-icon">🚪</span>
            <span class="action-text">{{ $t('app.settings.logout') }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 退出确认弹窗 -->
    <div v-if="showLogoutConfirm" class="settings-overlay" @click.self="showLogoutConfirm = false">
      <div ref="logoutDialog" class="settings-dialog confirm-dialog" role="dialog" aria-modal="true" :aria-label="$t('app.settings.logout')" tabindex="-1">
        <div class="confirm-text">{{ $t('app.settings.logoutConfirm') }}</div>
        <div class="confirm-actions">
          <button class="btn cancel" @click="showLogoutConfirm = false">{{ $t('app.cancel') }}</button>
          <button class="logout-btn" @click="doLogout">{{ $t('app.settings.logout') }}</button>
        </div>
      </div>
    </div>

  </div>
  <TokenQuotaDialog v-if="showQuota" @close="showQuota = false" />
  <SocialHub v-if="showSocial && store.state.loginValid" :initial-tab="socialInitialTab" @close="showSocial = false; socialInitialTab = 'people'" @chat="onSocialChat" />
</template>

<script setup lang="ts">
import type { MessageWrapper } from '../proto/messages'
import type { Id, TimeValue, User } from '../types/chat'
import type { OfflineMessage, Tokens } from '../types/api'
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import LoginRegister from '../components/LoginRegister.vue'
import SidebarUsers from '../components/SidebarUsers.vue'
import type { SidebarTab } from '../utils/contactDirectory'
import TokenQuotaDialog from '../components/TokenQuotaDialog.vue'
import ChatWindow from '../components/ChatWindow.vue'
import SocialHub from '../components/SocialHub.vue'
import type { SocialProfile } from '../types/social'
import { toast } from '../utils/toast'
import { useDialog } from '../composables/useDialog'
import { useStore } from '../store'
import chatSocket from '../services/chatSocket'
import { MSG_TYPE, AGENT_STREAM_TYPE } from '../proto/constants'
import siteConfig from '../siteConfig'
import api from '../api'
import { AGENT_USER_ID } from '../constants'
// 主题设置（统一由 utils/theme.ts 管理，App.vue 已在全局初始化）
import { theme, setTheme, initTheme } from '../utils/theme'

function normalizeTimestamp(raw: TimeValue | null | undefined) {
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
const sidebarTab = ref<SidebarTab>('messages')
const sidebarQueries = ref({ messages: '', contacts: '' })
const currentView = ref('list') // 'list' | 'chat'
const currentChatName = ref('')
const showSettings = ref(false)
const showSocial = ref(false)
const socialInitialTab = ref<'people' | 'me'>('people')

function onSocialChat(profile: SocialProfile) {
  if (profile.relation !== 'FRIEND') return
  const user = { id: profile.id, username: profile.username, name: profile.username, avatarUrl: profile.avatarUrl, signature: profile.signature }
  store.mergeUsers([user]); store.setSelectedChat(profile.id); showSocial.value = false; socialInitialTab.value = 'people'; onUserSelected(user)
}
const showQuota = ref(false)
const showLogoutConfirm = ref(false)
const settingsDialog = ref<HTMLElement | null>(null), logoutDialog = ref<HTMLElement | null>(null)
useDialog(settingsDialog, closeSettings)
useDialog(logoutDialog, () => { showLogoutConfirm.value = false })
const loading = ref(true) // 登录状态验证中

function openProfileEdit() {
  showSettings.value = false
  socialInitialTab.value = 'me'
  showSocial.value = true
}

// 主题设置（theme / setTheme / initTheme 来自 utils/theme.ts）

function switchLang(lang: string) {
  locale.value = lang
  localStorage.setItem('locale', lang)
  document.documentElement.lang = lang
}

function handleResize() {
  isMobile.value = window.innerWidth < 768
  if (!isMobile.value) {
    currentView.value = 'list'
  }
}

/** 处理 QUIC/WebTransport 收到的消息 */
async function handleIncomingMessage(msg: MessageWrapper) {
  try {
    if (msg.type === MSG_TYPE.CHAT && msg.chat) {
      const senderId = String(msg.chat.userId || 'unknown')

      // 检查发送者是否在好友列表中，不在则同步更新
      if (senderId !== 'you' && senderId !== String(store.state.accountId)) {
        const exists = store.state.users.some(u => String(u.id) === senderId)
        if (!exists) {
          // 尝试获取用户信息并添加到列表
          try {
            const userDetail = await api.getUserDetail(senderId)
            if (userDetail?.id) {
              store.addUser(userDetail)
            } else {
              // 如果获取失败，创建一个临时用户
              store.addUser({ id: Number(senderId), username: `用户${senderId}` })
            }
          } catch (e) {
            console.debug('获取新好友信息失败，使用临时信息:', e)
            store.addUser({ id: Number(senderId), username: `用户${senderId}` })
          }
        }
      }

      store.addMessage({
        user: senderId,
        text: msg.chat.content || '',
        time: normalizeTimestamp(msg.chat.timestamp),
        targetId: msg.chat.targetClientId,
        messageId: msg.chat.messageId || undefined,
        seqId: msg.chat.seqId || undefined
      })
      // 从消息 payload 中更新 seqId 游标（后端全链路携带 seqId）
      if (msg.chat.seqId && msg.chat.conversationId) {
        store.setConversationSeqId(msg.chat.conversationId, msg.chat.seqId)
      }
    } else if (msg.type === MSG_TYPE.GROUP_CHAT && msg.groupChat) {
      store.addGroupMessage({
        user: String(msg.groupChat.userId),
        text: msg.groupChat.content || '',
        time: normalizeTimestamp(msg.groupChat.timestamp),
        groupId: msg.groupChat.targetClientId,
        messageId: msg.groupChat.messageId || undefined,
        seqId: msg.groupChat.seqId || undefined
      })
      // 从消息 payload 中更新 seqId 游标
      if (msg.groupChat.seqId && msg.groupChat.conversationId) {
        store.setConversationSeqId(msg.groupChat.conversationId, msg.groupChat.seqId)
      }
    } else if (msg.type === MSG_TYPE.AGENT_CHAT_STREAM && msg.agentStream) {
      // Agent 流式消息（AgentStreamChunk 协议）
      const stream = msg.agentStream
      const requestId = String(stream.requestId || '')
      if (!requestId) return

      const streamType = stream.type
      // 0=TEXT, 1=TOOL_CALL, 2=TOOL_RESULT, 3=USAGE, 4=DONE, 5=ERROR
      if (streamType === AGENT_STREAM_TYPE.STREAM_TEXT && stream.text !== undefined) {
        // 文本片段：增量拼接
        store.upsertAgentStreamMessage(requestId, stream.text, false)
      } else if (streamType === AGENT_STREAM_TYPE.STREAM_TOOL_CALL && stream.toolCall) {
        // 工具调用：显示工具名称和参数
        const tc = stream.toolCall
        const toolInfo = `\n\n🔧 调用工具: ${tc.name}`
        store.upsertAgentStreamMessage(requestId, toolInfo, false)
      } else if (streamType === AGENT_STREAM_TYPE.STREAM_TOOL_RESULT && stream.toolResult) {
        // 工具结果：显示执行结果
        const tr = stream.toolResult
        const status = tr.success ? '✅' : '❌'
        const resultText = `\n${status} 结果: ${tr.result || '(空)'}`
        store.upsertAgentStreamMessage(requestId, resultText, false)
      } else if (streamType === AGENT_STREAM_TYPE.STREAM_USAGE && stream.usage) {
        // Token 用量：显示统计
        const u = stream.usage
        const usageText = `\n\n📊 Token: ${u.inputTokens} 输入 / ${u.outputTokens} 输出`
        store.upsertAgentStreamMessage(requestId, usageText, false)
      } else if (streamType === AGENT_STREAM_TYPE.STREAM_DONE) {
        // 流结束
        store.upsertAgentStreamMessage(requestId, '', true)
      } else if (streamType === AGENT_STREAM_TYPE.STREAM_ERROR && stream.error) {
        // 错误
        const errorMsg = stream.error.message || 'Agent 处理失败'
        store.upsertAgentStreamMessage(requestId, `\n\n❌ ${errorMsg}`, true)
      }
    } else if (msg.type === MSG_TYPE.SYNC_HINT) {
      // sync_hint：服务端推送的增量同步提示
      const hint = msg.ack
      if (!hint) return
      const conversationId = hint.clientId
      const seqId = parseInt(hint.message || '', 10)
      if (conversationId && seqId > 0) {
        // 服务端提示不是客户端已应用游标。
        // 触发增量同步
        syncConversation(conversationId, seqId)
      }
    } else if (msg.response?.clientId !== undefined && msg.response.online !== undefined) {
      store.updateUserOnlineStatus(parseInt(msg.response.clientId), msg.response.online)
    }
  } catch (e) {
    console.error('处理消息失败', e)
  }
}

/** 登录后拉取所有会话的离线消息 */
/**
 * 增量同步单个会话（sync_hint 触发）。
 * @param {string} conversationId - 会话 ID
 * @param {number} serverSeqId - 服务端最新 seqId
 */
async function syncConversation(conversationId: string, serverSeqId: Id) {
  const lastSeqId = store.getConversationSeqId(conversationId)
  if (BigInt(serverSeqId) <= BigInt(lastSeqId)) return

  try {
    const result = await api.syncMessages(conversationId, lastSeqId, 50)
    if (!result || !result.messages || result.messages.length === 0) return

    const accountId = store.state.accountId
    for (const msg of result.messages) {
      const isMe = String(msg.senderId) === String(accountId)
      // 后端 Message.id 即客户端 messageId（fromProto: id = chat.getMessageId()）
      const messageId = msg.id || msg.messageId || undefined
      if (conversationId.startsWith('group:')) {
        store.addGroupMessage({
          user: String(msg.senderId),
          text: msg.message,
          time: msg.timestamp,
          groupId: conversationId.replace('group:', ''),
          messageId,
          seqId: msg.seqId
        })
      } else {
        store.addMessage({
          user: isMe ? 'you' : String(msg.senderId),
          text: msg.message,
          time: msg.timestamp,
          targetId: isMe ? String(msg.receiverId) : String(msg.senderId),
          messageId,
          seqId: msg.seqId
        })
      }
    }

    if (result.nextSeqId) {
      store.setConversationSeqId(conversationId, result.nextSeqId)
    }
    console.debug(`📡 增量同步 ${conversationId}: ${result.messages.length} 条消息`)
  } catch (e) {
    console.warn('增量同步失败:', conversationId, e)
  }
}

async function syncAllConversations() {
  const accountId = String(store.state.accountId)
  if (!accountId) return
  let request: import('../types/api').RecentHistoryRequest = { limit: 100 }
  try {
    while (String(store.state.accountId) === accountId) {
      const page = await api.getRecentMessages(request)
      if (String(store.state.accountId) !== accountId) return
      const messages = page.messages.map(msg => {
        const isMe = String(msg.senderId) === accountId
        const common = { user: isMe ? 'you' : String(msg.senderId), text: msg.message,
          time: msg.timestamp, messageId: msg.id || msg.messageId || undefined, seqId: msg.seqId,
          messageType: msg.messageType === 1 ? 'image' : msg.messageType === 2 ? 'file' : 'text',
          fileUrl: msg.fileUrl, fileName: msg.fileName, fileSize: msg.fileSize,
          replyTo: msg.replyToMessageId ? { messageId: msg.replyToMessageId,
            content: msg.replyToContent || '', sender: msg.replyToSender || '' } : undefined }
        return msg.conversationId?.startsWith('group:')
          ? { ...common, user: String(msg.senderId), groupId: msg.conversationId.slice(6) }
          : { ...common, targetId: isMe ? String(msg.receiverId) : String(msg.senderId) }
      })
      await store.restoreHistoryPage(accountId, messages)
      if (String(store.state.accountId) !== accountId) return
      await store.rebuildConversationStates(true)
      if (!page.hasMore) return
      if (!page.nextId || page.nextTimestamp == null ||
          (page.nextId === request.afterId && page.nextTimestamp === request.afterTimestamp)) {
        throw new Error('Recent history cursor did not advance')
      }
      request = { windowEnd: page.windowEnd, afterTimestamp: page.nextTimestamp, afterId: page.nextId, limit: 100 }
    }
  } catch (error) {
    console.warn('Recent history synchronization failed:', error)
  }
}

/** 建立 WebTransport 连接 */
function connectChat(token: string, accountId: Id) {
  const transportConfig = siteConfig.TRANSPORT || {}
  const host = 'quic.chatlite.xin'
  const port = transportConfig.port || 8080
  chatSocket.connect(host, port, token, String(accountId), {
    onmessage: handleIncomingMessage,
    onAuthFailed: () => logout(),
    onFriendAccepted: async (accepterId) => {
      // 好友请求被接受，增量添加新好友
      try {
        const friend = await api.getUserDetail(accepterId)
        if (friend) {
          store.addUser(friend)
          toast.success(t('chat.friendAccepted') || '好友已添加')

          // 自动发送欢迎消息给新好友
          try {
            const welcomeText = '你好！我们已经是好友了，快来聊天吧 👋'
            await chatSocket.sendWrapper({
              type: MSG_TYPE.CHAT,
              chat: {
                targetClientId: Number(accepterId),
                content: welcomeText,
                timestamp: Date.now(),
                messageId: 'local_' + Date.now()
              }
            })
          } catch (e) {
            console.debug('发送欢迎消息失败:', e)
          }
        }
      } catch (e) {
        console.warn('获取新好友信息失败:', e)
      }
    }
  })
}

/** 清除本地登录状态 */
function clearAuth() {
  sidebarTab.value = 'messages'
  sidebarQueries.value = { messages: '', contacts: '' }
  currentView.value = 'list'
  sessionStorage.removeItem('charroom_token')
  localStorage.removeItem('charroom_refreshToken')
  localStorage.removeItem('charroom_accountId')
  store.setToken('')
  store.setRefreshToken('')
  store.setAccountId('')
  store.setLoginValid(false)
}

async function logout() {
  try {
    await chatSocket.sendWrapper({
      type: MSG_TYPE.LOGOUT,
      logout: { userId: Number(store.state.accountId) }
    })
  } catch (_) {}
  chatSocket.close()
  store.clearAll()
  clearAuth()
  store.setSelectedChat(null)
  showSettings.value = false
  showLogoutConfirm.value = false
  router.push('/')
}

/** 退出确认后的实际登出 */
function doLogout() {
  logout()
}

/** 保存登录凭证到本地 — access token 用 sessionStorage（XSS 安全），refresh token 用 localStorage */
function persistTokens(accessToken: string, refreshToken: string) {
  try {
    sessionStorage.setItem('charroom_token', accessToken)
    localStorage.setItem('charroom_refreshToken', refreshToken || '')
  } catch (_e) { /* ignore */ }
}

/** 获取当前用户信息并标记登录有效 */
async function initUserSession(accessToken: string, refreshToken: string) {
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

  // 同步离线期间的好友接受通知
  try {
    const notifications = await api.getFriendNotifications()
    if (notifications && notifications.length > 0) {
      for (const n of notifications) {
        // 获取新好友信息并添加到列表
        const friend = await api.getUserDetail(n.accepterId)
        if (friend) {
          store.addUser(friend)
        }
      }
      // 标记通知已读
      await api.markFriendNotificationsRead()
    }
  } catch (e) {
    console.warn('同步好友通知失败:', e)
  }

  // 批量拉取好友在线状态
  if (friends && friends.length > 0) {
    const friendIds = friends.map(f => f.id).filter(id => id && id !== AGENT_USER_ID)
    if (friendIds.length > 0) {
      const statusMap = await api.getOnlineStatus(friendIds)
      for (const [userId, online] of Object.entries(statusMap)) {
        store.updateUserOnlineStatus(parseInt(userId), online)
      }
    }
  }

  // 获取用户的群聊列表
  const myGroups = await api.getMyGroups()
  store.setGroups(myGroups || [])

  connectChat(accessToken, userRes.id)
  syncAllConversations()

  // 启动好友请求轮询
  startFriendRequestPolling()

  return true
}

/** 登录成功回调 */
async function onLogged(tokens: Tokens) {
  const accessToken = tokens?.accessToken || ''
  const refreshToken = tokens?.refreshToken || ''
  if (!accessToken) return
  await initUserSession(accessToken, refreshToken)
}

// 选中用户，切换到聊天视图
function onUserSelected(user: User) {
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
onMounted(() => {
  window.addEventListener('resize', handleResize)

  // 应用视图锁定文档滚动：保留顶部安全区留白，但页面整体不参与拖拽/橡皮筋
  document.body.classList.add('app-viewport')

  // 主题由 App.vue 全局初始化；这里确保本页也已应用（幂等）
  initTheme()

  // 异步恢复登录状态（不阻塞 onMounted）
  restoreSession()
})

async function restoreSession() {
  const savedToken = sessionStorage.getItem('charroom_token')
  const savedRefreshToken = localStorage.getItem('charroom_refreshToken') || ''
  const savedAccountId = localStorage.getItem('charroom_accountId')

  // sessionStorage 在浏览器关闭后清空，但 refreshToken 在 localStorage 中持久保存。
  // 即使 accessToken 丢失，只要有 refreshToken 就尝试恢复登录。
  if (!savedAccountId) {
    loading.value = false
    return
  }

  // 如果 accessToken 缺失但有 refreshToken，先用它刷新一个新 token
  let activeToken = savedToken
  if (!activeToken && savedRefreshToken) {
    try {
      const refreshed = await api.refreshToken(savedRefreshToken)
      if (refreshed?.accessToken) {
        activeToken = refreshed.accessToken
        store.setToken(refreshed.accessToken)
        store.setRefreshToken(refreshed.refreshToken || '')
        persistTokens(refreshed.accessToken, refreshed.refreshToken)
      }
    } catch (e) {
      console.debug('刷新 token 失败:', e)
    }
  }

  if (!activeToken) {
    loading.value = false
    return
  }

  store.setToken(activeToken)
  store.setRefreshToken(savedRefreshToken)
  store.setAccountId(savedAccountId)

  // 验证 token，失效则尝试刷新
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
}

// 页面关闭时发送登出消息
const handleBeforeUnload = () => {
  if (store.state.token) {
    chatSocket.sendWrapper({
      type: MSG_TYPE.LOGOUT,
      logout: { userId: Number(store.state.accountId) }
    }).catch(e => console.debug('登出消息发送失败:', e))
  }
}

// 好友请求轮询定时器
let friendRequestPollTimer: ReturnType<typeof setInterval> | null = null
const FRIEND_REQUEST_POLL_INTERVAL = 2 * 60 * 1000 // 2分钟

/** 启动好友请求轮询 */
function startFriendRequestPolling() {
  stopFriendRequestPolling()
  friendRequestPollTimer = setInterval(async () => {
    if (!store.state.token || !store.state.loginValid) return
    try {
      const requests = await api.getFriendRequests()
      // 通过自定义事件通知 SidebarUsers 更新
      window.dispatchEvent(new CustomEvent('friend-requests-updated', { detail: requests }))
    } catch (e) {
      console.debug('轮询好友请求失败:', e)
    }
  }, FRIEND_REQUEST_POLL_INTERVAL)
}

/** 停止好友请求轮询 */
function stopFriendRequestPolling() {
  if (friendRequestPollTimer) {
    clearInterval(friendRequestPollTimer)
    friendRequestPollTimer = null
  }
}

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('beforeunload', handleBeforeUnload)
  stopFriendRequestPolling()
  document.body.classList.remove('app-viewport')
})
</script>

<style scoped>
.loading-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
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

/* 高度跟随 body 内容盒（已扣掉顶部/底部安全区留白），避免多出可拖拽的溢出区域 */
.app-wrap{height:100%;display:flex;flex-direction:column}
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
    position: fixed;
    /* 固定在视口上时同样让开顶部安全区，保持与侧边栏一致的留白 */
    top: env(safe-area-inset-top, 0);
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
    min-height: 0; /* 允许 flex 收缩 */
  }
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

.settings-divider {
  height: 1px;
  background: var(--surface-border, rgba(0, 0, 0, 0.04));
  margin: 8px 0;
}

.settings-action-btn {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--text-primary, #333);
  font-size: 14px;
  cursor: pointer;
  transition: background 0.15s;
}

.settings-action-btn:hover {
  background: var(--surface-hover, rgba(0, 0, 0, 0.04));
}

.settings-action-btn .action-icon {
  font-size: 18px;
  width: 24px;
  text-align: center;
}

.settings-action-btn .action-text {
  flex: 1;
  text-align: left;
}

.settings-action-btn .action-arrow {
  font-size: 18px;
  color: var(--muted, #999);
}

.settings-action-btn.danger {
  color: #ff5f5f;
}

.settings-action-btn.danger:hover {
  background: rgba(255, 95, 95, 0.08);
}











.profile-dialog { width: min(360px, calc(100% - 40px)); }


.profile-error {
  color: #ff4757;
  font-size: 13px;
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




</style>
