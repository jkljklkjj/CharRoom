<template>
  <div class="chat-wrap">
    <!-- 聊天顶部：显示当前选中好友名称（移动端隐藏，由外层提供顶部栏） -->
    <header class="chat-top" v-if="!isMobile && currentChatId !== null">
      <span class="chat-top-title">{{ currentChatTitle }}</span>
      <div class="chat-top-actions" v-if="!isGroupChat">
        <button class="chat-top-btn" @click="toggleFriendMenu" title="好友操作">⋯</button>
        <div v-if="showFriendMenu" class="friend-dropdown" @click.stop>
          <div class="dropdown-item" @click="showFriendInfo">{{ $t('chat.info') }}</div>
          <div class="dropdown-item danger" @click="confirmDeleteFriend">{{ $t('chat.deleteFriend') }}</div>
        </div>
      </div>
    </header>

    <!-- 好友信息弹窗 -->
    <div v-if="showInfoModal" class="modal-overlay" @click="showInfoModal = false">
      <div class="modal-content" @click.stop>
        <button class="modal-close" @click="showInfoModal = false">✕</button>
        <div class="friend-info" v-if="friendInfo">
          <img :src="friendInfo.avatar || '/icons/icon-192x192.png'" class="info-avatar" />
          <div class="info-name">{{ friendInfo.name || friendInfo.username || friendInfo.id }}</div>
          <div class="info-id">{{ $t('chat.friendId') }}: {{ friendInfo.id }}</div>
          <div class="info-email" v-if="friendInfo.email">{{ $t('chat.email') }}: {{ friendInfo.email }}</div>
          <div class="info-signature" v-if="friendInfo.signature">{{ $t('chat.signature') }}: {{ friendInfo.signature }}</div>
        </div>
        <div v-else class="friend-info-loading">{{ $t('chat.loading') }}</div>
      </div>
    </div>

    <!-- 删除好友确认 -->
    <div v-if="showDeleteConfirm" class="modal-overlay" @click="showDeleteConfirm = false">
      <div class="modal-content confirm-dialog" @click.stop>
        <div class="confirm-text">{{ $t('chat.deleteConfirm') }}</div>
        <div class="confirm-actions">
          <button class="btn cancel" @click="showDeleteConfirm = false">{{ $t('chat.cancel') }}</button>
          <button class="btn danger" @click="doDeleteFriend">{{ $t('chat.confirmDelete') }}</button>
        </div>
      </div>
    </div>

    <!-- 聊天消息列表：只显示当前会话的消息 -->
    <div class="messages" ref="msgList" v-if="currentChatId !== null">
      <div v-for="(m,i) in currentMessages" :key="i" :class="['msg', {me: m.user==='you'}]" @contextmenu.prevent="showMessageContextMenu($event, m)">
        <div class="row">
          <div class="avatar-col">
            <img v-if="getAvatar(m.user)" :src="getAvatar(m.user)" class="msg-avatar" />
            <div v-else class="avatar-placeholder">{{ initials('' + m.user) }}</div>
          </div>
          <div class="bubble-col">
            <div class="meta">
              <span class="sender">{{ m.user === 'you' ? $t('chat.myself') : (currentChatUser ? (currentChatUser.name || currentChatUser.username) : $t('chat.groupChat')) }}</span>
              <span class="time">{{ formatRelativeTime(m.time) }}</span>
            </div>
            <!-- 图片消息 -->
            <div v-if="isImageMessage(m.text)" class="image-message">
              <img :src="extractImageUrl(m.text)" :alt="extractImageAlt(m.text)" @click="openImagePreview(extractImageUrl(m.text))" />
            </div>
            <!-- 文件消息 -->
            <div v-else-if="isFileMessage(m.text)" class="file-message">
              <div class="file-icon">{{ getFileIcon(extractFileName(m.text)) }}</div>
              <div class="file-info">
                <div class="file-name">{{ extractFileName(m.text) }}</div>
                <div class="file-size">{{ extractFileSize(m.text) }}</div>
              </div>
            </div>
            <!-- 普通文本消息 -->
            <div v-else class="text-content" v-html="formatText(m.text)"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 消息上下文菜单 -->
    <div v-if="showContextMenu" class="context-menu" :style="{ left: contextMenuPos.x + 'px', top: contextMenuPos.y + 'px' }">
      <div class="menu-item" @click="copyMessage">{{ $t('chat.context.copy') }}</div>
      <div class="menu-item" @click="shareMessage" v-if="canShare">{{ $t('chat.context.share') }}</div>
      <div class="menu-item" @click="replyMessage">{{ $t('chat.context.reply') }}</div>
      <div class="menu-item danger" v-if="selectedMessage?.user === 'you'" @click="deleteMessage">{{ $t('chat.context.delete') }}</div>
    </div>

    <!-- 未选中好友时的占位 -->
    <div class="empty-placeholder" v-if="currentChatId === null">
      <div class="placeholder-text">{{ $t('chat.selectContactHint') }}</div>
    </div>

    <!-- 输入框：只要选中会话就显示 -->
    <div
      class="composer-wrap"
      v-if="currentChatId !== null"
      @paste="handlePaste"
      @dragover.prevent="handleDragOver"
      @dragleave.prevent="handleDragLeave"
      @drop.prevent="handleDrop"
      :class="{ 'drag-over': isDragOver }"
    >
      <!-- 拖拽提示 -->
      <div v-if="isDragOver" class="drag-overlay">
        <div class="drag-tip">{{ $t('chat.dragTip') }}</div>
      </div>

      <form class="composer" @submit.prevent="send">
        <textarea
          v-model="text"
          ref="messageInput"
          :placeholder="$t('chat.inputPlaceholder')"
          rows="1"
          @keydown.enter.exact.prevent="send"
          @input="autoResize"
        ></textarea>
        <button class="send" type="submit">{{ $t('chat.send') }}</button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { useStore } from '../store'
