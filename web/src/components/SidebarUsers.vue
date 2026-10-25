<template>
  <div class="users-wrap">
    <div class="users-header">
      <div class="title">{{ $t('sidebar.title') }}</div>
      <div class="header-actions">
        <button class="friend-requests-btn" :aria-label="$t('sidebar.frTitle')" :title="$t('sidebar.frTitle')" @click="toggleFriendRequests" :class="{hasRequests: friendRequests.length > 0}">
          <AppIcon name="requests" />
          <span v-if="friendRequests.length > 0" class="badge">{{ friendRequests.length }}</span>
        </button>
        <button class="settings-btn" :aria-label="$t('app.settings.title')" :title="$t('app.settings.title')" @click="openSettings"><AppIcon name="settings" /></button>
        <button class="add" :aria-label="$t('sidebar.addFriendTitle')" :title="$t('sidebar.addFriendTitle')" @click="onAdd"><AppIcon name="add" /></button>
      </div>
    </div>

    <button class="discovery-entry" :aria-label="$t('social.discover')" @click="emit('open-discovery')"><AppIcon name="discover" /><span>{{ $t('social.discover') }}<small>{{ $t('polish.discoverySubtitle') }}</small></span><span aria-hidden="true">›</span></button>

    <div class="search-bar">
      <input
        v-model="searchQuery"
        type="search"
        :placeholder="$t('sidebar.searchPlaceholder')"
        :aria-label="$t('sidebar.searchPlaceholder')"
      />
    </div>

    <!-- 添加好友弹窗 -->
    <div v-if="showAddModal" class="add-friend-modal" @click.self="closeAddModal">
      <div ref="addDialog" class="modal-content" role="dialog" aria-modal="true" :aria-label="$t('sidebar.addFriendTitle')" tabindex="-1">
        <div class="modal-header">
          <h3>{{ $t('sidebar.addFriendTitle') }}</h3>
          <button class="close-btn" @click="closeAddModal">×</button>
        </div>
        <div class="modal-body add-friend-body">
          <input
            ref="addAccountInput"
            v-model="addAccount"
            class="add-friend-input"
            type="text"
            :placeholder="$t('sidebar.addFriendPrompt')"
            :disabled="addingFriend"
            @keyup.enter="submitAddFriend"
            @keyup.esc="closeAddModal"
          />
          <p v-if="addError" class="add-friend-error">{{ addError }}</p>
        </div>
        <div class="modal-footer">
          <button class="cancel-btn" @click="closeAddModal" :disabled="addingFriend">{{ $t('sidebar.addFriendCancel') }}</button>
          <button class="confirm-btn" @click="submitAddFriend" :disabled="addingFriend">
            {{ addingFriend ? $t('sidebar.addFriendSending') : $t('sidebar.addFriendConfirm') }}
          </button>
        </div>
      </div>
    </div>

    <!-- 好友申请弹窗 -->
    <div v-if="showFriendRequests" class="friend-requests-modal" @click.self="toggleFriendRequests">
      <div ref="requestsDialog" class="modal-content" role="dialog" aria-modal="true" :aria-label="$t('sidebar.frTitle')" tabindex="-1">
        <div class="modal-header">
          <h3>{{ $t('sidebar.frTitle') }}</h3>
          <button class="close-btn" @click="toggleFriendRequests">×</button>
        </div>
        <div class="modal-body">
          <div v-if="friendRequests.length === 0" class="empty">{{ $t('sidebar.frEmpty') }}</div>
          <ul v-else class="request-list">
            <li v-for="req in friendRequests" :key="req.id" class="request-item">
              <div class="request-info">
                <div v-if="req.avatarUrl" class="avatar"><img :src="req.avatarUrl" alt="avatar" /></div>
                <div v-else class="avatar">{{ initials(req.username) }}</div>
                <div class="request-meta">
                  <div class="name">{{ req.username }}</div>
                  <div v-if="req.reason" class="desc">{{ req.reason }}</div>
                  <div class="desc">{{ $t('sidebar.frAccount') }}{{ req.id }} {{ $t('sidebar.frAddFriend') }}</div>
                </div>
              </div>
              <div class="request-actions">
                <button class="accept-btn" @click="handleAccept(req.id)">{{ $t('sidebar.frAccept') }}</button>
                <button class="reject-btn" @click="handleReject(req.id)">{{ $t('sidebar.frReject') }}</button>
              </div>
            </li>
          </ul>
        </div>
      </div>
    </div>

    <div v-if="friendsLoading" class="contacts-empty" role="status">{{ $t('social.loading') }}</div>
    <div v-else-if="friendsError" class="contacts-empty" role="alert"><p>{{ $t('social.failed') }}</p><button class="cancel-btn" @click="loadFriends">{{ $t('polish.retry') }}</button></div>
    <div v-else-if="!filteredUsers.length" class="contacts-empty" role="status"><p>{{ $t(searchQuery ? 'polish.noContacts' : 'polish.emptyContacts') }}</p><button class="cancel-btn" @click="searchQuery ? searchQuery = '' : emit('open-discovery')">{{ $t(searchQuery ? 'polish.clearSearch' : 'social.discover') }}</button></div>
    <ul class="users-list" :aria-label="$t('sidebar.title')">
      <li v-for="u in filteredUsers" :key="u.id" role="button" tabindex="0" :aria-current="store.state.selectedChatId === u.id ? 'true' : undefined" @click="select(u)" @keydown.enter.prevent="select(u)" @keydown.space.prevent="select(u)" :class="{active: store.state.selectedChatId === u.id}">
        <div v-if="u.avatarUrl" class="avatar"><img :src="avatarSrc(u)" alt="avatar" /></div>
        <div v-else class="avatar">{{ initials(u.name || u.account || u.username) }}</div>
        <div class="meta">
          <div class="name-row">
            <div class="name">{{ u.name || u.account || u.username }}</div>
            <span v-if="u.preview.unreadCount > 0" class="unread-badge">{{ u.preview.unreadCount > 99 ? '99+' : u.preview.unreadCount }}</span>
          </div>
          <div class="sub">{{ u.signature || u.status || $t('polish.startConversation') }}</div>
        </div>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import type { Id, User } from '../types/chat'
