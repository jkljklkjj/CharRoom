<template>
  <div class="users-wrap">
    <div class="users-header">
      <div class="title">{{ $t('app.brand') }}</div>
      <div class="header-actions">
        <button class="friend-requests-btn" :aria-label="$t('sidebar.frTitle')" :title="$t('sidebar.frTitle')" @click="toggleFriendRequests" :class="{hasRequests: friendRequests.length > 0}">
          <AppIcon name="requests" />
          <span v-if="friendRequests.length > 0" class="badge">{{ friendRequests.length }}</span>
        </button>
        <button class="settings-btn" :aria-label="$t('app.settings.title')" :title="$t('app.settings.title')" @click="openSettings"><AppIcon name="settings" /></button>
      </div>
    </div>

    <button class="discovery-entry" :aria-label="$t('social.discover')" @click="emit('open-discovery')"><AppIcon name="discover" /><span>{{ $t('social.discover') }}<small>{{ $t('polish.discoverySubtitle') }}</small></span><span aria-hidden="true">›</span></button>

    <div class="sidebar-tabs" role="tablist" :aria-label="$t('sidebar.navigation')">
      <button v-for="item in tabs" :key="item" :id="`sidebar-tab-${item}`" role="tab"
        :aria-selected="tab === item" :tabindex="tab === item ? 0 : -1" aria-controls="sidebar-panel"
        @click="emit('update:tab', item)" @keydown="onTabKeydown($event, item)">{{ $t(`sidebar.${item}`) }}</button>
    </div>
    <div class="search-bar">
      <input type="search" :value="query" :aria-label="$t(tab === 'contacts' ? 'sidebar.searchContacts' : 'sidebar.searchMessages')"
        :placeholder="$t(tab === 'contacts' ? 'sidebar.searchContacts' : 'sidebar.searchMessages')" @input="emit('update:query', ($event.target as HTMLInputElement).value)" />
      <button v-if="query" class="clear-search" :aria-label="$t('sidebar.clearSearch')" @click="emit('update:query', '')">×</button>
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

    <div id="sidebar-panel" class="directory-panel" role="tabpanel" :aria-labelledby="`sidebar-tab-${tab}`" :aria-busy="friendsLoading">
      <!-- 加载态用骨架行占位：与真实联系人行同尺寸，不出文字，避免加载前后布局跳变 -->
      <ul v-if="friendsLoading && !visibleCount" class="users-list skeleton-list" role="status" :aria-label="$t('social.loading')">
        <li v-for="n in 7" :key="n" class="user-row skeleton-row" aria-hidden="true">
          <div class="avatar skeleton-avatar"></div>
          <div class="meta">
            <div class="skeleton-line skeleton-name"></div>
            <div class="skeleton-line skeleton-sub"></div>
          </div>
        </li>
      </ul>
      <div v-else-if="friendsError && !visibleCount" class="contacts-empty" role="alert"><p>{{ $t('social.failed') }}</p><button class="cancel-btn" @click="loadFriends">{{ $t('polish.retry') }}</button></div>
      <div v-else-if="!visibleCount" class="contacts-empty" role="status">
        <p>{{ $t(query.trim() ? 'sidebar.noMatches' : 'polish.emptyContacts') }}</p>
        <button class="cancel-btn" @click="query.trim() ? emit('update:query', '') : emit('open-discovery')">{{ $t(query.trim() ? 'sidebar.clearSearch' : 'social.discover') }}</button>
      </div>
      <ul v-else class="users-list" :aria-label="$t(`sidebar.${tab}`)">
        <template v-for="section in sections" :key="section.key">
          <li v-if="section.label && section.users.length" class="section-heading"><h3>{{ section.label }} <span>{{ section.users.length }}</span></h3></li>
          <li v-for="u in section.users" :key="u.id" class="user-row" role="button" tabindex="0"
            :data-contact-id="u.id" :aria-current="String(store.state.selectedChatId) === String(u.id) ? 'true' : undefined"
            @click="select(u)" @keydown.enter.prevent="select(u)" @keydown.space.prevent="select(u)" :class="{active: String(store.state.selectedChatId) === String(u.id)}">
            <div v-if="u.avatarUrl" class="avatar"><img :src="avatarSrc(u)" alt="" /></div>
            <div v-else class="avatar" :class="{ 'group-avatar': Number(u.id) < 0 }">{{ initials(contactName(u)) }}</div>
            <div class="meta">
              <div class="name-row">
                <div class="name">{{ contactName(u) }}</div>
                <span v-if="tab === 'messages' && unreadCount(u) > 0" class="unread-badge">{{ unreadCount(u) > 99 ? '99+' : unreadCount(u) }}</span>
              </div>
              <div class="sub">{{ subtitle(u) }}</div>
            </div>
          </li>
        </template>
      </ul>
    </div>
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