import chatSocket from '../services/chatSocket'

const store = useStore()
const { t } = useI18n()
const text = ref('')
const msgList = ref(null)
const messageInput = ref(null)
const isDragOver = ref(false)
const showContextMenu = ref(false)
const contextMenuPos = ref({ x: 0, y: 0 })
const selectedMessage = ref(null)
const canShare = ref('share' in navigator)

// 监听窗口大小变化
const windowWidth = ref(window.innerWidth)
let resizeTimer = null
function handleResize() {
  clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => {
    windowWidth.value = window.innerWidth
  }, 100)
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  document.addEventListener('click', handleClickOutside)
})
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('click', handleClickOutside)
  clearTimeout(resizeTimer)
})

// 当前选中的聊天 ID
const currentChatId = computed(() => {
  const val = store.state.selectedChatId
  console.log('🟡 ChatWindow currentChatId computed, val=', val, 'type=', typeof val)
  return val
})

// 当前选中的聊天用户
const currentChatUser = computed(() => {
  if (currentChatId.value == null) return null
  return store.state.users.find(u => String(u.id) === String(currentChatId.value)) || null
})

// 是否为群聊
const isGroupChat = computed(() => {
  return currentChatId.value != null && Number(currentChatId.value) < 0
})

// 好友操作
const showFriendMenu = ref(false)
const showInfoModal = ref(false)
const showDeleteConfirm = ref(false)
const friendInfo = ref(null)

function toggleFriendMenu() { showFriendMenu.value = !showFriendMenu.value }

function handleClickOutside() { if (showFriendMenu.value) showFriendMenu.value = false }

async function showFriendInfo() {
  showFriendMenu.value = false; showInfoModal.value = true; friendInfo.value = null
  if (!currentChatUser.value) return
  const { getUserDetail } = await import('../api')
  const data = await getUserDetail(currentChatUser.value.id)
  friendInfo.value = data?.data || currentChatUser.value
}

function confirmDeleteFriend() { showFriendMenu.value = false; showDeleteConfirm.value = true }

async function doDeleteFriend() {
  if (!currentChatUser.value) return
  const { delFriend } = await import('../api')
  const ok = await delFriend(currentChatUser.value.id)
  if (ok) { store.removeUser(currentChatUser.value.id) }
  showDeleteConfirm.value = false
}

const currentChatTitle = computed(() => {
  if (currentChatUser.value) {
    return currentChatUser.value.name || currentChatUser.value.account || currentChatUser.value.username || t('chat.userPrefix', { id: currentChatUser.value.id })
  }
  if (isGroupChat.value) {
    return t('chat.groupChat')
  }
  return t('chat.selectContact')
})