import type { FriendRequest } from '../types/api'
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useStore } from '../store'
import api from '../api'
import AppIcon from './AppIcon.vue'
import { useDialog } from '../composables/useDialog'
import chatSocket from '../services/chatSocket'
import { MSG_TYPE, CONVERSATION_TYPE } from '../proto/constants'

const emit = defineEmits<{ 'user-selected': [user: User]; 'open-settings': []; 'open-discovery': [] }>()

const store = useStore()
const { t } = useI18n()
const showFriendRequests = ref(false)
const friendRequests = ref<FriendRequest[]>([])
const searchQuery = ref('')
const friendsLoading = ref(true), friendsError = ref(false)
const addDialog = ref<HTMLElement | null>(null), requestsDialog = ref<HTMLElement | null>(null)
useDialog(addDialog, closeAddModal)
useDialog(requestsDialog, () => { showFriendRequests.value = false })

// Vue computed tracks profile changes as well as conversation activity.
const sortedUsers = computed(() => {
  return [...store.state.users]
    .map((user, index) => ({
      ...user,
      __index: index,
      preview: store.state.conversationStates[String(user.id)] || {
        lastIncomingMessageTime: 0,
        unreadCount: 0
      }
    }))
    .sort((a, b) => {
      const timeDiff = (b.preview.lastIncomingMessageTime || 0) - (a.preview.lastIncomingMessageTime || 0)
      if (timeDiff !== 0) return timeDiff
      const unreadDiff = (b.preview.unreadCount || 0) - (a.preview.unreadCount || 0)
      if (unreadDiff !== 0) return unreadDiff
      return a.__index - b.__index
    })
})

const filteredUsers = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase()
  if (!keyword) return sortedUsers.value
  return sortedUsers.value.filter(u => {
    const username = String(u.name || u.account || u.username || '').toLowerCase()
    const id = String(u.id || '').toLowerCase()
    return username.includes(keyword) || id.includes(keyword)
  })
})

