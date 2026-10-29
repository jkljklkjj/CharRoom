<template>
  <div class="chat-wrap">
    <!-- 聊天顶部：显示当前选中好友名称（移动端隐藏，由外层提供顶部栏） -->
    <header class="chat-top" v-if="!isMobile && currentChatId !== null">
      <div class="chat-identity"><img v-if="currentChatUser?.avatarUrl" :src="currentChatUser.avatarUrl" alt="" class="paper-avatar" /><span v-else class="paper-avatar">{{ initials(currentChatTitle) }}</span><div><span class="chat-top-title">{{ currentChatTitle }}</span><small :class="{online:currentChatUser?.online}">{{ $t(currentChatUser?.online ? 'product.chatPresence' : 'product.chatQuiet') }}</small></div></div>
      <div class="chat-top-actions" v-if="!isGroupChat">
        <button class="chat-top-btn" @click.stop="toggleFriendMenu" :title="$t('product.conversationOptions')" :aria-label="$t('product.conversationOptions')">⋯</button>
        <div v-if="showFriendMenu" class="friend-dropdown" @click.stop>
          <div class="dropdown-item" @click="showFriendInfo">{{ $t('chat.info') }}</div>
          <div class="dropdown-item danger" @click="confirmDeleteFriend">{{ $t('chat.deleteFriend') }}</div>
        </div>
      </div>
    </header>

    <!-- 好友信息弹窗 -->
    <div v-if="showInfoModal" class="modal-overlay" @click="showInfoModal = false">
      <div ref="infoDialog" class="modal-content" role="dialog" aria-modal="true" :aria-label="$t('chat.info')" tabindex="-1" @click.stop>
        <button class="modal-close" @click="showInfoModal = false">✕</button>
        <SocialProfileCard v-if="currentChatUser && !isGroupChat" :user-id="String(currentChatUser.id)" @chat="showInfoModal = false" @blocked="showInfoModal = false" />
      </div>
    </div>

    <!-- 删除好友确认 -->
    <div v-if="showDeleteConfirm" class="modal-overlay" @click="showDeleteConfirm = false">
      <div ref="deleteDialog" class="modal-content confirm-dialog" role="dialog" aria-modal="true" :aria-label="$t('chat.deleteFriend')" tabindex="-1" @click.stop>
        <div class="confirm-text">{{ $t('chat.deleteConfirm') }}</div>
        <div class="confirm-actions">
          <button class="btn cancel" @click="showDeleteConfirm = false">{{ $t('chat.cancel') }}</button>
          <button class="btn danger" @click="doDeleteFriend">{{ $t('chat.confirmDelete') }}</button>
        </div>
      </div>
    </div>

    <!-- 聊天消息列表：只显示当前会话的消息 -->
    <VirtualMessageList :key="readingKey" class="messages" ref="virtualList" v-if="currentChatId !== null" :items="currentMessages" @scroll="onMessageScroll" v-slot="{ item: m, index }">
      <div>
        <!-- QQ 式时间分隔：首条消息、跨天或与上一条间隔 5 分钟以上时居中显示时间 -->
        <div v-if="showTimeDivider(index)" class="time-divider">{{ formatDividerTime(m.time ?? m.timestamp) }}</div>
        <!-- 系统消息（加入群聊/退出群聊等） -->
        <div v-if="isSystemMessage(m)" class="system-message">
          <span class="system-text">{{ m.text }}</span>
        </div>
        <!-- 普通消息 -->
        <div v-else :class="['msg', {me: m.user===SELF_USER}]" @contextmenu.prevent="showMessageContextMenu($event, m)">
          <div class="row">
            <div class="avatar-col">
              <img v-if="getAvatar(m.user)" :src="getAvatar(m.user)" class="msg-avatar" />
              <div v-else class="avatar-placeholder">{{ initials(senderName(m.user)) }}</div>
            </div>
            <div class="bubble-col paper-bubble" :class="{mine:m.user===SELF_USER}">
              <!-- 群聊保留发送者昵称以区分成员；私聊不显示用户名和时间（参考 QQ） -->
              <div v-if="isGroupChat" class="meta">
                <span class="sender">{{ senderName(m.user) }}</span>
              </div>
            <!-- 图片消息 -->
            <div v-if="m.messageType === 1 && m.fileUrl" class="image-message">
              <img :src="safeAttachmentUrl(m.fileUrl)" :alt="m.fileName || $t('chat.imageAlt')" @click="openImagePreview(m.fileUrl)" />
            </div>
            <!-- 文件消息 -->
            <div v-else-if="m.messageType === 2 && m.fileUrl" class="file-message">
              <div class="file-icon">{{ getFileIcon(m.fileName || '') }}</div>
              <div class="file-info">
                <div class="file-name"><a :href="safeAttachmentUrl(m.fileUrl)" target="_blank" rel="noopener noreferrer">{{ m.fileName || $t('chat.unknownFile') }}</a></div>
                <div class="file-size">{{ ((m.fileSize || 0) / 1024).toFixed(1) }} KB</div>
              </div>
            </div>
            <!-- 普通文本消息 — 安全：text 已在 store 中通过 DOMPurify.sanitize 预清洗 -->
            <div v-else class="text-content" v-html="formatText(m.text)"></div>
            </div>
            <MessageSendIndicator v-if="m.user===SELF_USER" :status="m.isSent" :started-at="m.sendStartedAt ?? normalizeTimeValue(m.time ?? m.timestamp)" @retry="retryMessage(m)" />
          </div>
        </div>
      </div>
    </VirtualMessageList>

    <div v-if="(store.state.historyLoading || store.state.historySyncing) && !currentMessages.length" class="history-feedback" role="status">{{ $t(store.state.historyLoading ? 'sync.historyLoading' : 'sync.historySyncing') }}</div>
    <button v-else-if="store.state.historyError" class="history-feedback" @click="store.setSelectedChat(currentChatId)">{{ $t('sync.historyFailed') }}</button>
    <div v-else-if="currentChatId !== null && !currentMessages.length" class="history-feedback" role="status">{{ $t('sync.historyEmpty') }}</div>
    <button v-if="store.state.historySyncError" class="history-sync-feedback" @click="emit('retry-history')">{{ $t('sync.historySyncFailed') }}</button>
    <span v-else-if="store.state.historySyncing && currentMessages.length" class="history-sync-feedback" role="status">{{ $t('sync.refreshing') }}</span>
    <!-- 消息上下文菜单 -->
    <button v-if="newMessageCount || store.state.historyHasNewer" class="jump-latest" @click="jumpLatest">{{ store.state.historyHasNewer ? $t('polish.latestMessages') : $t('polish.newMessages', { count: newMessageCount }) }} ↓</button>
    <div v-if="showContextMenu" class="context-menu" :style="{ left: contextMenuPos.x + 'px', top: contextMenuPos.y + 'px' }">
      <div class="menu-item" @click="copyMessage">{{ $t('chat.context.copy') }}</div>
      <div class="menu-item" @click="shareMessage" v-if="canShare">{{ $t('chat.context.share') }}</div>
      <div class="menu-item" @click="replyMessage">{{ $t('chat.context.reply') }}</div>
      <div class="menu-item" @click="forwardMessage">{{ $t('chat.context.forward') }}</div>
      <div class="menu-item danger" v-if="selectedMessage?.user === SELF_USER" @click="deleteMessage">{{ $t('chat.context.delete') }}</div>
    </div>

    <!-- 转发选择弹窗 -->
    <div v-if="forwardDialogOpen" class="modal-overlay" @click.self="forwardDialogOpen = false">
      <div ref="forwardDialog" class="modal-content forward-dialog" role="dialog" aria-modal="true" :aria-label="$t('chat.forward.title')" tabindex="-1">
        <div class="forward-header">
          <h3>{{ $t('chat.forward.title') }}</h3>
          <button class="forward-close" @click="forwardDialogOpen = false">×</button>
        </div>
        <div class="forward-list">
          <div
            v-for="u in forwardTargets"
            :key="u.id"
            class="forward-item"
            @click="doForward(u)"
          >
            <div class="forward-avatar" v-if="u.avatarUrl"><img :src="avatarSrc(u)" alt="" /></div>
            <div class="forward-avatar placeholder" v-else>{{ initials(u.name || u.username || u.id) }}</div>
            <div class="forward-name">{{ u.name || u.username || u.id }}</div>
          </div>
          <div v-if="forwardTargets.length === 0" class="forward-empty">{{ $t('chat.forward.empty') }}</div>
        </div>
      </div>
    </div>

    <!-- 未选中好友时的占位 -->
    <div class="empty-placeholder" v-if="currentChatId === null">
      <div class="placeholder-illustration" aria-hidden="true"><span>· · ·</span></div>
      <div class="placeholder-title">{{ $t('product.emptyTitle') }}</div>
      <div class="placeholder-hint">{{ $t('product.emptyHint') }}</div>
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

      <!-- 回复预览栏 -->
      <div v-if="replyTo" class="reply-preview">
        <div class="reply-preview-text">
          <span class="reply-sender">{{ $t('chat.replyTo', { sender: replyTo.sender }) }}</span>
          <span class="reply-content">{{ replyTo.content }}</span>
        </div>
        <button class="reply-cancel" @click="cancelReply" title="取消回复">×</button>
      </div>

      <button v-if="uploading" type="button" @click="cancelUpload">{{ $t('composer.cancelUpload') }}</button>
      <form class="composer" @submit.prevent="send">
        <textarea
          v-model="text"
          ref="messageInput"
          :placeholder="inputPlaceholder"
          :aria-label="$t('chat.inputPlaceholder')"
          rows="1"
          @keydown="composerEnter($event, send)"
          @input="autoResize"
          @focus="onInputFocus"
        ></textarea>
        <button class="send" type="submit" :disabled="!text.trim() || sendingCurrent">{{ $t('chat.send') }}</button>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
