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
          <ChatWindow class="chat" @retry-history="syncAllConversations" />
        </div>
      </template>

      <!-- 桌面端视图 -->
      <template v-else>
        <SidebarUsers v-model:tab="sidebarTab" v-model:query="sidebarQueries[sidebarTab]" class="sidebar" @open-settings="showSettings = true" @open-discovery="showSocial = true" />
        <ChatWindow class="chat" @retry-history="syncAllConversations" />
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
import type { Tokens } from '../types/api'
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
import { AgentStreamReducer } from '../services/agentStreamReducer'
import { ConversationSync } from '../services/conversationSync'
import { refreshContacts, invalidateContacts } from '../store/contactDirectory'
import { bootstrapSession } from '../services/sessionBootstrap'
import { sessionEpoch } from '../services/sessionEpoch'
import { historyMessages } from '../utils/historyMessages'
import {ConversationId} from '../utils/conversationId'
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
  store.mergeUsers([user]); store.setSelectedChat(ConversationId.privateChat(profile.id).key); showSocial.value = false; socialInitialTab.value = 'people'; onUserSelected(user)
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

const agentStreams = new AgentStreamReducer()
const pendingAgentRenders = new Map<string, string>()
let agentRenderFrame = 0
function clearAgentStreams() {
  cancelAnimationFrame(agentRenderFrame); agentRenderFrame = 0
  pendingAgentRenders.clear(); agentStreams.clear()
}

/** 处理 QUIC/WebTransport 收到的消息 */
async function handleIncomingMessage(msg: MessageWrapper) {
  const session = sessionEpoch.capture()
  try {
    if (msg.type === MSG_TYPE.CHAT && msg.chat) {
      const rawSender = String(msg.chat.userId || 'unknown')
      const senderId = rawSender === String(store.state.accountId) ? 'you' : rawSender

      // 检查发送者是否在好友列表中，不在则同步更新
      if (senderId !== 'you' && senderId !== String(store.state.accountId)) {
        const exists = store.state.users.some(u => String(u.id) === senderId)
        if (!exists) {
          // 尝试获取用户信息并添加到列表
          try {
            const userDetail = await api.getUserDetail(senderId)
            if (!session.isCurrent()) return
            if (userDetail?.id) {
              store.addUser(userDetail)
            } else {
              // 如果获取失败，创建一个临时用户
              store.addUser({ id: Number(senderId), username: `用户${senderId}` })
            }
          } catch (e) {
            if (!session.isCurrent()) return
            console.debug('获取新好友信息失败，使用临时信息:', e)
            store.addUser({ id: Number(senderId), username: `用户${senderId}` })
          }
        }
      }

      if (!session.isCurrent()) return
      await store.addMessage({
        user: senderId,
        text: msg.chat.content || '',
        time: normalizeTimestamp(msg.chat.timestamp),
        targetId: msg.chat.targetClientId,
        messageId: msg.chat.messageId || undefined,
        seqId: msg.chat.seqId || undefined
      })
      if (!session.isCurrent()) return
      // Live delivery requests authoritative history; it does not prove a complete prefix.
      if (msg.chat.seqId && msg.chat.conversationId) {
        void syncConversation(msg.chat.conversationId, msg.chat.seqId)
      }
    } else if (msg.type === MSG_TYPE.GROUP_CHAT && msg.groupChat) {
      await store.addGroupMessage({
        user: String(msg.groupChat.userId),
        text: msg.groupChat.content || '',
        time: normalizeTimestamp(msg.groupChat.timestamp),
        groupId: msg.groupChat.targetClientId,
        messageId: msg.groupChat.messageId || undefined,
        seqId: msg.groupChat.seqId || undefined
      })
      if (!session.isCurrent()) return
      // The group history cursor is committed only after a complete response is stored.
      if (msg.groupChat.seqId && msg.groupChat.conversationId) {
        void syncConversation(msg.groupChat.conversationId, msg.groupChat.seqId)
      }
    } else if (msg.type === MSG_TYPE.AGENT_CHAT_STREAM && msg.agentStream) {
      const stream = msg.agentStream, id = String(stream.requestId || '')
      const state = agentStreams.accept(stream)
      if (state) {
        if (state.done) {
          pendingAgentRenders.delete(id)
          await store.upsertAgentStreamMessage(id, state.text, true)
          if (session.isCurrent()) await chatSocket.sendAck(id)
        } else {
          pendingAgentRenders.set(id, state.text)
          if (!agentRenderFrame) agentRenderFrame = requestAnimationFrame(() => {
            agentRenderFrame = 0
            if (!session.isCurrent()) return
            for (const [key, text] of pendingAgentRenders) void store.upsertAgentStreamMessage(key, text).catch(error => console.warn('Agent render failed:', error))
            pendingAgentRenders.clear()
          })
        }
      }
    } else if (msg.type === MSG_TYPE.SYNC_HINT) {
      // sync_hint：服务端推送的增量同步提示
      const hint = msg.ack
      if (!hint) return
      const conversationId = hint.clientId
      const seqId = hint.message || ''
      if (conversationId && /^\d+$/.test(seqId) && BigInt(seqId) > 0n) {
        // 服务端提示不是客户端已应用游标。
        // 触发增量同步
        syncConversation(conversationId, seqId)
      }
    } else if (msg.response?.clientId !== undefined && msg.response.online !== undefined) {
      store.updateUserOnlineStatus(parseInt(msg.response.clientId), msg.response.online)
    }
  } catch (e) {
    console.error('处理消息失败', e)
    throw e
  }
}