import { initials, avatarSrc } from '../utils/format'
import { AGENT_USER_ID } from '../constants'
import { toast } from '../utils/toast'

function select(u: User) {
  store.setSelectedChat(u.id)
  emit('user-selected', u)

  // 点击用户时，主动查询该用户的在线状态（排除AI助手和群组）
  if (Number(u.id) > 0 && u.id !== AGENT_USER_ID) {
    chatSocket.sendWrapper({
      type: MSG_TYPE.CHECK,
      check: {
        conversationType: CONVERSATION_TYPE.PRIVATE,
        targetClientId: u.id
      }
    }).catch(err => {
      console.warn('Failed to query online status:', err)
    })
  }
}

// ── 添加好友（应用内弹窗，替代原生 prompt） ──
const showAddModal = ref(false)
const addAccount = ref('')
const addError = ref('')
const addingFriend = ref(false)
const addAccountInput = ref<HTMLInputElement | null>(null)

function onAdd() {
  addAccount.value = ''
  addError.value = ''
  showAddModal.value = true
  // 等弹窗渲染完成后再聚焦输入框
  requestAnimationFrame(() => addAccountInput.value?.focus())
}

function closeAddModal() {
  if (addingFriend.value) return
  showAddModal.value = false
}

async function submitAddFriend() {
  if (addingFriend.value) return
  const account = addAccount.value.trim()
  if (!account) {
    addError.value = t('sidebar.addFriendErrorEmpty')
    return
  }

  addError.value = ''
  addingFriend.value = true
  try {
    const result = await api.addFriend(account)
    if (result.ok) {
      showAddModal.value = false
      addAccount.value = ''
      toast.success(t('sidebar.frSent'))
    } else {
      addError.value = result.message || t('sidebar.addFriendFailed')
    }
  } catch (e) {
    addError.value = t('sidebar.addFriendFailed')
  } finally {
    addingFriend.value = false
  }
}

function openSettings() {
  emit('open-settings')
}

// 好友申请相关功能
function toggleFriendRequests() {
  showFriendRequests.value = !showFriendRequests.value
  if (showFriendRequests.value) {
    loadFriendRequests()
  }
}

async function loadFriendRequests() {
  try {
    const requests = await api.getFriendRequests()
    friendRequests.value = requests
  } catch (e) {
    console.warn('加载好友申请失败:', e)
  }
}

async function handleAccept(userId: Id) {
  const friend = await api.acceptFriend(userId)
  if (friend) {
    toast.success(t('sidebar.frAccepted'))
    // 移除已处理的申请
    friendRequests.value = friendRequests.value.filter(req => req.id !== userId)
    // 增量添加新好友（不重新请求整个列表）
    store.addUser(friend)

    // 自动发送欢迎消息给新好友
    try {
      const welcomeText = '你好！我们已经是好友了，快来聊天吧 👋'
      await chatSocket.sendWrapper({
        type: MSG_TYPE.CHAT,
        chat: {
          targetClientId: Number(userId),
          content: welcomeText,
          timestamp: Date.now(),
          messageId: 'local_' + Date.now()
        }
      })
    } catch (e) {
      console.debug('发送欢迎消息失败:', e)
    }
  } else {
    toast.error(t('sidebar.frAcceptFailed'))
  }
}

async function handleReject(userId: Id) {
  const ok = await api.rejectFriend(userId)
  if (ok) {
    toast.info(t('sidebar.frRejected'))
    // 移除已处理的申请
    friendRequests.value = friendRequests.value.filter(req => req.id !== userId)
  } else {
    toast.error(t('sidebar.frRejectFailed'))
  }
}

/** 监听好友请求轮询更新（WebApp 每 2 分钟轮询后通过自定义事件通知） */
function onFriendRequestsUpdated(e: CustomEvent<FriendRequest[]>) {
  const requests = e.detail
  if (Array.isArray(requests)) {
    const oldCount = friendRequests.value.length
    friendRequests.value = requests
    // 新请求到达时显示通知
    if (requests.length > oldCount && oldCount >= 0) {
      toast.info(`📩 收到 ${requests.length - oldCount} 个新好友请求`)
    }
  }
}