// 判断是否为移动端
const isMobile = computed(() => {
  return windowWidth.value < 768
})

// 当前会话的消息列表（私聊或群聊）
const currentMessages = computed(() => {
  if (currentChatId.value == null) return []
  const msgs = isGroupChat.value ? store.state.groupMessages : store.state.messages
  // 只保留最新 100 条（类似 QQ），消除大量消息时的滚动开销
  return msgs.length > 100 ? msgs.slice(msgs.length - 100) : msgs
})


let _scrollInit = false

// QQ 式滚动：首次定位到底部（无动画），后续仅当接近底部时跟随
function scrollToBottom(animate = false) {
  nextTick(() => {
    const el = msgList.value
    if (!el) return
    if (animate) {
      el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
    } else {
      el.scrollTop = el.scrollHeight
    }
  })
}

// 首次进入：直接定位到底部，无动画
watch(currentChatId, () => {
  _scrollInit = false
  scrollToBottom(false)
}, { immediate: true })

// 新消息到达：仅在接近底部时跟随（无动画）
// 通过监听数组长度变化触发，因为 .push() 不改变引用
watch(() => isGroupChat.value ? store.state.groupMessages.length : store.state.messages.length, () => {
  const el = msgList.value
  if (!el) return
  if (!_scrollInit) {
    _scrollInit = true
    scrollToBottom(false)
    return
  }
  // 在底部附近才跟随
  const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 200
  if (nearBottom) {
    scrollToBottom(true)
  }
})

// 相对时间格式化
function formatRelativeTime(ts) {
  if (!ts) return ''
  const now = new Date()
  const time = new Date(ts)
  const diff = now - time
  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (seconds < 10) return t('time.justNow')
  if (seconds < 60) return t('time.secondsAgo', { n: seconds })
  if (minutes < 60) return t('time.minutesAgo', { n: minutes })
  if (hours < 24) return t('time.hoursAgo', { n: hours })
  if (days === 1) return t('time.yesterday')
  if (days < 7) return t('time.daysAgo', { n: days })

  // 超过 7 天显示绝对时间
  const month = time.getMonth() + 1
  const day = time.getDate()
  const hour = time.getHours().toString().padStart(2, '0')
  const minute = time.getMinutes().toString().padStart(2, '0')
  return `${month}/${day} ${hour}:${minute}`
}

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

// 图片消息检测与解析
function isImageMessage(text) {
  if (!text) return false
  return text.startsWith('![') && text.includes('](data:image/')
}

function extractImageUrl(text) {
  const match = text.match(/!\[(.*?)\]\((.*)\)/)
  return match ? match[2] : ''
}

function extractImageAlt(text) {
  const match = text.match(/!\[(.*?)\]\((.*)\)/)
  return match ? match[1] : t('chat.imageAlt')
}

function openImagePreview(url) {
  window.open(url, '_blank', 'noopener,noreferrer')
}

// 文件消息检测与解析
function isFileMessage(text) {
  if (!text) return false
  return text.startsWith('📎 ') && text.includes('(') && text.endsWith('KB)')
}

function extractFileName(text) {
  const match = text.match(/📎 (.*?) \(/)
  return match ? match[1] : t('chat.unknownFile')
}

function extractFileSize(text) {
  const match = text.match(/\(([\d.]+KB)\)/)
  return match ? match[1] : ''
}

function getFileIcon(filename) {
  const ext = filename.split('.').pop().toLowerCase()
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(ext)) return '🖼️'
  if (['pdf'].includes(ext)) return '📄'
  if (['doc', 'docx'].includes(ext)) return '📝'
  if (['xls', 'xlsx'].includes(ext)) return '📊'
  if (['ppt', 'pptx'].includes(ext)) return '📈'
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return '📦'
  if (['mp3', 'wav', 'ogg', 'flac'].includes(ext)) return '🎵'
  if (['mp4', 'avi', 'mov', 'mkv', 'webm'].includes(ext)) return '🎬'
  return '📄'
}

// 文本格式化（支持简单的 markdown）
function formatText(text) {
  if (!text || typeof text !== "string") return ''
  // 转义 HTML 防止 XSS
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  return escaped
}

// textarea 自动高度
function autoResize() {
  const el = messageInput.value
  if (!el) return
  el.style.height = 'auto'
  const newHeight = Math.min(el.scrollHeight, 120) // 最大 5 行约 120px
  el.style.height = newHeight + 'px'
}

