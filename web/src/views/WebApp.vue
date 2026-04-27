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
        <SidebarUsers class="sidebar" />
        <ChatWindow class="chat" />
      </template>
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

const WS_URL = import.meta.env.VITE_WS_URL || (location.origin.replace(/^http/, 'ws') + '/ws')

const store = useStore()

// 移动端适配
const isMobile = ref(window.innerWidth < 768)
const currentView = ref('list') // 'list' | 'chat'
const currentChatName = ref('')

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

async function onLogged(token) {
  store.setToken(token)
  // 登录后加载好友列表
  const friends = await api.getFriends()
  store.setUsers(friends)
  // 登录后立即建立 websocket 连接
  chatSocket.connect(WS_URL, token, store.state.accountId, {
    onopen: () => console.log('ws open'),
    onmessage: (msg) => {
      // msg is decoded MessageWrapper object
      try {
        if (msg.type === 'chat' && msg.chat) {
          const m = { user: msg.chat.userId || 'unknown', text: msg.chat.content, time: msg.chat.timestamp || new Date().toISOString() }
          store.addMessage(m)
        } else if (msg.type === 'groupChat' && msg.groupChat) {
          const gm = { user: msg.groupChat.userId, text: msg.groupChat.content, time: new Date().toISOString(), groupId: msg.groupChat.targetClientId }
          store.addGroupMessage(gm)
        }
      } catch (e) {
        console.error('处理 incoming 消息失败', e)
      }
    },
    onclose: () => console.log('ws close'),
    onerror: (e) => console.error('ws error', e)
  })
}

onMounted(() => {
  // 页面挂载时可做进一步初始化（如尝试恢复 token）
  if (store.state.token) {
    // 自动恢复连接
    chatSocket.connect(WS_URL, store.state.token, store.state.accountId, {
      onopen: () => console.log('ws restored'),
      onmessage: (msg) => {
        if (msg.type === 'chat' && msg.chat) store.addMessage({ user: msg.chat.userId || 'unknown', text: msg.chat.content, time: msg.chat.timestamp || new Date().toISOString() })
        else if (msg.type === 'groupChat' && msg.groupChat) store.addGroupMessage({ user: msg.groupChat.userId, text: msg.groupChat.content, time: new Date().toISOString(), groupId: msg.groupChat.targetClientId })
      }
    })
  }
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
</style>