onMounted(() => {
  // 页面加载时检查是否有好友申请和加载好友列表
  // 只有在token已经通过有效性验证后才调用，避免过期token导致的错误
  if (store.state.token && store.state.loginValid) {
    loadFriendRequests()
    loadFriends()
  }
  // 监听轮询更新
  window.addEventListener('friend-requests-updated', onFriendRequestsUpdated as EventListener)
})

onUnmounted(() => {
  window.removeEventListener('friend-requests-updated', onFriendRequestsUpdated as EventListener)
})

// 监听登录有效性变化，登录成功后自动加载数据
watch(() => store.state.loginValid, (newVal) => {
  if (newVal && store.state.token) {
    loadFriendRequests()
    loadFriends()
  }
})

async function loadFriends() {
  friendsLoading.value = true; friendsError.value = false
  try {
    const friends = await api.getFriends()
    store.setUsers(friends)
  } catch (e) {
    friendsError.value = true
    console.warn('加载好友列表失败:', e)
  } finally { friendsLoading.value = false }
}
</script>

<style scoped>
.users-wrap{height:100%;display:flex;flex-direction:column}
.users-header{display:flex;align-items:center;justify-content:space-between;padding:16px 12px 8px;gap:8px}
.users-header .title{font-size:19px;font-weight:650;white-space:nowrap}
.header-actions{display:flex;gap:2px;align-items:center}
.header-actions button{display:grid;place-items:center;width:40px;height:40px;padding:0;color:var(--text-secondary);border-radius:10px;flex:none}
.discovery-entry{display:flex;align-items:center;gap:12px;text-align:left;margin:8px 12px 16px;padding:12px;border:1px solid var(--surface-border);border-radius:12px;background:var(--panel);color:var(--text-primary);cursor:pointer}
.discovery-entry:hover{background:var(--hover)}
.discovery-entry>svg{color:var(--accent-2);flex:none}
.discovery-entry>span:first-of-type{flex:1;font-weight:600;font-size:14px}
.discovery-entry small{display:block;margin-top:4px;color:var(--text-secondary);font-size:12px;font-weight:400}
.contacts-empty{padding:36px 20px;text-align:center;color:var(--text-secondary);font-size:14px;line-height:1.7}
.friend-requests-btn{position:relative;background:transparent;border:0;font-size:18px;cursor:pointer;padding:4px 8px;border-radius:4px}
.friend-requests-btn:hover{background:var(--hover)}
.friend-requests-btn.hasRequests{color:#ff7a33}
.badge{position:absolute;top:-2px;right:-2px;background:#ff4757;color:white;font-size:10px;padding:1px 4px;border-radius:8px;min-width:12px;text-align:center}
.settings-btn, .add{background:transparent;border:0;font-size:18px;cursor:pointer;padding:4px 8px;border-radius:4px}
.settings-btn:hover, .add:hover{background:var(--hover)}
.users-list{list-style:none;padding:0;margin:0;overflow:auto;flex:1}
.users-list li{display:flex;align-items:center;padding:12px;margin:2px 8px;border-radius:10px;cursor:pointer;min-height:44px}
.users-list li:hover{background:var(--hover)}
.users-list li.active{background:rgba(255,122,51,0.08)}
.avatar{width:40px;height:40px;border-radius:8px;overflow:hidden;display:flex;align-items:center;justify-content:center;font-weight:700;margin-right:10px;background:var(--surface-2, #f0f0f0)}
.avatar img{width:100%;height:100%;object-fit:cover}
.meta{flex:1;min-width:0}
.name-row{display:flex;align-items:center;justify-content:space-between;gap:8px}
.meta .name{font-weight:600;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.meta .sub{font-size:12px;color:var(--muted);margin-top:5px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.unread-badge{background:#ff4757;color:white;font-size:10px;padding:1px 6px;border-radius:999px;min-width:18px;text-align:center;line-height:16px;flex:none}

/* 搜索框样式 */
.search-bar{padding: 0 12px 12px;}
.search-bar input{
  width: 100%;
  padding: 10px 16px 10px 40px;
  border: 1px solid var(--input-border, #e8e8e8);
  border-radius: 24px;
  font-size: 14px;
  background: var(--panel) url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="%23999" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"></circle><path d="m21 21-4.35-4.35"></path></svg>') no-repeat 14px center;
  color: var(--text-primary, #222);
  transition: all 0.3s ease;
  box-sizing: border-box;
}
.search-bar input:focus{
  outline: none;
  border-color: #ff7a33;
  background-color: var(--panel);
  box-shadow: 0 0 0 3px rgba(255, 122, 51, 0.1);
}
.search-bar input:hover{
  border-color: var(--input-border, #d0d0d0);
  background-color: var(--panel);
}
.search-bar input::placeholder{
  color: var(--input-placeholder, #bbb);
}

/* 添加好友弹窗 */
.add-friend-modal{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;z-index:1001}
.add-friend-body{padding:16px 20px;display:flex;flex-direction:column;gap:8px}
.add-friend-input{width:100%;box-sizing:border-box;padding:10px 12px;border:1px solid var(--input-border, #e8e8e8);border-radius:8px;font-size:14px;background:var(--input-bg, #fafafa);color:var(--text-primary, #222);outline:none;transition:border-color .2s ease, box-shadow .2s ease}
.add-friend-input:focus{border-color:var(--accent-2, #ff7a33);box-shadow:0 0 0 3px rgba(255,122,51,0.1)}
.add-friend-input:disabled{opacity:.6}
.add-friend-error{margin:0;color:#ff4757;font-size:13px}
.modal-footer{display:flex;justify-content:flex-end;gap:10px;padding:12px 20px 16px;border-top:1px solid var(--surface-border, #eee)}
.cancel-btn{background:transparent;border:1px solid var(--surface-border, #e0e0e0);color:var(--text-secondary, #666);padding:7px 16px;border-radius:6px;cursor:pointer;font-size:13px}
.cancel-btn:hover{background:var(--hover)}
.cancel-btn:disabled{opacity:.5;cursor:not-allowed}
.confirm-btn{background:var(--accent-2, #ff7a33);color:#fff;border:0;padding:7px 16px;border-radius:6px;cursor:pointer;font-size:13px;font-weight:600}
.confirm-btn:hover{filter:brightness(1.08)}
.confirm-btn:disabled{opacity:.6;cursor:not-allowed}

/* 好友申请弹窗 */
.friend-requests-modal{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;z-index:1000}
.modal-content{background:var(--panel, #fff);border-radius:12px;width:400px;max-width:90%;box-shadow:0 4px 20px rgba(0,0,0,0.15)}
.modal-header{display:flex;align-items:center;justify-content:space-between;padding:16px 20px;border-bottom:1px solid var(--surface-border, #eee)}
.modal-header h3{margin:0;font-size:16px;color:var(--text-primary, #222)}
.close-btn{background:transparent;border:0;font-size:20px;cursor:pointer;color:var(--muted, #999);padding:0 4px}
.close-btn:hover{color:var(--text-primary, #333)}
.modal-body{padding:12px 0}
.empty{text-align:center;padding:40px 20px;color:var(--muted, #999);font-size:14px}
.request-list{list-style:none;padding:0;margin:0}
.request-item{display:flex;align-items:center;justify-content:space-between;padding:12px 20px;border-bottom:1px solid var(--surface-border, #f5f5f5)}
.request-item:last-child{border-bottom:0}
.request-info{display:flex;align-items:center}
.request-meta .name{font-weight:600;font-size:14px}
.request-meta .desc{font-size:12px;color:var(--text-secondary, #666);margin-top:2px}
.request-actions{display:flex;gap:8px}
.accept-btn{background:#52c41a;color:white;border:0;padding:6px 12px;border-radius:4px;cursor:pointer;font-size:12px}
.accept-btn:hover{background:#43a047}
.reject-btn{background:var(--bubble-bg, #f5f5f5);color:var(--text-secondary, #666);border:0;padding:6px 12px;border-radius:4px;cursor:pointer;font-size:12px}
.reject-btn:hover{background:var(--hover)}
</style>
