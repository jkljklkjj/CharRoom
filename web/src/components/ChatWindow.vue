<template>
  <div class="chat-wrap">
    <header class="chat-top"># 会话</header>
    <div class="messages" ref="msgList">
      <div v-for="(m,i) in store.state.messages" :key="i" :class="['msg', {me: m.user==='you'}]">
        <div class="row">
          <div class="avatar-col">
            <img v-if="getAvatar(m.user)" :src="getAvatar(m.user)" class="msg-avatar" />
            <div v-else class="avatar-placeholder">{{ initials('' + m.user) }}</div>
          </div>
          <div class="bubble-col">
            <div class="meta">{{ m.user }} · {{ time(m.time) }}</div>
            <div class="text">{{ m.text }}</div>
          </div>
        </div>
      </div>
    </div>
    <form class="composer" @submit.prevent="send">
      <input v-model="text" placeholder="输入消息并回车发送..." />
      <button class="send">发送</button>
    </form>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useStore } from '../store'
import chatSocket from '../services/chatSocket'
import proto from '../proto'

const store = useStore()
const text = ref('')
const msgList = ref(null)

function time(t){ if(!t) return '' ; const d = new Date(t); return d.toLocaleTimeString() }
function getAvatar(userId){
  if(!userId) return null
  const id = typeof userId === 'string' && userId !== 'you' ? Number(userId) : userId
  const u = store.state.users.find(x => x.id === id)
  if (!u) return null
  if (!u.avatarUrl) return null
  const v = u.avatarKey
  return v ? (u.avatarUrl + (u.avatarUrl.includes('?') ? '&v=' : '?v=') + encodeURIComponent(v)) : u.avatarUrl
}

function initials(name) {
  if (!name) return 'U'
  return name.split(' ').map(s => s[0]).slice(0,2).join('').toUpperCase()
}

function send(){
  if(!text.value.trim()) return
  const m = { user: 'you', text: text.value, time: new Date().toISOString() }
  store.addMessage(m)
  // 发送给选中的联系人
  const to = store.state.selectedChatId || ''
  const wrapper = {
    type: 'chat',
    chat: { targetClientId: to, content: m.text, userId: store.state.accountId || 'web', timestamp: m.time }
  }
  chatSocket.sendWrapper(wrapper).catch(()=>{})
  text.value = ''
  // 简单本地发送 - 后续可用 chatSocket.sendWrapper 发送 protobuf
  setTimeout(()=>{ if (msgList.value) msgList.value.scrollTop = msgList.value.scrollHeight },50)
}

onMounted(()=>{
  // 可在此连接 websocket 并处理 incoming messages
})
</script>

<style scoped>
.chat-wrap{display:flex;flex-direction:column;height:100%}
.chat-top{padding:12px 16px;border-bottom:1px solid rgba(0,0,0,0.04)}
.messages{flex:1;overflow:auto;padding:16px;background:#fff}
.msg{margin-bottom:12px;padding:8px;border-radius:8px;background:rgba(0,0,0,0.02)}
.msg .row{display:flex;align-items:flex-start}
.msg .avatar-col{width:44px;display:flex;align-items:flex-start;justify-content:center;margin-right:8px}
.msg .msg-avatar{width:36px;height:36px;border-radius:50%;object-fit:cover}
.msg .avatar-placeholder{width:36px;height:36px;border-radius:50%;background:linear-gradient(180deg,var(--accent-1),var(--accent-2));display:flex;align-items:center;justify-content:center;color:#fff;font-weight:700}
.msg .bubble-col{flex:1}
.msg.me{background:linear-gradient(90deg,rgba(255,154,102,0.12),rgba(255,122,51,0.12));align-self:flex-end}
.meta{font-size:12px;color:var(--muted);margin-bottom:6px}
.composer{display:flex;padding:12px;border-top:1px solid rgba(0,0,0,0.04)}
.composer input{flex:1;padding:10px;border-radius:8px;border:1px solid #eee;margin-right:8px}
.composer .send{background:linear-gradient(180deg,var(--accent-1),var(--accent-2));color:#fff;border:0;padding:10px 14px;border-radius:8px}
</style>