let _sendLock = { private: false, group: false }
function send(){
  const key = isGroupChat.value ? 'group' : 'private'
  if(!text.value.trim() || currentChatId.value == null || _sendLock[key]) return
  _sendLock[key] = true
  setTimeout(() => _sendLock[key] = false, 500)
  const m = {
    user: 'you',
    text: text.value,
    time: new Date().toISOString(),
    targetId: currentChatId.value // 添加目标用户ID，用于区分会话
  }

  const to = currentChatId.value
  const wrapper = isGroupChat.value
    ? {
        type: 'group_chat',
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

  chatSocket.sendWrapper(wrapper).catch(e => console.warn("send failed:", e.message))
  text.value = ''
  // 滚动到底部
  scrollToBottom()
}

// 处理粘贴事件
function handlePaste(e) {
  const items = e.clipboardData?.items
  if (!items || currentChatId.value == null) return

  for (const item of items) {
    // 处理图片
    if (item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) {
        handleFileUpload(file)
        e.preventDefault()
        break
      }
    }
    // 处理其他文件
    else if (item.kind === 'file') {
      const file = item.getAsFile()
      if (file) {
        handleFileUpload(file)
        e.preventDefault()
        break
      }
    }
  }
}

// 处理拖拽进入
function handleDragOver(e) {
  if (currentChatId.value == null) return
  isDragOver.value = true
}

// 处理拖拽离开
function handleDragLeave(e) {
  isDragOver.value = false
}

// 处理文件放下
function handleDrop(e) {
  isDragOver.value = false
  const files = e.dataTransfer?.files
  if (!files || files.length === 0 || currentChatId.value == null) return

  for (const file of files) {
    handleFileUpload(file)
  }
}

// 处理文件上传
async function handleFileUpload(file) {
  // 处理图片文件，转成base64发送
  if (file.type.startsWith('image/')) {
    const reader = new FileReader()
    reader.onload = async (e) => {
      const base64Data = e.target.result
      const m = {
        user: 'you',
        text: `![${file.name}](${base64Data})`,
        time: new Date().toISOString(),
        targetId: currentChatId.value
      }

      const wrapper = isGroupChat.value
        ? {
            type: 'group_chat',
            groupChat: { targetClientId: String(currentChatId.value), content: m.text, timestamp: m.time }
          }
        : {
            type: 'chat',
            chat: { targetClientId: String(currentChatId.value), content: m.text, timestamp: m.time }
          }

      if (isGroupChat.value) {
        store.addGroupMessage({ ...m, groupId: currentChatId.value })
      } else {
        store.addMessage(m)
      }

      await chatSocket.sendWrapper(wrapper).catch(() => {})
      scrollToBottom()
    }
    reader.readAsDataURL(file)
  } else {
    // 处理其他类型文件
    const m = {
      user: 'you',
      text: `📎 ${file.name} (${(file.size / 1024).toFixed(1)}KB)`,
      time: new Date().toISOString(),
      targetId: currentChatId.value
    }

    const wrapper = isGroupChat.value
      ? {
          type: 'group_chat',
          groupChat: { targetClientId: String(currentChatId.value), content: m.text, timestamp: m.time }
        }
      : {
          type: 'chat',
          chat: { targetClientId: String(currentChatId.value), content: m.text, timestamp: m.time }
        }

    if (isGroupChat.value) {
      store.addGroupMessage({ ...m, groupId: currentChatId.value })
    } else {
      store.addMessage(m)
    }

    await chatSocket.sendWrapper(wrapper).catch(() => {})
    scrollToBottom()
  }
}

// 显示消息上下文菜单
function showMessageContextMenu(e, message) {
  e.preventDefault()
  selectedMessage.value = message
  contextMenuPos.value = { x: e.clientX, y: e.clientY }
  showContextMenu.value = true

  // 点击其他地方关闭菜单
  document.addEventListener('click', closeContextMenu, { once: true })
}

// 关闭上下文菜单
function closeContextMenu() {
  showContextMenu.value = false
  selectedMessage.value = null
}

// 复制消息内容
function copyMessage() {
  if (!selectedMessage.value) return
  navigator.clipboard.writeText(selectedMessage.value.text).then(() => {
    console.log('Message copied successfully')
  })
  closeContextMenu()
}