import { contactDirectory, contactName, matchesContact, sidebarUsers, chatTargetId, type SidebarTab } from '../utils/contactDirectory'

const props = defineProps<{ tab: SidebarTab; query: string }>()
const emit = defineEmits<{ 'user-selected': [user: User]; 'open-settings': []; 'open-discovery': []; 'update:tab': [tab: SidebarTab]; 'update:query': [query: string] }>()
const tabs: SidebarTab[] = ['messages', 'contacts']

const store = useStore()
const { t, locale } = useI18n()
const showFriendRequests = ref(false)
const friendRequests = ref<FriendRequest[]>([])
const friendsLoading = ref(true), friendsError = ref(false)
const requestsDialog = ref<HTMLElement | null>(null)
useDialog(requestsDialog, () => { showFriendRequests.value = false })

const allUsers = computed(() => sidebarUsers(store.state.users, store.state.groups))

// Vue computed tracks profile changes as well as conversation activity.
const sortedUsers = computed(() => {
  return allUsers.value
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

const directory = computed(() => contactDirectory(allUsers.value, store.state.accountId, props.query, locale.value))
const sections = computed(() => props.tab === 'contacts'
  ? [{ key: 'friends', label: t('sidebar.friends'), users: directory.value.friends }, { key: 'groups', label: t('sidebar.groups'), users: directory.value.groups }]
  : [{ key: 'messages', label: '', users: sortedUsers.value.filter(user => matchesContact(user, props.query)) }])
const visibleCount = computed(() => sections.value.reduce((total, section) => total + section.users.length, 0))
const unreadCount = (user: User) => store.state.conversationStates[String(user.id)]?.unreadCount || 0
function subtitle(user: User) {
  if (props.tab === 'contacts') return user.signature || t(Number(user.id) < 0 ? 'sidebar.groupId' : 'sidebar.accountId', { id: chatTargetId(user.id) })
  return store.state.conversationStates[String(user.id)]?.lastMessagePreview || user.signature || user.status || t('polish.startConversation')
}
function onTabKeydown(event: KeyboardEvent, tab: SidebarTab) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const next = event.key === 'Home' ? 'messages' : event.key === 'End' ? 'contacts' : tab === 'messages' ? 'contacts' : 'messages'
  emit('update:tab', next)
  const button = event.currentTarget as HTMLElement
  button.parentElement?.querySelector<HTMLButtonElement>(`#sidebar-tab-${next}`)?.focus()
}

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
.users-wrap{height:100%;min-height:0;display:flex;flex-direction:column}
.users-header{display:flex;align-items:center;justify-content:space-between;padding:16px 12px 8px;gap:8px}
.users-header .title{font-size:19px;font-weight:650;white-space:nowrap}
.header-actions{display:flex;gap:2px;align-items:center}
.header-actions button{display:grid;place-items:center;width:40px;height:40px;padding:0;color:var(--text-secondary);border-radius:10px;flex:none}
.discovery-entry{display:flex;align-items:center;gap:12px;text-align:left;margin:8px 12px 16px;padding:12px;border:1px solid var(--surface-border);border-radius:12px;background:var(--panel);color:var(--text-primary);cursor:pointer}
.discovery-entry:hover{background:var(--hover)}
.discovery-entry>svg{color:var(--accent-2);flex:none}
.discovery-entry>span:first-of-type{flex:1;font-weight:600;font-size:14px}
.discovery-entry small{display:block;margin-top:4px;color:var(--text-secondary);font-size:12px;font-weight:400}
.sidebar-tabs{display:flex;gap:6px;padding:0 12px 12px}
.sidebar-tabs button{flex:1;min-height:44px;border:0;border-radius:12px;background:transparent;color:var(--text-secondary);font:inherit;font-size:14px;font-weight:600;cursor:pointer}
.sidebar-tabs button[aria-selected="true"]{background:#ffead8;color:#98451e}
[data-theme="dark"] .sidebar-tabs button[aria-selected="true"]{background:#493024;color:#ffb785}
.sidebar-tabs button:focus-visible,.user-row:focus-visible{outline:2px solid var(--accent-2);outline-offset:-2px}
.search-bar{display:flex;align-items:center;margin:0 12px 10px;border:1px solid var(--surface-border);background:var(--panel);border-radius:12px;min-height:44px}
.search-bar:focus-within{border-color:var(--accent-2)}
.search-bar input{width:100%;min-width:0;background:transparent;color:var(--text-primary);border:0;outline:0;padding:12px;font:inherit;font-size:13px}
.search-bar input::-webkit-search-cancel-button{display:none}
.clear-search{border:0;background:transparent;color:var(--text-secondary);font-size:20px;min-width:44px;min-height:44px;cursor:pointer}
.directory-panel{display:flex;flex-direction:column;min-height:0;flex:1;overflow:auto;overscroll-behavior:contain}
.section-heading h3{font-size:12px;font-weight:500;color:var(--text-secondary);margin:16px 24px 6px;display:flex;gap:8px}
.section-heading span{font-variant-numeric:tabular-nums}
.group-avatar{color:var(--accent-2)}
.contacts-empty{padding:36px 20px;text-align:center;color:var(--text-secondary);font-size:14px;line-height:1.7}
/* 加载骨架：尺寸与 .user-row 一致，刷新已有数据时不闪 */
.skeleton-list{overflow:hidden}
.skeleton-row{pointer-events:none}
.skeleton-avatar,.skeleton-line{background:var(--surface-2,#f0f0f0);animation:sidebar-skeleton 1.2s ease-in-out infinite alternate}
.skeleton-line{height:12px;border-radius:6px}
.skeleton-name{width:44%}
.skeleton-sub{width:70%;height:11px;margin-top:8px}
@keyframes sidebar-skeleton{from{opacity:1}to{opacity:.55}}
.friend-requests-btn{position:relative;background:transparent;border:0;font-size:18px;cursor:pointer;padding:4px 8px;border-radius:4px}
.friend-requests-btn:hover{background:var(--hover)}
.friend-requests-btn.hasRequests{color:#ff7a33}
.badge{position:absolute;top:-2px;right:-2px;background:#ff4757;color:white;font-size:10px;padding:1px 4px;border-radius:8px;min-width:12px;text-align:center}
.settings-btn{background:transparent;border:0;font-size:18px;cursor:pointer;padding:4px 8px;border-radius:4px}
.settings-btn:hover{background:var(--hover)}
.users-list{list-style:none;padding:0;margin:0;overflow:auto;flex:1;overscroll-behavior:contain}
.users-list .user-row{display:flex;align-items:center;padding:12px;margin:2px 8px;border-radius:10px;cursor:pointer;min-height:44px}
.users-list .user-row:hover{background:var(--hover)}
.users-list .user-row.active{background:rgba(255,122,51,0.08)}
.avatar{flex:none;width:40px;height:40px;border-radius:8px;overflow:hidden;display:flex;align-items:center;justify-content:center;font-weight:700;margin-right:10px;background:var(--surface-2, #f0f0f0)}
.avatar img{width:100%;height:100%;object-fit:cover}
.meta{flex:1;min-width:0}
.name-row{display:flex;align-items:center;justify-content:space-between;gap:8px}
.meta .name{font-weight:600;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.meta .sub{font-size:12px;color:var(--muted);margin-top:5px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.unread-badge{background:#ff4757;color:white;font-size:10px;padding:1px 6px;border-radius:999px;min-width:18px;text-align:center;line-height:16px;flex:none}

.cancel-btn{background:transparent;border:1px solid var(--surface-border, #e0e0e0);color:var(--text-secondary, #666);padding:7px 16px;border-radius:6px;cursor:pointer;font-size:13px}
.cancel-btn:hover{background:var(--hover)}
.cancel-btn:disabled{opacity:.5;cursor:not-allowed}

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
