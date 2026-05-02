<template>
  <div class="app-wrap">
    <LoginRegister v-if="!store.state.token" @logged="onLogged" />

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
          <button class="logout-btn" @click="logout">退出登录</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import LoginRegister from '../components/LoginRegister.vue'
import SidebarUsers from '../components/SidebarUsers.vue'
import ChatWindow from '../components/ChatWindow.vue'
import { useStore } from '../store'
import chatSocket from '../services/chatSocket'
import api from '../api'

function normalizeTimestamp(raw) {
  if (raw == null || raw === '') {
    return new Date().toISOString()
  }
  if (typeof raw === 'number') {
    return new Date(raw).toISOString()
  }
  if (typeof raw === 'string') {
    const numeric = Number(raw)
    if (!Number.isNaN(numeric) && String(numeric) === raw.trim()) {
      return new Date(numeric).toISOString()
    }
    const parsed = new Date(raw)
    if (!Number.isNaN(parsed.getTime())) {
      return parsed.toISOString()
    }
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

// 监听窗口大小变化
function handleResize() {
  isMobile.value = window.innerWidth < 768
  // 横屏时自动切回双栏布局
  if (!isMobile.value) {
    currentView.value = 'list'
  }
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})

// 选中用户，切换到聊天视图
function onUserSelected(user) {
  if (isMobile.value) {
    currentChatName.value = user.name || user.account || user.username || `用户 ${user.id}`
    currentView.value = 'chat'
  }
}

// 返回好友列表
function backToList() {
  currentView.value = 'list'
  store.setSelectedChat(null)
}

function closeSettings() {
  showSettings.value = false
}

function logout() {
  chatSocket.close()
  store.clearAll()
  store.setToken('')
  store.setAccountId('')
  store.setSelectedChat(null)
  localStorage.removeItem('charroom_token')
  localStorage.removeItem('charroom_accountId')
  showSettings.value = false
}

async function onLogged(token) {
  store.setToken(token)
  // 登录后获取当前用户信息并设置accountId
  const userRes = await api.getCurrentUser()
  if (userRes && userRes.id) {
    store.setAccountId(userRes.id)
    try {
      localStorage.setItem('charroom_token', token)
      localStorage.setItem('charroom_accountId', String(userRes.id))
    } catch (_e) {
      // ignore localStorage failures
    }
  }
  // 登录后加载好友列表
  const friends = await api.getFriends()
  store.setUsers(friends)
  // 登录后立即建立 websocket 连接
  chatSocket.connect(WS_URL, token, store.state.accountId, {
    onopen: () => console.log('ws open'),
    onmessage: (msg) => {
      // msg is decoded MessageWrapper object
      console.log('📩 收到原始消息:', msg)
      try {
        if (msg.type === 'chat' && msg.chat) {
          console.log('💬 收到聊天消息:', msg.chat)
          const m = {
            user: String(msg.chat.userId || 'unknown'), // 确保userId是字符串类型
            text: msg.chat.content,
            time: normalizeTimestamp(msg.chat.timestamp),
            targetId: msg.chat.targetClientId // 保留目标ID用于调试
          }
          console.log('📝 构造消息对象:', m)
          console.log('📍 当前选中的聊天ID:', store.state.selectedChatId)
          store.addMessage(m)
          console.log('💾 当前消息列表:', store.state.messages)

          // 测试过滤逻辑
          if (store.state.selectedChatId) {
            const selectedId = String(store.state.selectedChatId)
            const shouldShow = String(m.user) === selectedId || String(m.targetId) === selectedId
            console.log(`🔍 消息是否应该显示: ${shouldShow} (m.user=${m.user}, m.targetId=${m.targetId}, selectedId=${selectedId})`)
          }
        } else if (msg.type === 'groupChat' && msg.groupChat) {
          console.log('👥 收到群聊消息:', msg.groupChat)
          const gm = {
            user: String(msg.groupChat.userId), // 确保userId是字符串类型
            text: msg.groupChat.content,
            time: normalizeTimestamp(msg.groupChat.timestamp),
            groupId: msg.groupChat.targetClientId
          }
          store.addGroupMessage(gm)
        } else if (msg.type === 'heartbeat') {
          console.log('❤️ 收到 heartbeat 心跳消息，忽略同步展示')
        } else {
          console.log('ℹ️ 收到其他类型消息:', msg.type)
        }
      } catch (e) {
        console.error('❌ 处理 incoming 消息失败', e)
      }
    },
    onclose: () => console.log('ws close'),
    onerror: (e) => console.error('ws error', e),
    onAuthFailed: (reason) => {
      console.log('🔑 认证失败:', reason)
      // 清除本地凭证并跳回登录页
      logout()
    }
  })
}

onMounted(async () => {
  // 页面挂载时可做进一步初始化（如尝试恢复 token）
  const storedToken = store.state.token || localStorage.getItem('charroom_token')
  const storedAccountId = localStorage.getItem('charroom_accountId')
  if (storedToken) {
    store.setToken(storedToken)
    if (storedAccountId) {
      store.setAccountId(storedAccountId)
    }

    // 先验证token有效性，再获取用户信息
    const tokenValid = await api.validateToken()
    if (tokenValid) {
      const userRes = await api.getCurrentUser()
      if (userRes && userRes.id) {
        store.setAccountId(userRes.id)
        try {
          localStorage.setItem('charroom_token', storedToken)
          localStorage.setItem('charroom_accountId', String(userRes.id))
        } catch (_e) {
          // ignore localStorage failures
        }
        // 自动恢复连接
        chatSocket.connect(WS_URL, storedToken, store.state.accountId, {
          onopen: () => console.log('ws restored'),
          onmessage: (msg) => {
            console.log('📩 恢复连接收到原始消息:', msg)
            try {
              if (msg.type === 'chat' && msg.chat) {
                console.log('💬 恢复连接收到聊天消息:', msg.chat)
                const m = {
                  user: String(msg.chat.userId || 'unknown'),
                  text: msg.chat.content,
                  time: normalizeTimestamp(msg.chat.timestamp),
                  targetId: msg.chat.targetClientId
                }
                console.log('📝 恢复连接构造消息对象:', m)
                store.addMessage(m)
                console.log('💾 恢复连接后消息列表:', store.state.messages)
              } else if (msg.type === 'groupChat' && msg.groupChat) {
                console.log('👥 恢复连接收到群聊消息:', msg.groupChat)
                const gm = {
                  user: String(msg.groupChat.userId),
                  text: msg.groupChat.content,
                  time: normalizeTimestamp(msg.groupChat.timestamp),
                  groupId: msg.groupChat.targetClientId
                }
                store.addGroupMessage(gm)
              } else if (msg.type === 'heartbeat') {
                console.log('❤️ 恢复连接收到 heartbeat 心跳消息，忽略同步展示')
              } else {
                console.log('ℹ️ 恢复连接收到其他类型消息:', msg.type)
              }
            } catch (e) {
              console.error('❌ 恢复连接处理消息失败', e)
            }
          },
          onAuthFailed: (reason) => {
            console.log('🔑 恢复连接认证失败:', reason)
            // 清除本地凭证并跳回登录页
            logout()
          }
        })
      } else {
        localStorage.removeItem('charroom_token')
        localStorage.removeItem('charroom_accountId')
        store.setToken('')
        store.setAccountId('')
      }
    } else {
      localStorage.removeItem('charroom_token')
      localStorage.removeItem('charroom_accountId')
      store.setToken('')
      store.setAccountId('')
    }
  }

  // 页面关闭时主动发送登出消息
  window.addEventListener('beforeunload', () => {
    if (store.state.token) {
      // 发送登出消息
      chatSocket.sendWrapper({
        type: 'logout',
        logout: { userId: String(store.state.accountId) }
      }).catch(() => {})
    }
  })
})
</script>

<style scoped>
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
    background: #fff;
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
</style>