// 分享消息（使用系统分享API）
async function shareMessage() {
  if (!selectedMessage.value || !canShare.value) return
  try {
    await navigator.share({
      text: selectedMessage.value.text,
      title: t('chat.shareTitle')
    })
  } catch (err) {
    console.log('Share cancelled or failed:', err)
  }
  closeContextMenu()
}

// 回复消息
function replyMessage() {
  if (!selectedMessage.value) return
  // 在输入框中引用要回复的内容
  const sender = selectedMessage.value.user === 'you' ? t('chat.myself') : (currentChatUser.value?.name || currentChatUser.value?.username || t('chat.myself'))
  text.value = t('chat.replyPrefix', { sender }) + `: \n${selectedMessage.value.text}\n---\n`
  messageInput.value?.focus()
  // 移动光标到末尾
  if (messageInput.value) {
    messageInput.value.selectionStart = messageInput.value.selectionEnd = messageInput.value.value.length
  }
  closeContextMenu()
}

// 删除消息（仅自己的消息）
function deleteMessage() {
  if (!selectedMessage.value) return
  // 从 store 中删除（需要 store 支持，这里只做本地删除）
  const idx = currentMessages.value.findIndex(m => m === selectedMessage.value)
  if (idx > -1) {
    if (isGroupChat.value) {
      store.state.groupMessages.splice(idx, 1)
    } else {
      store.state.messages.splice(idx, 1)
    }
  }
  closeContextMenu()
}

// 键盘快捷键处理
function handleKeydown(e) {
  if (e.key === 'Escape') {
    showContextMenu.value = false
    showFriendMenu.value = false
    showInfoModal.value = false
    showDeleteConfirm.value = false
  }
}

onMounted(()=>{
  // 组件挂载时滚动到底部
  scrollToBottom()
  // 监听键盘快捷键
  window.addEventListener('keydown', handleKeydown)
  // 可在此连接 websocket 并处理 incoming messages
})
</script>

<style scoped>
.chat-wrap{display:flex;flex-direction:column;height:100%}
.chat-top{padding:12px 16px;border-bottom:1px solid var(--surface-border);font-weight:600}
.messages{flex:1;overflow:auto;padding:16px;background:var(--panel);display:flex;flex-direction:column;gap:12px}
.msg{display:flex;width:100%}
.msg .row{display:flex;align-items:flex-start;gap:8px}
.msg .avatar-col{width:44px;display:flex;align-items:flex-start;justify-content:center;flex-shrink:0}
.msg .msg-avatar{width:36px;height:36px;border-radius:50%;object-fit:cover}
.msg .avatar-placeholder{width:36px;height:36px;border-radius:50%;background:linear-gradient(180deg,var(--accent-1),var(--accent-2));display:flex;align-items:center;justify-content:center;color:#fff;font-weight:700}
.msg .bubble-col{
  max-width: 70%; /* 限制最大宽度为容器的 70% */
  word-wrap: break-word;
  white-space: pre-wrap;
  width: fit-content; /* 宽度随内容自适应 */
  border-radius: 18px;
  padding: 10px 14px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
  transition: transform 0.2s ease, opacity 0.2s ease;
  animation: messageSlideIn 0.3s ease-out;
}

@keyframes messageSlideIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

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
  border-radius: 18px 18px 4px 18px;
  padding: 10px 14px;
}
.msg.me .meta{
  color: rgba(255,255,255,0.85);
}

/* 对方发的消息：在左边，灰色气泡，头像在左侧 */
.msg:not(.me){
  justify-content: flex-start;
}
.msg:not(.me) .bubble-col{
  background: var(--bubble-bg);
  border-radius: 18px 18px 18px 4px;
  padding: 10px 14px;
}

/* 消息元信息（发送者 + 时间） */
.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 6px;
}
.meta .sender {
  font-weight: 500;
}
.meta .time {
  opacity: 0.7;
  font-size: 10px;
}

/* 文本内容 */
.text-content {
  word-wrap: break-word;
  white-space: pre-wrap;
  line-height: 1.5;
  font-size: 14px;
}

/* 图片消息 */
.image-message {
  margin-top: 4px;
}
.image-message img {
  max-width: 100%;
  max-height: 300px;
  border-radius: 12px;
  cursor: pointer;
  transition: transform 0.2s ease;
  object-fit: cover;
}
.image-message img:hover {
  transform: scale(1.02);
}