const emit=defineEmits<{ 'retry-history': [] }>()
import {ConversationId} from '../utils/conversationId'
import { chatTargetId, sidebarUsers } from '../utils/contactDirectory'
import SocialProfileCard from './SocialProfileCard.vue'
import VirtualMessageList from './VirtualMessageList.vue'
import MessageSendIndicator from './MessageSendIndicator.vue'
import {normalizeTimeValue} from '../store/storage'
import { drafts, composerSubmissions } from '../composables/useComposerDrafts'
import { submitDraft } from '../services/composerDrafts'
import { sessionEpoch } from '../services/sessionEpoch'
import { uploadAttachment } from '../api/attachments'
import { composerEnter } from '../utils/composer'
import { useDialog } from '../composables/useDialog'
import type { Id, TimeValue, ChatMessage, User } from '../types/chat'
import { onBeforeUnmount, ref, onMounted, computed, watch, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { useStore } from '../store'
import chatSocket from '../services/chatSocket'
import { MSG_TYPE } from '../proto/constants'

const store = useStore()
const infoDialog = ref<HTMLElement | null>(null), deleteDialog = ref<HTMLElement | null>(null), forwardDialog = ref<HTMLElement | null>(null)
useDialog(infoDialog, () => { showInfoModal.value = false })
useDialog(deleteDialog, () => { showDeleteConfirm.value = false })
useDialog(forwardDialog, () => { forwardDialogOpen.value = false })
// 注入 store 引用给 chatSocket（替代 window.__chatStore hack）
chatSocket.setStore(store)

// 时间分隔实时刷新：每 60s 更新一次 now，跨天时重算分隔文案（formatDividerTime 读取 now.value）
const now = ref(Date.now())
let relTimeTimer: ReturnType<typeof setTimeout> | undefined

const { t } = useI18n()
const draftVersion = ref(0)
const draftKey = () => `${store.state.accountId}:${currentChatId.value}`
const text = computed({
  get: () => { draftVersion.value; return drafts.snapshot(draftKey()).text },
  set: value => { const draft = drafts.snapshot(draftKey()); drafts.edit(draft.key, value, draft.reply); draftVersion.value++ }
})
const submitting = ref(composerSubmissions)
const sendingCurrent = computed(() => submitting.value.has(draftKey()))
const uploads = new Map<string, AbortController>()
const uploading = ref(false)
function cancelUpload() { uploads.get(draftKey())?.abort() }

const virtualList = ref<InstanceType<typeof VirtualMessageList> | null>(null)
const msgList = computed(() => virtualList.value?.element ?? null)
const messageInput = ref<HTMLTextAreaElement | null>(null)
const isDragOver = ref(false)
const showContextMenu = ref(false)
const contextMenuPos = ref({ x: 0, y: 0 })
const selectedMessage = ref<ChatMessage | null>(null)
const canShare = ref('share' in navigator)

// 监听窗口大小变化
const windowWidth = ref(window.innerWidth)
let resizeTimer: ReturnType<typeof setTimeout> | undefined
function handleResize() {
  clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => {
    windowWidth.value = window.innerWidth
  }, 100)
}
// 竖屏/窄屏用短文案，避免 placeholder 在输入框里折成两行
const inputPlaceholder = computed(() => windowWidth.value < 768
  ? t('chat.inputPlaceholderCompact')
  : t('chat.inputPlaceholder'))

