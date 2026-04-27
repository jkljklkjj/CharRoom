<template>
  <div class="app-wrap">
    <LoginRegister v-if="!store.state.token" @logged="onLogged" />

    <div v-else class="app-main">
      <SidebarUsers class="sidebar" />
      <ChatWindow class="chat" />
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import LoginRegister from '../components/LoginRegister.vue'
import SidebarUsers from '../components/SidebarUsers.vue'
import ChatWindow from '../components/ChatWindow.vue'
import { useStore } from '../store'
import chatSocket from '../services/chatSocket'
import api from '../api'

const WS_URL = import.meta.env.VITE_WS_URL || (location.origin.replace(/^http/, 'ws') + '/ws')

const store = useStore()

async function onLogged(token) {
  store.setToken(token)
  // 登录后加载好友列表
  const friends = await api.getFriends()
  store.setUsers(friends)
  // 登录后立即建立 websocket 连接
  chatSocket.connect(WS_URL, token, {
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
    chatSocket.connect(WS_URL, store.state.token, {
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
</style>
