<template>
  <div class="chat-wrap">
    <!-- 聊天顶部：显示当前选中好友名称（移动端隐藏，由外层提供顶部栏） -->
    <header class="chat-top" v-if="!isMobile">
      {{ currentChatUser ? (currentChatUser.name || currentChatUser.account || currentChatUser.username || `用户 ${currentChatUser.id}`) : '请选择一个联系人开始聊天' }}
    </header>

    <!-- 聊天消息列表：只显示当前会话的消息 -->
    <div class="messages" ref="msgList" v-if="currentChatUser">
      <div v-for="(m,i) in currentMessages" :key="i" :class="['msg', {me: m.user==='you'}]">
        <div class="row">
          <div class="avatar-col">
            <img v-if="getAvatar(m.user)" :src="getAvatar(m.user)" class="msg-avatar" />
            <div v-else class="avatar-placeholder">{{ initials('' + m.user) }}</div>
          </div>
          <div class="bubble-col">
            <div class="meta">{{ m.user === 'you' ? '我' : (currentChatUser.name || currentChatUser.username) }} · {{ time(m.time) }}</div>
            <div class="text">{{ m.text }}</div>
          </div>
        </div>
      </div>
      <!-- 没有消息时的提示 -->
      <div v-if="currentMessages.length === 0" class="empty-chat">
        暂无消息，开始你们的对话吧~
      </div>
    </div>

    <!-- 未选中好友时的占位 -->
    <div class="empty-placeholder" v-else>
      <div class="placeholder-text">👆 请在左侧选择一个联系人开始聊天</div>
    </div>

    <!-- 输入框：只有选中好友时才显示 -->
    <form class="composer" @submit.prevent="send" v-if="currentChatUser">
      <input v-model="text" placeholder="输入消息并回车发送..." />
      <button class="send">发送</button>
    </form>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch, onUnmounted } from 'vue'
import { useStore } from '../store'
import chatSocket from '../services/chatSocket'
import proto from '../proto'

const store = useStore()
const text = ref('')
const msgList = ref(null)

// 监听窗口大小变化
let resizeTimer = null
function handleResize() {
  // 防抖处理
  clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => {
    // 触发响应式更新
  }, 100)
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  clearTimeout(resizeTimer)
})

// 当前选中的聊天用户
const currentChatUser = computed(() => {
  if (!store.state.selectedChatId) return null
  return store.state.users.find(u => u.id === store.state.selectedChatId) || null
})

// 判断是否为移动端
const isMobile = computed(() => {
  return window.innerWidth < 768
})

// 当前会话的消息过滤
const currentMessages = computed(() => {
  if (!store.state.selectedChatId) return []
  const selectedId = store.state.selectedChatId
  return store.state.messages.filter(m => {
    // 两种情况属于当前会话：
    // 1. 对方发过来的消息：m.user 等于 选中的用户ID
    // 2. 自己发出去的消息：m.targetId 等于 选中的用户ID
    return Number(m.user) === selectedId || m.targetId === selectedId
  })
})

// 切换会话或新消息到来时滚动到底部
watch([currentChatUser, currentMessages], () => {
  setTimeout(() => {
    if (msgList.value) {
      msgList.value.scrollTop = msgList.value.scrollHeight
    }
  }, 50)
}, { deep: true })

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
  if(!text.value.trim() || !store.state.selectedChatId) return
  const m = {
    user: 'you',
    text: text.value,
    time: new Date().toISOString(),
    targetId: store.state.selectedChatId // 添加目标用户ID，用于区分会话
  }
  store.addMessage(m)
  // 发送给选中的联系人
  const to = store.state.selectedChatId
  const wrapper = {
    type: 'chat',
    chat: { targetClientId: String(to), content: m.text, userId: String(store.state.accountId) || 'web', timestamp: m.time }
  }
  chatSocket.sendWrapper(wrapper).catch(()=>{})
  text.value = ''
  // 滚动到底部
  setTimeout(()=>{ if (msgList.value) msgList.value.scrollTop = msgList.value.scrollHeight },50)
}

onMounted(()=>{
  // 可在此连接 websocket 并处理 incoming messages
})
</script>

<style scoped>
.chat-wrap{display:flex;flex-direction:column;height:100%}
.chat-top{padding:12px 16px;border-bottom:1px solid rgba(0,0,0,0.04);font-weight:600}
.messages{flex:1;overflow:auto;padding:16px;background:#fff}
.msg{margin-bottom:12px;display:flex;max-width:70%}
.msg .row{display:flex;align-items:flex-start;width:100%}
.msg .avatar-col{width:44px;display:flex;align-items:flex-start;justify-content:center;margin-right:8px}
.msg .msg-avatar{width:36px;height:36px;border-radius:50%;object-fit:cover}
.msg .avatar-placeholder{width:36px;height:36px;border-radius:50%;background:linear-gradient(180deg,var(--accent-1),var(--accent-2));display:flex;align-items:center;justify-content:center;color:#fff;font-weight:700}
.msg .bubble-col{flex:1;min-width:0}
.msg .text{word-wrap:break-word;white-space:pre-wrap}

/* 自己发的消息：在右边，橙色气泡 */
.msg.me{
  align-self: flex-end;
}
.msg.me .bubble-col{
  background: linear-gradient(135deg, #ff9a66, #ff7a33);
  color: white;
  border-radius: 18px 18px 4px 18px;
  padding: 10px 14px;
}
.msg.me .meta{
  color: rgba(255,255,255,0.8);
}

/* 对方发的消息：在左边，灰色气泡 */
.msg:not(.me){
  align-self: flex-start;
}
.msg:not(.me) .bubble-col{
  background: #f5f5f5;
  border-radius: 18px 18px 18px 4px;
  padding: 10px 14px;
}
.meta{font-size:12px;color:var(--muted);margin-bottom:6px}
.composer{display:flex;padding:12px;border-top:1px solid rgba(0,0,0,0.04)}
.composer input{flex:1;padding:10px;border-radius:8px;border:1px solid #eee;margin-right:8px}
.composer .send{background:linear-gradient(180deg,var(--accent-1),var(--accent-2));color:#fff;border:0;padding:10px 14px;border-radius:8px}

/* 空状态样式 */
.empty-chat{text-align:center;color:#999;padding:40px 20px;font-size:14px}
.empty-placeholder{flex:1;display:flex;align-items:center;justify-content:center;background:#fafafa}
.placeholder-text{color:#999;font-size:16px}
</style>