onMounted(() => {
  window.addEventListener('resize', handleResize)
  document.addEventListener('click', handleClickOutside)
  window.addEventListener('keydown', handleKeydown)
  relTimeTimer = setInterval(() => { now.value = Date.now() }, 60000)
  scrollToBottom()
})
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  uploads.forEach(controller => controller.abort())
  window.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('click', handleClickOutside)
  if (relTimeTimer) clearInterval(relTimeTimer)
  clearTimeout(resizeTimer)
})

// 当前选中的聊天 ID
const currentChatId = computed(() => {
  return store.state.selectedChatId
})

// 当前选中的聊天用户
const currentChatUser = computed(() => {
  if (currentChatId.value == null) return null
  return sidebarUsers(store.state.users, store.state.groups).find(u => ConversationId.forContact(u).key === currentChatId.value) || null
})

// 是否为群聊
const isGroupChat = computed(() => {
  return currentChatId.value != null && ConversationId.parse(currentChatId.value).isGroup
})

// 好友操作
const showFriendMenu = ref(false)
const showInfoModal = ref(false)
const showDeleteConfirm = ref(false)

function toggleFriendMenu() { showFriendMenu.value = !showFriendMenu.value }

function handleClickOutside() { if (showFriendMenu.value) showFriendMenu.value = false }

function showFriendInfo() {
  showFriendMenu.value = false
  showInfoModal.value = true
}

function confirmDeleteFriend() { showFriendMenu.value = false; showDeleteConfirm.value = true }