/** 登录后拉取所有会话的离线消息 */
/**
 * 增量同步单个会话（sync_hint 触发）。
 * @param {string} conversationId - 会话 ID
 * @param serverSeqId - 服务端最新 seqId（十进制字符串）
 */
const incrementalSync = new ConversationSync({
  capture: () => {
    const owner = String(store.state.accountId), session = sessionEpoch.capture()
    return { key: `${owner}:${session.version}`, isCurrent: () => session.isCurrent() && String(store.state.accountId) === owner }
  },
  cursor: conversation => store.getConversationSeqId(conversation),
  fetch: (conversation, after) => api.syncMessages(conversation, after, 50),
  apply: async (conversation, rows) => {
    const owner = String(store.state.accountId)
    await store.restoreHistoryPage(owner, historyMessages(rows, owner),{conversationId:conversation,seqId:rows.at(-1)!.seqId!})
  },
  commit: (conversation, cursor) => store.setConversationSeqId(conversation, cursor)
})

async function syncConversation(conversationId: string, serverSeqId: Id) {
  const done=store.beginHistorySync()
  let failed=false
  try { await incrementalSync.run(conversationId, serverSeqId) }
  catch (error) { failed=true;console.warn('Incremental synchronization failed:', conversationId, error) }
  finally {done(failed)}
}

async function syncAllConversations() {
  const accountId = String(store.state.accountId)
  if (!accountId) return
  const session = sessionEpoch.capture(),done=store.beginHistorySync()
  let failed=false
  let request: import('../types/api').RecentHistoryRequest = { limit: 100 }
  try {
    while (session.isCurrent() && String(store.state.accountId) === accountId) {
      const page = await api.getRecentMessages(request)
      if (!session.isCurrent() || String(store.state.accountId) !== accountId) return
      const messages = historyMessages(page.messages, accountId)
      await store.restoreHistoryPage(accountId, messages)
      if (!session.isCurrent() || String(store.state.accountId) !== accountId) return
      const receivedIds = page.messages.filter(message => !message.conversationId?.startsWith('group:') &&
        String(message.receiverId) === accountId).map(message => String(message.id))
      await api.confirmOfflineMessages(receivedIds)
      if (!session.isCurrent() || String(store.state.accountId) !== accountId) return
      await store.rebuildConversationStates(true)
      if (!page.hasMore) return
      if (!page.nextId || page.nextTimestamp == null ||
          (page.nextId === request.afterId && page.nextTimestamp === request.afterTimestamp)) {
        throw new Error('Recent history cursor did not advance')
      }
      request = { windowEnd: page.windowEnd, afterTimestamp: page.nextTimestamp, afterId: page.nextId, limit: 100 }
    }
  } catch (error) {
    failed=true
    console.warn('Recent history synchronization failed:', error)
  } finally {done(failed)}
}