/* 文件消息 */
.file-message {
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(255, 255, 255, 0.1);
  padding: 8px 12px;
  border-radius: 10px;
  margin-top: 4px;
  backdrop-filter: blur(10px);
}
.file-message .file-icon {
  font-size: 24px;
  flex-shrink: 0;
}
.file-message .file-info {
  min-width: 0;
  flex: 1;
}
.file-message .file-name {
  font-size: 13px;
  font-weight: 500;
  color: inherit;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.file-message .file-size {
  font-size: 11px;
  opacity: 0.7;
  margin-top: 2px;
}

/* Composer 输入框 */
.composer{display:flex;padding:12px;border-top:1px solid var(--surface-border);gap:8px}
.composer textarea{
  flex:1;
  padding:10px 14px;
  border-radius:12px;
  border:1px solid var(--surface-border);
  background:var(--panel);
  color:var(--text-primary);
  font-size:14px;
  font-family: inherit;
  resize: none;
  min-height: 44px;
  max-height: 120px;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.composer textarea:focus{
  outline: none;
  border-color: var(--accent-2);
  box-shadow: 0 0 0 3px rgba(255, 122, 51, 0.1);
}
.composer .send{
  background:linear-gradient(180deg,var(--accent-1),var(--accent-2));
  color:#fff;
  border:0;
  padding:10px 20px;
  border-radius:12px;
  font-weight:600;
  cursor:pointer;
  transition: transform 0.1s, box-shadow 0.2s;
}
.composer .send:hover{
  box-shadow: 0 4px 12px rgba(255, 122, 51, 0.3);
}
.composer .send:active{
  transform: scale(0.96);
}

/* 空状态样式 */
.empty-placeholder{flex:1;display:flex;align-items:center;justify-content:center;background:#fafafa}
.placeholder-text{color:#999;font-size:16px}

/* 上下文菜单样式 */
.context-menu {
  position: fixed;
  background: var(--panel);
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
  padding: 4px 0;
  z-index: 1000;
  min-width: 120px;
  color: var(--text-primary);
}
.menu-item {
  padding: 8px 12px;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.1s;
}
.menu-item:hover {
  background: var(--surface-border);
}
.menu-item.danger {
  color: #ff4757;
}
.menu-item.danger:hover {
  background: rgba(255, 71, 87, 0.1);
}

/* 聊天顶部操作栏 */
.chat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--surface-border);
  font-weight: 600;
}
.chat-top-title {
  flex: 1;
}
.chat-top-actions {
  position: relative;
}
.chat-top-btn {
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  color: var(--text-secondary);
}
.chat-top-btn:hover {
  background: var(--surface-border);
}
.friend-dropdown {
  position: absolute;
  right: 0;
  top: 100%;
  background: var(--panel);
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  min-width: 140px;
  z-index: 100;
  overflow: hidden;
}
.dropdown-item {
  padding: 10px 16px;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.15s;
}
.dropdown-item:hover {
  background: var(--surface-border);
}
.dropdown-item.danger {
  color: #ff4757;
}

/* 模态弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}
.modal-content {
  background: var(--panel);
  border-radius: 12px;
  padding: 24px;
  min-width: 300px;
  max-width: 400px;
  position: relative;
}
.modal-close {
  position: absolute;
  right: 12px;
  top: 12px;
  background: none;
  border: none;
  font-size: 18px;
  cursor: pointer;
  color: var(--text-secondary);
}
.friend-info {
  text-align: center;
}
.info-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  object-fit: cover;
  margin-bottom: 12px;
}
.info-name {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 8px;
}
.info-id, .info-email, .info-signature {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}
.friend-info-loading {
  text-align: center;
  color: var(--text-secondary);
  padding: 40px 0;
}
.confirm-dialog {
  text-align: center;
}
.confirm-text {
  font-size: 16px;
  margin-bottom: 20px;
}
.confirm-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
.confirm-actions .btn {
  padding: 8px 24px;
  border-radius: 6px;
  border: 1px solid var(--surface-border);
  cursor: pointer;
  font-size: 14px;
  background: var(--panel);
  color: var(--text-primary);
}
.confirm-actions .btn.danger {
  background: #ff4757;
  color: #fff;
  border-color: #ff4757;
}
</style>