async function doDeleteFriend() {
  if (!currentChatUser.value) return
  try {
    const { delFriend } = await import('../api')
    const ok = await delFriend(currentChatUser.value.id)
    if (ok) { store.removeUser(currentChatUser.value.id) }
  } catch (e) {
    console.warn('删除好友失败:', e)
  }
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
  // 不再 slice(-100)：slice 窗口与「向上加载历史」矛盾（历史插在前面会被窗口截断看不到）。
  // VirtualMessageList bounds mounted DOM while retaining loaded history and stable pagination.
  return isGroupChat.value ? store.state.groupMessages : store.state.messages
})


let _scrollInit = false
const newMessageCount = ref(0)

// QQ 式滚动：首次定位到底部（无动画），后续仅当接近底部时跟随
function scrollToBottom() {
  newMessageCount.value = 0
  nextTick(() => {
    const el = msgList.value
    if (!el) return
    virtualList.value?.scrollToBottom()
  })
}

const readingKey=computed(()=>`${store.state.accountId}:${currentChatId.value ?? ''}`)
let restoringReading=false,readingGeneration=0
function saveReading(key:string) {
  const position=virtualList.value?.captureReading()
  if(position)conversationViews.savePosition(key,position)
}
watch(readingKey,async (key,previous)=>{
  if(previous)saveReading(previous)
  const generation=++readingGeneration
  restoringReading=true;_scrollInit=false;newMessageCount.value=0
  uploading.value=uploads.has(draftKey())
  await nextTick()
  if(generation!==readingGeneration)return
  const saved=conversationViews.position(key)
  if(saved && currentMessages.value.length) {
    await virtualList.value?.restoreReading(saved);_scrollInit=true
  } else if(currentMessages.value.length) {scrollToBottom();_scrollInit=true}
  restoringReading=false
},{immediate:true})
onBeforeUnmount(()=>saveReading(readingKey.value))

async function jumpLatest(){try{if(store.state.historyHasNewer)await store.loadLatestMessages();await nextTick();scrollToBottom()}catch{toast.error(t('social.failed'))}}

// 向上滚动加载更多历史消息
let _loadingOlder = false
async function onMessageScroll() {
  const el = msgList.value
  if (!el || _loadingOlder) return
  store.setHistoryFollowingLatest(el.scrollHeight-el.scrollTop-el.clientHeight<200 && !store.state.historyHasNewer)
  if (el.scrollHeight - el.scrollTop - el.clientHeight < 64) newMessageCount.value = 0
  // 距顶部 100px 时触发加载
  if (el.scrollTop < 100) {
    const hasMore = isGroupChat.value ? store.state.hasMoreGroupMessages : store.state.hasMoreMessages
    if (!hasMore) return
    _loadingOlder = true
    const prevHeight = el.scrollHeight, prevTop = el.scrollTop, conversation = currentChatId.value
    const savedReading=virtualList.value?.captureReading()
    try {
      const loaded = await store.loadOlderMessages()
      await nextTick()
      if (loaded && conversation === currentChatId.value && el === msgList.value) {
        if(savedReading)await virtualList.value?.restoreReading(savedReading)
        else el.scrollTop = prevTop + el.scrollHeight - prevHeight
      }
    } catch { toast.error(t('social.failed')) }
    finally { _loadingOlder = false }
  }
}

// 新消息到达：仅在接近底部时跟随（无动画）
// 通过监听数组长度变化触发，因为 .push() 不改变引用
watch(() => [currentMessages.value.at(-1)?.messageId || currentMessages.value.at(-1)?.time, currentMessages.value.length] as const, ([last, count], [previousLast, previous]) => {
  if(restoringReading)return
  // Prepending history keeps the same last message. It must not act like a new arrival.
  if (!last || last === previousLast) return
  const el = msgList.value
  if (!el) return
  if (!_scrollInit) {
    _scrollInit = true
    scrollToBottom()
    return
  }
  // 在底部附近才跟随
  const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 200
  if (nearBottom) {
    scrollToBottom()
  } else {
    newMessageCount.value += Math.max(1, count - previous)
  }
})