/** 建立 WebTransport 连接 */
function connectChat(token: string, accountId: Id) {
  const session = sessionEpoch.capture()
  const isCurrent = () => session.isCurrent() && String(store.state.accountId) === String(accountId)
  const transportConfig = siteConfig.TRANSPORT || {}
  const host = 'quic.chatlite.xin'
  const port = transportConfig.port || 8080
  return chatSocket.connect(host, port, token, String(accountId), {
    onAuthenticated:()=>{if(isCurrent())void refreshContacts()},
    onmessage: async msg => { if (isCurrent()) await handleIncomingMessage(msg) },
    onAuthFailed: () => { if (isCurrent()) void logout() },
    onFriendAccepted: async (accepterId) => {
      if (!isCurrent()) return
      // 好友请求被接受，增量添加新好友
      try {
        const friend = await api.getUserDetail(accepterId)
        if (!isCurrent()) return
        if (friend) {
          store.addUser(friend)
          invalidateContacts()
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
                messageId: 'local_' + globalThis.crypto.randomUUID()
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
  clearAgentStreams()
  stopFriendRequestPolling()
  chatSocket.close()
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
  sessionEpoch.renew()
  const session = sessionEpoch.capture()
  try {
    await chatSocket.sendWrapper({
      type: MSG_TYPE.LOGOUT,
      logout: { userId: Number(store.state.accountId) }
    })
  } catch (_) {}
  if (!session.isCurrent()) return
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
  clearAgentStreams()
  sessionEpoch.renew()
  chatSocket.close()
  stopFriendRequestPolling()
  const attempt = sessionEpoch.capture()
  store.setToken(accessToken)
  store.setRefreshToken(refreshToken || '')
  const userRes = await api.getCurrentUser()
  if (!attempt.isCurrent()) return false
  if (!userRes?.id) { clearAuth(); return false }

  store.setAccountId(userRes.id)
  const owner = String(userRes.id), session = sessionEpoch.capture()
  const isCurrent = () => session.isCurrent() && String(store.state.accountId) === owner
  store.setLoginValid(true)
  persistTokens(store.state.token, store.state.refreshToken)
  localStorage.setItem('charroom_accountId', owner)

  // Each task owns its post-await checks. Contacts/groups must not delay history or transport.
  void bootstrapSession(() => connectChat(store.state.token, userRes.id), {
    history: async ()=>{await store.restoreAppliedCursors();if(isCurrent())await syncAllConversations()},
    contacts: async () => {
      await refreshContacts()
      if(!isCurrent())return
      const ids=store.state.users.map(friend=>friend.id).filter(id=>id&&id!==AGENT_USER_ID)
      if(!ids.length)return
      const statuses=await api.getOnlineStatus(ids)
      if(!isCurrent())return
      for(const [id,online] of Object.entries(statuses))store.updateUserOnlineStatus(id,online)
    },
    notifications: async () => {
      const notifications = await api.getFriendNotifications()
      if (!isCurrent() || !notifications?.length) return
      for (const notification of notifications) {
        const friend = await api.getUserDetail(notification.accepterId)
        if (!isCurrent()) return
        if (friend) store.addUser(friend)
      }
      if (isCurrent()) { invalidateContacts(); await api.markFriendNotificationsRead() }
    }
  }, isCurrent, (name, error) => console.warn('Session initialization task failed:', name, error))
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
function revalidateContacts() { if(store.state.loginValid)void refreshContacts() }
function onBusinessResume(){revalidateContacts();void refreshFriendRequests?.()}
function onDirectoryVisibility() { if(document.visibilityState==='visible')onBusinessResume() }
let directoryTimer: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  window.addEventListener('contacts-changed',invalidateContacts)
  window.addEventListener('online',onBusinessResume)
  window.addEventListener('focus',onBusinessResume)
  document.addEventListener('visibilitychange',onDirectoryVisibility)
  directoryTimer=setInterval(()=>{if(document.visibilityState==='visible')revalidateContacts()},60000)
  window.addEventListener('resize', handleResize)

  // 应用视图锁定文档滚动：保留顶部安全区留白，但页面整体不参与拖拽/橡皮筋
  document.body.classList.add('app-viewport')

  // 主题由 App.vue 全局初始化；这里确保本页也已应用（幂等）
  initTheme()

  // 异步恢复登录状态（不阻塞 onMounted）
  restoreSession()
})

async function restoreSession() {
  const attempt = sessionEpoch.capture()
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
      if (!attempt.isCurrent()) return
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
  if (!attempt.isCurrent()) return
  store.setAccountId(savedAccountId)
  const validation = sessionEpoch.capture()

  // 验证 token，失效则尝试刷新
  let validated = await api.validateToken()
  if (!validation.isCurrent()) return

  if (!validated && savedRefreshToken) {
    const refreshed = await api.refreshToken(savedRefreshToken)
    if (!validation.isCurrent()) return
    if (refreshed?.accessToken) {
      activeToken = refreshed.accessToken
      store.setToken(refreshed.accessToken)
      store.setRefreshToken(refreshed.refreshToken || '')
      persistTokens(refreshed.accessToken, refreshed.refreshToken)
      validated = await api.validateToken()
      if (!validation.isCurrent()) return
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
let friendRequestPollBusy=false
let refreshFriendRequests:(()=>Promise<void>)|undefined

/** 启动好友请求轮询 */
function startFriendRequestPolling() {
  stopFriendRequestPolling()
  const session = sessionEpoch.capture()
  refreshFriendRequests = async () => {
    if (!store.state.token || !store.state.loginValid || document.visibilityState==='hidden' || !navigator.onLine || friendRequestPollBusy) return
    friendRequestPollBusy=true
    try {
      const requests = await api.getFriendRequests()
      if (!session.isCurrent()) return
      // 通过自定义事件通知 SidebarUsers 更新
      window.dispatchEvent(new CustomEvent('friend-requests-updated', { detail: requests }))
    } catch (e) {
      console.debug('轮询好友请求失败:', e)
    } finally {friendRequestPollBusy=false}
  }
  friendRequestPollTimer=setInterval(refreshFriendRequests,FRIEND_REQUEST_POLL_INTERVAL)
}

/** 停止好友请求轮询 */
function stopFriendRequestPolling() {
  refreshFriendRequests=undefined
  if (friendRequestPollTimer) {
    clearInterval(friendRequestPollTimer)
    friendRequestPollTimer = null
  }
}

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onUnmounted(() => {
  clearInterval(directoryTimer)
  window.removeEventListener('contacts-changed',invalidateContacts)
  window.removeEventListener('online',onBusinessResume)
  window.removeEventListener('focus',onBusinessResume)
  document.removeEventListener('visibilitychange',onDirectoryVisibility)
  clearAgentStreams()
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('beforeunload', handleBeforeUnload)
  stopFriendRequestPolling()
  sessionEpoch.renew()
  chatSocket.close()
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
  background: var(--bg);
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid var(--action);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.loading-wrap p {
  margin-top: 16px;
  color: var(--muted);
  font-size: 14px;
}

/* 高度跟随 body 内容盒（已扣掉顶部/底部安全区留白），避免多出可拖拽的溢出区域 */
.app-wrap{height:100%;display:flex;flex-direction:column}
.app-main{display:flex;flex:1;min-height:0}
.sidebar{width:300px;flex-shrink:0;border-right:1px solid var(--surface-border);background:var(--bg)}
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
    border-bottom: 1px solid var(--surface-border);
    background: var(--bg);
  }

  .back-btn {
    background: transparent;
    color:var(--text-primary);
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
