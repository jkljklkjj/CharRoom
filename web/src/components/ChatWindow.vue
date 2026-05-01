<template>
  <div class="chat-wrap">
    <!-- 聊天顶部：显示当前选中好友名称（移动端隐藏，由外层提供顶部栏） -->
    <header class="chat-top" v-if="!isMobile">
      {{ currentChatTitle }}
    </header>

    <!-- 聊天消息列表：只显示当前会话的消息 -->
    <div class="messages" ref="msgList" v-if="currentChatId !== null">
      <div class="history-search">
        <input
          v-model="historyQuery"
          type="search"
          placeholder="搜索当前聊天历史，支持消息内容和发送者"
        />
      </div>
      <div v-for="(m,i) in filteredMessages" :key="i" :class="['msg', {me: m.user==='you'}]">
        <div class="row">
          <div class="avatar-col">
            <img v-if="getAvatar(m.user)" :src="getAvatar(m.user)" class="msg-avatar" />
            <div v-else class="avatar-placeholder">{{ initials('' + m.user) }}</div>
          </div>
          <div class="bubble-col">
            <div class="meta">{{ m.user === 'you' ? '我' : (currentChatUser ? (currentChatUser.name || currentChatUser.username) : '群聊') }} · {{ time(m.time) }}</div>
            <div class="text">{{ m.text }}</div>
          </div>
        </div>
      </div>
      <!-- 没有消息时的提示 -->
      <div v-if="filteredMessages.length === 0" class="empty-chat">
        未找到匹配消息，换个关键词试试
      </div>
    </div>

    <!-- 未选中好友时的占位 -->
    <div class="empty-placeholder" v-else>
      <div class="placeholder-text">👆 请在左侧选择一个联系人开始聊天</div>
    </div>

    <!-- 输入框：只要选中会话就显示 -->
    <form class="composer" @submit.prevent="send" v-if="currentChatId !== null">
      <input v-model="text" placeholder="输入消息并回车发送..." />
      <button class="send">发送</button>
    </form>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch, onUnmounted } from 'vue'
import { useStore } from '../store'
import chatSocket from '../services/chatSocket'

const store = useStore()
const text = ref('')
const historyQuery = ref('')
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

// 当前选中的聊天 ID
const currentChatId = computed(() => store.state.selectedChatId)

// 当前选中的聊天用户
const currentChatUser = computed(() => {
  if (currentChatId.value == null) return null
  return store.state.users.find(u => u.id === currentChatId.value) || null
})

// 是否为群聊
const isGroupChat = computed(() => {
  return currentChatId.value != null && Number(currentChatId.value) < 0
})

const currentChatTitle = computed(() => {
  if (currentChatUser.value) {
    return currentChatUser.value.name || currentChatUser.value.account || currentChatUser.value.username || `用户 ${currentChatUser.value.id}`
  }
  if (isGroupChat.value) {
    return '群聊'
  }
  return '请选择一个联系人开始聊天'
})

// 判断是否为移动端
const isMobile = computed(() => {
  return window.innerWidth < 768
})

// 当前会话的消息列表（私聊或群聊）
const currentMessages = computed(() => {
  if (currentChatId.value == null) return []
  return isGroupChat.value ? store.state.groupMessages : store.state.messages
})

const filteredMessages = computed(() => {
  const keyword = historyQuery.value.trim().toLowerCase()
  if (!keyword) return currentMessages.value
  return currentMessages.value.filter(m => {
    const textValue = String(m.text || m.message || '').toLowerCase()
    const senderValue = String(m.user || m.senderName || '').toLowerCase()
    return textValue.includes(keyword) || senderValue.includes(keyword)
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
  const id = String(userId)
  const u = store.state.users.find(x => String(x.id) === id)
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
  if(!text.value.trim() || currentChatId.value == null) return
  const m = {
    user: 'you',
    text: text.value,
    time: new Date().toISOString(),
    targetId: currentChatId.value // 添加目标用户ID，用于区分会话
  }

  const to = currentChatId.value
  const wrapper = isGroupChat.value
    ? {
        type: 'groupChat',
        groupChat: { targetClientId: String(to), content: m.text, timestamp: m.time }
      }
    : {
        type: 'chat',
        chat: { targetClientId: String(to), content: m.text, timestamp: m.time }
      }

  if (isGroupChat.value) {
    store.addGroupMessage({ ...m, groupId: to })
  } else {
    store.addMessage(m)
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
.messages{flex:1;overflow:auto;padding:16px;background:#fff;display:flex;flex-direction:column;gap:12px}
.msg{display:flex;width:100%}
.msg .row{display:flex;align-items:flex-start;gap:8px}
.msg .avatar-col{width:44px;display:flex;align-items:flex-start;justify-content:center;flex-shrink:0}
.msg .msg-avatar{width:36px;height:36px;border-radius:50%;object-fit:cover}
.msg .avatar-placeholder{width:36px;height:36px;border-radius:50%;background:linear-gradient(180deg,var(--accent-1),var(--accent-2));display:flex;align-items:center;justify-content:center;color:#fff;font-weight:700}
.msg .bubble-col{
  max-width: calc(100% - 52px); /* 减去头像宽度和间距 */
  word-wrap: break-word;
  white-space: pre-wrap;
  width: fit-content; /* 宽度随内容自适应 */
}
.msg .text{word-wrap:break-word;white-space:pre-wrap}

/* 自己发的消息：在右边，橙色气泡，头像在右侧 */
.msg.me{
  justify-content: flex-end;
}
.msg.me .row{
  flex-direction: row-reverse;
}
.msg.me .bubble-col{
  background: linear-gradient(135deg, #ff9a66, #ff7a33);
  color: white;
  border-radius: 18px 18px 18px 4px;
  padding: 10px 14px;
}
.msg.me .meta{
  color: rgba(255,255,255,0.8);
}

/* 对方发的消息：在左边，灰色气泡，头像在左侧 */
.msg:not(.me){
  justify-content: flex-start;
}
.msg:not(.me) .bubble-col{
  background: #f5f5f5;
  border-radius: 18px 18px 4px 18px;
  padding: 10px 14px;
}
.meta{font-size:12px;color:var(--muted);margin-bottom:6px}
.composer{display:flex;padding:12px;border-top:1px solid rgba(0,0,0,0.04)}
.composer input{flex:1;padding:10px;border-radius:8px;border:1px solid #eee;margin-right:8px}
.composer .send{background:linear-gradient(180deg,var(--accent-1),var(--accent-2));color:#fff;border:0;padding:10px 14px;border-radius:8px}

.history-search{padding:0 16px 12px}
.history-search input{width:100%;padding:10px 12px;border-radius:10px;border:1px solid rgba(0,0,0,0.12);outline:none;transition:border-color .2s}
.history-search input:focus{border-color:#ff7a33}
/* 空状态样式 */
.empty-chat{text-align:center;color:#999;padding:40px 20px;font-size:14px}
.empty-placeholder{flex:1;display:flex;align-items:center;justify-content:center;background:#fafafa}
.placeholder-text{color:#999;font-size:16px}
</style>