// QQ 式时间分隔：首条消息、跨天或与上一条间隔 5 分钟以上时居中显示时间
const TIME_DIVIDER_GAP_MS = 5 * 60 * 1000
function messageTimestamp(m: ChatMessage | undefined) {
  return m ? normalizeTimeValue(m.time ?? m.timestamp) : 0
}
function dayStart(ts: number) {
  const date = new Date(ts)
  date.setHours(0, 0, 0, 0)
  return date.getTime()
}
function showTimeDivider(index: number) {
  const list = currentMessages.value
  const current = messageTimestamp(list[index])
  if (!current) return false
  if (index === 0) return true
  const previous = messageTimestamp(list[index - 1])
  if (!previous) return true
  return current - previous >= TIME_DIVIDER_GAP_MS || dayStart(current) !== dayStart(previous)
}
// 今天只显示时间；昨天/本年/跨年补齐日期（读取 now.value 使模板随定时器刷新）
function formatDividerTime(ts: TimeValue | undefined) {
  const value = normalizeTimeValue(ts)
  if (!value) return ''
  const date = new Date(value)
  const time = `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
  const days = Math.round((dayStart(now.value) - dayStart(value)) / 86400000)
  if (days <= 0) return time
  if (days === 1) return t('time.dividerYesterday', { time })
  const month = date.getMonth() + 1, day = date.getDate()
  if (date.getFullYear() === new Date(now.value).getFullYear()) return t('time.dividerMonthDay', { month, day, time })
  return t('time.dividerFull', { year: date.getFullYear(), month, day, time })
}

// userId → avatarUrl 的 memoized 映射，避免每条消息 O(好友数) find
// （仅在 store.state.users 变化时重建）
const avatarMap = computed(() => {
  const map = new Map()
  for (const u of store.state.users) {
    if (u && u.id != null) map.set(String(u.id), avatarSrc(u))
  }
  return map
})
const senderNames=computed(()=>new Map(store.state.users.map(user=>[String(user.id),user.name || user.username || String(user.id)])))
function senderName(userId:Id) {
  return userId===SELF_USER?t('chat.myself'):senderNames.value.get(String(userId)) || t('chat.userPrefix',{id:userId})
}

function getAvatar(userId: Id) {
  if (!userId) return null
  return avatarMap.value.get(String(userId)) || null
}

import { avatarSrc } from '../utils/format'
import { AGENT_USER_ID, SELF_USER } from '../constants'
import { conversationViews } from '../services/conversationViews'
import { toast } from '../utils/toast'

import { initials } from '../utils/format'

function safeAttachmentUrl(value: string): string {
  try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) ? url.href : '' }
  catch { return '' }
}
function openImagePreview(value: string) {
  const url = safeAttachmentUrl(value)
  if (url) window.open(url, '_blank', 'noopener,noreferrer')
}

function getFileIcon(filename: string) {
  const ext = (filename.split('.').pop() || '').toLowerCase()
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

// 系统消息检测
function isSystemMessage(m: ChatMessage) {
  return m.user === 'system' || (m as { senderId?: string }).senderId === 'system'
}

// 文本格式化（文本已在 store 中预清洗，此处直接返回）
function formatText(text: string) {
  if (!text || typeof text !== "string") return ''
  return text
}

// textarea 自动高度
function autoResize() {
  const el = messageInput.value
  if (!el) return
  el.style.height = 'auto'
  const newHeight = Math.min(el.scrollHeight, 120) // 最大 5 行约 120px
  el.style.height = newHeight + 'px'
}

/** 移动端键盘弹出时，将输入区滚到可视范围 */
function onInputFocus() {
  // 仅移动端需要
  if (window.innerWidth > 768) return
  // 延迟执行等键盘完全弹出
  setTimeout(() => {
    const composer = document.querySelector('.composer')
    composer?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, 300)
}

async function send() {
  const snapshot = drafts.snapshot(draftKey()), target = currentChatId.value
  if (!snapshot.text.trim() || target == null || submitting.value.has(snapshot.key)) return
  const owner = String(store.state.accountId || ''), group = isGroupChat.value, session = sessionEpoch.capture()
  submitting.value.add(snapshot.key)
  const localId = 'local_' + globalThis.crypto.randomUUID(), timestamp = Date.now()
  const message: ChatMessage = { user: 'you', text: snapshot.text, time: timestamp, targetId: chatTargetId(target),
    messageId: localId, isSent: 'sending', replyTo: snapshot.reply || undefined }
  const fields = { targetClientId: chatTargetId(target), content: message.text,
    timestamp, messageId: localId, ...(snapshot.reply?.messageId ? { replyToMessageId: snapshot.reply.messageId,
      replyToContent: snapshot.reply.content, replyToSender: snapshot.reply.sender } : {}) }
  const agent = !group && Number(chatTargetId(target)) === AGENT_USER_ID
  const wrapper = group ? { type: MSG_TYPE.GROUP_CHAT, groupChat: fields }
    : { type: agent ? MSG_TYPE.AGENT_CHAT : MSG_TYPE.CHAT, chat: fields }
  try {
    await submitDraft(
      () => group ? store.addGroupMessage({ ...message, groupId: chatTargetId(target) }) : store.addMessage(message),
      async () => {
        if (!session.isCurrent() || owner !== String(store.state.accountId)) throw new Error('Stale account')
        if (agent) { if (!await chatSocket.sendWrapper(wrapper)) throw new Error('Agent transport unavailable') }
        else await chatSocket.enqueueWrapper(wrapper, owner)
      },
      () => { drafts.commit(snapshot); draftVersion.value++ })
    if (session.isCurrent() && currentChatId.value === target) scrollToBottom()
  } catch {
    if (session.isCurrent()) { store.updateMessageStatus(localId, 'failed'); toast.error(t('composer.persistFailed')) }
  } finally { submitting.value.delete(snapshot.key) }
}

/**
 * 重试发送失败的消息。
 */
async function retryMessage(m: ChatMessage) {
  if (!m.text || !m.messageId) return
  const group = m.groupId != null || isGroupChat.value
  const to = m.groupId != null ? ConversationId.group(m.groupId).key : m.targetId != null ? ConversationId.privateChat(m.targetId).key : currentChatId.value
  if (!to) return

  // 重试复用原 ID，防止 ACK 丢失后把同一条消息再次持久化。
  const newMessageId = m.messageId
  const replyFields = m.replyTo?.messageId ? {
    replyToMessageId: m.replyTo.messageId, replyToContent: m.replyTo.content, replyToSender: m.replyTo.sender
  } : {}
  const messageType = m.messageType ?? 0
  const metadata = { messageType, fileUrl: m.fileUrl, fileName: m.fileName, fileSize: m.fileSize }
  const isAgent = !group && Number(chatTargetId(to)) === AGENT_USER_ID
  const wrapper = group
    ? {
        type: MSG_TYPE.GROUP_CHAT,
        groupChat: { targetClientId: chatTargetId(to!), content: m.text, timestamp: new Date(m.time || Date.now()).getTime(), messageId: newMessageId, ...replyFields, ...metadata }
      }
    : isAgent
      ? {
          type: MSG_TYPE.AGENT_CHAT,
          chat: { targetClientId: chatTargetId(to), content: m.text, timestamp: new Date(m.time || Date.now()).getTime(), messageId: newMessageId, ...replyFields, ...metadata }
        }
      : {
          type: MSG_TYPE.CHAT,
          chat: { targetClientId: chatTargetId(to), content: m.text, timestamp: new Date(m.time || Date.now()).getTime(), messageId: newMessageId, ...replyFields, ...metadata }
        }

  // 更新状态为发送中
  store.updateMessageStatus(m.messageId, 'sending', Date.now())

  try {
    await chatSocket.sendWrapper(wrapper)
  } catch (e) {
    console.warn("重试失败:", e instanceof Error ? e.message : String(e))
    store.updateMessageStatus(newMessageId, 'failed')
  }
}

// 处理粘贴事件
function handlePaste(e: ClipboardEvent) {
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
function handleDragOver() {
  if (currentChatId.value == null) return
  isDragOver.value = true
}

// 处理拖拽离开
function handleDragLeave() {
  isDragOver.value = false
}

// 处理文件放下
function handleDrop(e: DragEvent) {
  isDragOver.value = false
  const files = e.dataTransfer?.files
  if (!files || files.length === 0 || currentChatId.value == null) return

  for (const file of files) {
    handleFileUpload(file)
  }
}

// 处理文件上传
const MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB
const MAX_IMAGE_DIM = 1920 // 图片最长边

/** Upload the object first, then enqueue its URL and metadata for the captured conversation. */
async function handleFileUpload(file: File) {
  const target = currentChatId.value, group = isGroupChat.value, owner = String(store.state.accountId || '')
  const key = draftKey(), session = sessionEpoch.capture()
  if (target == null || !owner || uploads.has(key)) return
  if (file.size > MAX_FILE_SIZE) { toast.error(t('chat.fileTooLarge', { size: (file.size / 1024 / 1024).toFixed(1) })); return }
  const controller = new AbortController()
  const onLogout = () => controller.abort()
  session.signal.addEventListener('abort', onLogout, { once: true })
  uploads.set(key, controller); uploading.value = true
  const localId = 'local_' + globalThis.crypto.randomUUID()
  try {
    // Upload bytes once; realtime packets contain only the URL and attachment metadata.
    const url = await uploadAttachment(file, controller.signal)
    if (!session.isCurrent() || controller.signal.aborted || owner !== String(store.state.accountId)) return
    const image = file.type.startsWith('image/'), timestamp = Date.now()
    const message: ChatMessage = { user: SELF_USER, text: image ? `![${file.name}](${url})` : `${file.name}: ${url}`,
      time: timestamp, targetId: chatTargetId(target), messageId: localId, isSent: 'sending',
      messageType: image ? 1 : 2, fileUrl: url, fileName: file.name, fileSize: file.size }
    const fields = { targetClientId: chatTargetId(target), content: message.text,
      timestamp, messageId: localId, messageType: image ? 1 as const : 2 as const, fileUrl: url, fileName: file.name, fileSize: file.size }
    const agent = !group && Number(chatTargetId(target)) === AGENT_USER_ID
    const wrapper = group ? { type: MSG_TYPE.GROUP_CHAT, groupChat: fields }
      : { type: agent ? MSG_TYPE.AGENT_CHAT : MSG_TYPE.CHAT, chat: fields }
    await submitDraft(
      () => group ? store.addGroupMessage({ ...message, groupId: chatTargetId(target) }) : store.addMessage(message),
      async () => {
        if (!session.isCurrent() || controller.signal.aborted) throw new Error('Upload cancelled')
        if (agent) { if (!await chatSocket.sendWrapper(wrapper)) throw new Error('Agent unavailable') }
        else await chatSocket.enqueueWrapper(wrapper, owner)
      }, () => {})
    if (currentChatId.value === target) scrollToBottom()
  } catch (error) {
    if (session.isCurrent() && !controller.signal.aborted) {
      store.updateMessageStatus(localId, 'failed')
      toast.error(t((error as { code?: number })?.code === 5002 ? 'composer.dailyUploadLimit' : 'composer.uploadFailed'))
    }
  } finally {
    session.signal.removeEventListener('abort', onLogout)
    if (uploads.get(key) === controller) uploads.delete(key)
    uploading.value = uploads.has(draftKey())
  }
}

// 显示消息上下文菜单
function showMessageContextMenu(e: MouseEvent, message: ChatMessage) {
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
  navigator.clipboard.writeText(selectedMessage.value.text).catch(e => {
    console.warn('复制失败:', e)
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
    console.debug('Share cancelled or failed:', err)
  }
  closeContextMenu()
}

// 回复预览：选中消息后输入框上方显示引用条，发送时携带 replyTo 字段
const replyTo = computed({
  get: () => { draftVersion.value; return drafts.snapshot(draftKey()).reply },
  set: value => { const draft = drafts.snapshot(draftKey()); drafts.edit(draft.key, draft.text, value); draftVersion.value++ }
}) // { messageId, content, sender }

// 回复消息（进入预览模式，不再把引用文本塞进输入框）
function replyMessage() {
  if (!selectedMessage.value) return
  const m = selectedMessage.value
  const sender = m.user === 'you'
    ? t('chat.myself')
    : (currentChatUser.value?.name || currentChatUser.value?.username || String(m.user))
  replyTo.value = {
    messageId: m.messageId,
    content: String(m.text || '').slice(0, 80),
    sender
  }
  messageInput.value?.focus()
  closeContextMenu()
}

function cancelReply() {
  replyTo.value = null
}

// ── 消息转发 ─────────────────────────────────────────────

const forwardDialogOpen = ref(false)
// 转发目标：好友（排除群组与 AI 助手）
const forwardTargets = computed(() =>
  store.state.users.filter(u => Number(u.id) > 0 && u.id !== AGENT_USER_ID)
)

function forwardMessage() {
  if (!selectedMessage.value) return
  forwardDialogOpen.value = true
  closeContextMenu()
}

async function doForward(target: User) {
  const content = selectedMessage.value?.text
  forwardDialogOpen.value = false
  if (!content) return
  const localMessageId = 'local_' + globalThis.crypto.randomUUID()
  try {
    await chatSocket.sendWrapper({
      type: MSG_TYPE.CHAT,
      chat: {
        targetClientId: String(target.id),
        content,
        timestamp: Date.now(),
        messageId: localMessageId
      }
    })
  } catch (e) {
    console.warn('转发失败:', e)
  }
}

// 删除消息（仅自己的消息）
function deleteMessage() {
  if (!selectedMessage.value) return
  store.deleteMessage(selectedMessage.value, isGroupChat.value)
  closeContextMenu()
}

// 键盘快捷键处理
function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    showContextMenu.value = false
    showFriendMenu.value = false
    showInfoModal.value = false
    showDeleteConfirm.value = false
  }
}
</script>

<style scoped>
.chat-wrap{display:flex;flex-direction:column;height:100%}
.chat-top{padding:12px 16px;border-bottom:1px solid var(--surface-border);font-weight:600}
.messages{flex:1;min-height:0;overflow:auto;overscroll-behavior:contain;scroll-behavior:auto;padding:24px;background:var(--bg);display:flex;flex-direction:column;gap:12px}
.msg{display:flex;width:100%}
.msg .row{display:flex;align-items:flex-start;gap:8px;width:100%;max-width:760px;min-width:0}
.msg .avatar-col{width:44px;display:flex;align-items:flex-start;justify-content:center;flex-shrink:0}
.msg .msg-avatar{width:36px;height:36px;border-radius:12px;object-fit:cover}
.msg .avatar-placeholder{width:36px;height:36px;border-radius:12px;background:var(--apricot);display:flex;align-items:center;justify-content:center;color:var(--on-apricot);font-weight:600}
.msg .bubble-col{
  max-width: 70%; /* 限制最大宽度为容器的 70% */
  word-wrap: break-word;
  white-space: pre-wrap;
  width: fit-content; /* 宽度随内容自适应 */
  flex: 0 1 auto;
  padding: 11px 15px;
}

/* QQ 式时间分隔：仅首条、跨天或间隔 5 分钟以上出现，替代气泡内的用户名/时间 */
.time-divider {
  display: flex;
  justify-content: center;
  padding: 4px 0 2px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
  user-select: none;
}

/* 系统消息（加入群聊/退出群聊等） */
.system-message {
  text-align: center;
  padding: 4px 16px;
  margin: 2px 0;
}
.system-text {
  display: inline-block;
  background: rgba(0, 0, 0, 0.05);
  color: #999;
  font-size: 12px;
  padding: 4px 12px;
  border-radius: 12px;
  line-height: 1.4;
}

/* 暗色主题 */
:root.dark .system-text,
[data-theme='dark'] .system-text {
  background: rgba(255, 255, 255, 0.08);
  color: #888;
}

/* 自己发的消息：在右边，橙色气泡，头像在右侧 */
.msg.me{
  justify-content: flex-end;
}
.msg.me .row{
  flex-direction: row-reverse;
}
.msg.me .meta{
  color: var(--on-message-self);
}

/* 对方发的消息：在左边，灰色气泡，头像在左侧 */
.msg:not(.me){
  justify-content: flex-start;
}

/* 消息元信息：仅群聊显示发送者昵称 */
.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 4px;
}
.meta .sender {
  font-weight: 500;
}

/* 文本内容 */
.text-content {
  word-wrap: break-word;
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: 15px;
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

.reply-preview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 8px 12px 0;
  padding: 6px 10px;
  background: rgba(255, 122, 51, 0.08);
  border-left: 3px solid var(--accent-2, #ff7a33);
  border-radius: 6px;
  font-size: 12px;
  color: var(--muted, #666);
}
.reply-preview-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.reply-sender { font-weight: 600; margin-right: 6px; }
.reply-content { color: var(--text-primary, #333); }
.reply-cancel {
  border: none;
  background: transparent;
  color: #999;
  font-size: 16px;
  cursor: pointer;
  padding: 0 2px;
  line-height: 1;
}
.reply-cancel:hover { color: #333; }
.forward-dialog {
  width: 300px;
  max-height: 60vh;
  display: flex;
  flex-direction: column;
}
.forward-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--surface-border, #eee);
}
.forward-header h3 { margin: 0; font-size: 15px; }
.forward-close {
  border: none;
  background: transparent;
  font-size: 18px;
  cursor: pointer;
  color: #999;
}
.forward-list {
  overflow: auto;
  padding: 8px 0;
}
.forward-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
}
.forward-item:hover { background: rgba(0,0,0,0.04); }
.forward-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  overflow: hidden;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  background: var(--apricot);
  color: var(--on-apricot);
}
.forward-avatar img { width: 100%; height: 100%; object-fit: cover; }
.forward-name { font-size: 14px; }
.forward-empty {
  text-align: center;
  padding: 30px 0;
  color: #999;
  font-size: 13px;
}


.composer{display:flex;padding:12px;border-top:1px solid var(--surface-border);gap:8px}
.jump-latest{align-self:center;border:1px solid var(--surface-border);border-radius:20px;background:var(--panel);color:var(--accent-2);padding:9px 18px;margin:6px;cursor:pointer;font-size:13px}
.composer{align-items:flex-end;padding-bottom:max(12px,env(safe-area-inset-bottom))}
.composer textarea{box-sizing:border-box;min-width:0}
.composer .send{min-height:44px}
.composer .send:disabled{opacity:.45;cursor:default;transform:none;box-shadow:none}
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
  background:var(--action);
  color:var(--on-action);
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
.empty-placeholder{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;background:var(--bg);gap:8px;padding:24px;text-align:center}
.placeholder-illustration{position:relative;width:90px;height:65px;margin-bottom:24px}.placeholder-illustration span:first-child{display:grid;place-items:center;width:90px;height:65px;border:1px solid var(--surface-border-strong);border-radius:26px 26px 26px 5px;background:var(--panel);color:var(--on-apricot);font-size:32px;transform:rotate(-7deg)}
.placeholder-title{font-size:22px;line-height:1.5;font-weight:600;color:var(--text-primary)}
.placeholder-hint{font-size:13px;color:var(--muted);line-height:1.8;max-width:360px}
.placeholder-text{color:var(--muted, #999);font-size:16px}

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
.chat-identity{flex:1;display:flex;align-items:center;gap:12px;min-width:0}.chat-identity>div{min-width:0}.chat-identity small{display:block;font-size:11px;font-weight:400;color:var(--muted);margin-top:4px}.chat-identity small.online{color:var(--sage-ink)}.chat-identity .paper-avatar{width:38px;height:38px;object-fit:cover}
.chat-top-title {
  display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;
}
.chat-top-actions {
  flex:none;position: relative;
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

<style scoped>
.history-feedback{position:absolute;inset:80px 16px auto;text-align:center;color:var(--text-secondary);font-size:13px;background:transparent;border:0;padding:24px;z-index:1}
.chat-wrap{position:relative}
</style>

<style scoped>
.history-sync-feedback{position:absolute;right:12px;top:58px;color:var(--text-secondary);background:var(--panel);border:0;border-radius:8px;padding:3px 6px;font-size:11px;z-index:2}
@media(max-width:767px){.history-sync-feedback{top:6px}}
</style>
