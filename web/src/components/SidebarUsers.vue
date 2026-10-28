<template>
  <div class="users-wrap">
    <div class="users-header">
      <router-link to="/" class="sidebar-brand" :aria-label="$t('product.backHome')"><BrandMark /></router-link>
      <div class="header-actions">
        <button class="friend-requests-btn" :aria-label="$t('sidebar.frTitle')" :title="$t('sidebar.frTitle')" @click="toggleFriendRequests" :class="{hasRequests: friendRequests.length > 0}">
          <AppIcon name="requests" />
          <span v-if="friendRequests.length > 0" class="badge">{{ friendRequests.length }}</span>
        </button>
        <button class="settings-btn" :aria-label="$t('app.settings.title')" :title="$t('app.settings.title')" @click="openSettings"><AppIcon name="settings" /></button>
      </div>
    </div>

    <div class="directory-greeting"><h1>{{ $t('product.greeting') }}</h1><p>{{ $t('product.greetingHint') }}</p></div>

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

    <span v-if="store.state.directoryPhase === 'refreshing'" class="directory-feedback" role="status">{{ $t('sync.refreshing') }}</span>
    <button v-else-if="store.state.directoryPhase === 'stale'" class="directory-feedback" @click="refreshContacts(true)">{{ $t('sync.stale') }}</button>
    <div ref="directoryPanel" id="sidebar-panel" class="directory-panel" role="tabpanel" :aria-labelledby="`sidebar-tab-${tab}`" :aria-busy="friendsLoading" @scroll="directoryScrolled">
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
      <ul v-else class="users-list virtual-contacts" :aria-label="$t(`sidebar.${tab}`)">
        <li role="presentation" aria-hidden="true" :style="{height:`${directoryLayout.offsets[directoryRange.start]}px`}"></li>
        <template v-for="(row,rowOffset) in visibleRows" :key="row.key">
          <li v-if="row.kind==='heading'" class="section-heading"><h3>{{ row.label }} <span>{{ row.count }}</span></h3></li>
          <template v-else>
          <li v-for="u in [row.user]" :key="u.id" class="user-row" role="button" tabindex="0" :aria-posinset="directoryRange.start+rowOffset+1" :aria-setsize="rows.length"
            :data-contact-id="ConversationId.forContact(u).key" :aria-current="store.state.selectedChatId === ConversationId.forContact(u).key ? 'true' : undefined"
            @click="select(u)" @keydown="contactKeydown($event,row.key)" @keydown.enter.prevent="select(u)" @keydown.space.prevent="select(u)" :class="{active: store.state.selectedChatId === ConversationId.forContact(u).key}">
            <div v-if="u.avatarUrl" class="avatar"><img :src="avatarSrc(u)" alt="" loading="lazy" decoding="async" width="44" height="44" /></div>
            <div v-else class="avatar" :class="{ 'group-avatar': u.conversationKind==='group' }">{{ initials(contactName(u)) }}</div>
            <div class="meta">
              <div class="name-row">
                <div class="name">{{ contactName(u) }}</div>
                <span v-if="tab === 'messages' && unreadCount(u) > 0" class="unread-badge">{{ unreadCount(u) > 99 ? '99+' : unreadCount(u) }}</span>
              </div>
              <div class="sub">{{ subtitle(u) }}</div>
            </div>
          </li>
          </template>
        </template>
        <li role="presentation" aria-hidden="true" :style="{height:`${directoryLayout.total-directoryLayout.offsets[directoryRange.end]}px`}"></li>
      </ul>
    </div>
    <button class="discovery-entry" :aria-label="$t('social.discover')" @click="emit('open-discovery')"><AppIcon name="discover" /><span>{{ $t('social.discover') }}<small>{{ $t('polish.discoverySubtitle') }}</small></span><span aria-hidden="true">›</span></button>
  </div>
</template>

<script setup lang="ts">
import {ConversationId} from '../utils/conversationId'
import type { Id, User } from '../types/chat'
import type { FriendRequest } from '../types/api'
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import {contactRows,contactRowHeight} from '../utils/virtualContacts'
import {virtualLayout} from '../utils/virtualLayout'
import { useI18n } from 'vue-i18n'
import { useStore } from '../store'
import api from '../api'
import AppIcon from './AppIcon.vue'
import BrandMark from './BrandMark.vue'
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
const friendsLoading = computed(() => store.state.directoryPhase === 'loading')
const friendsError = computed(() => store.state.directoryPhase === 'error')
const requestsDialog = ref<HTMLElement | null>(null)
useDialog(requestsDialog, () => { showFriendRequests.value = false })

const allUsers = computed(() => sidebarUsers(store.state.users, store.state.groups))

// Vue computed tracks profile changes as well as conversation activity.
const sortedUsers = computed(() => {
  return allUsers.value
    .slice()
    .sort((a, b) => {
      const left=store.state.conversationStates[ConversationId.forContact(a).key],right=store.state.conversationStates[ConversationId.forContact(b).key]
      const timeDiff = (right?.lastIncomingMessageTime || 0) - (left?.lastIncomingMessageTime || 0)
      if (timeDiff !== 0) return timeDiff
      const unreadDiff = (right?.unreadCount || 0) - (left?.unreadCount || 0)
      if (unreadDiff !== 0) return unreadDiff
      return 0 // Stable sort preserves original directory order for ties.
    })
})

const directory = computed(() => contactDirectory(allUsers.value, store.state.accountId, props.query, locale.value))
const sections = computed(() => props.tab === 'contacts'
  ? [{ key: 'friends', label: t('sidebar.friends'), users: directory.value.friends }, { key: 'groups', label: t('sidebar.groups'), users: directory.value.groups }]
  : [{ key: 'messages', label: '', users: sortedUsers.value.filter(user => matchesContact(user, props.query)) }])
const visibleCount = computed(() => sections.value.reduce((total, section) => total + section.users.length, 0))
const directoryPanel=ref<HTMLElement|null>(null),directoryTop=ref(0),directoryHeight=ref(700)
const rows=computed(()=>contactRows(sections.value))
const directoryLayout=computed(()=>virtualLayout(rows.value.map(row=>row.key),new Map(rows.value.map(row=>[row.key,contactRowHeight(row)]))))
const directoryRange=computed(()=>directoryLayout.value.range(directoryTop.value,directoryHeight.value,300))
const visibleRows=computed(()=>rows.value.slice(directoryRange.value.start,directoryRange.value.end))
function directoryScrolled(){const el=directoryPanel.value;if(el){directoryTop.value=el.scrollTop;directoryHeight.value=el.clientHeight}}
let directoryObserver:ResizeObserver|undefined
onMounted(()=>{directoryObserver=new ResizeObserver(directoryScrolled);if(directoryPanel.value)directoryObserver.observe(directoryPanel.value);directoryScrolled()})
onUnmounted(()=>directoryObserver?.disconnect())
watch(()=>[props.tab,props.query],()=>{if(directoryPanel.value)directoryPanel.value.scrollTop=0;directoryTop.value=0})
async function contactKeydown(event:KeyboardEvent,key:string){
  if(!['ArrowDown','ArrowUp','Home','End'].includes(event.key))return
  event.preventDefault()
  const entries=rows.value,current=entries.findIndex(row=>row.key===key),direction=event.key==='ArrowUp'?-1:1
  let index=event.key==='Home'?0:event.key==='End'?entries.length-1:current+direction
  while(index>=0&&index<entries.length&&entries[index].kind!=='user')index+=event.key==='End'?-1:direction
  const row=entries[index],el=directoryPanel.value
  if(!row||row.kind!=='user'||!el)return
  const offset=directoryLayout.value.offsets[index],bottom=directoryLayout.value.offsets[index+1]
  if(offset<el.scrollTop)el.scrollTop=offset
  else if(bottom>el.scrollTop+el.clientHeight)el.scrollTop=bottom-el.clientHeight
  directoryScrolled();await nextTick()
  el.querySelector<HTMLElement>(`[data-contact-id="${ConversationId.forContact(row.user).key}"]`)?.focus({preventScroll:true})
}
const unreadCount = (user: User) => store.state.conversationStates[ConversationId.forContact(user).key]?.unreadCount || 0
function subtitle(user: User) {
  if (props.tab === 'contacts') return user.signature || t(user.conversationKind==='group' ? 'sidebar.groupId' : 'sidebar.accountId', { id: user.id })
  return store.state.conversationStates[ConversationId.forContact(user).key]?.lastMessagePreview || user.signature || user.status || t('polish.startConversation')
}
function onTabKeydown(event: KeyboardEvent, tab: SidebarTab) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const next = event.key === 'Home' ? 'messages' : event.key === 'End' ? 'contacts' : tab === 'messages' ? 'contacts' : 'messages'
  emit('update:tab', next)
  const button = event.currentTarget as HTMLElement
  button.parentElement?.querySelector<HTMLButtonElement>(`#sidebar-tab-${next}`)?.focus()
}

import { refreshContacts } from '../store/contactDirectory'
import { initials, avatarSrc } from '../utils/format'
import { AGENT_USER_ID } from '../constants'
import { toast } from '../utils/toast'

function select(u: User) {
  store.setSelectedChat(ConversationId.forContact(u).key)
  emit('user-selected', u)

  // 点击用户时，主动查询该用户的在线状态（排除AI助手和群组）
  if (u.conversationKind!=='group' && Number(u.id) > 0 && u.id !== AGENT_USER_ID) {
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

function loadFriends() { return refreshContacts() }
</script>

<style scoped>
.users-wrap{position:relative;height:100%;min-height:0;display:flex;flex-direction:column}
.users-header{display:flex;align-items:center;justify-content:space-between;padding:16px 12px 8px;gap:8px}
.sidebar-brand{text-decoration:none}.directory-greeting{padding:19px 20px 22px}.directory-greeting h1{font-size:20px;line-height:1.5;letter-spacing:-.03em;font-weight:600;margin:0 0 5px}.directory-greeting p{margin:0;color:var(--muted);font-size:12px}
.header-actions{display:flex;gap:2px;align-items:center}
.header-actions button{display:grid;place-items:center;width:40px;height:40px;padding:0;color:var(--text-secondary);border-radius:10px;flex:none}
.discovery-entry{display:flex;align-items:center;gap:12px;text-align:left;margin:12px;padding:12px;flex-shrink:0;border:1px solid var(--surface-border);border-radius:12px;background:var(--apricot);color:var(--text-primary);cursor:pointer}
.discovery-entry:hover{background:var(--hover)}
.discovery-entry>svg{color:var(--accent-2);flex:none}
.discovery-entry>span:first-of-type{flex:1;font-weight:600;font-size:14px}
.discovery-entry small{display:block;margin-top:4px;color:var(--text-secondary);font-size:12px;font-weight:400}
.sidebar-tabs{display:flex;gap:6px;padding:0 12px 12px}
.sidebar-tabs button{flex:1;min-height:44px;border:0;border-radius:12px;background:transparent;color:var(--text-secondary);font:inherit;font-size:14px;font-weight:600;cursor:pointer}
.sidebar-tabs button[aria-selected="true"]{background:var(--apricot);color:var(--on-apricot)}

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
.users-list .user-row.active{background:var(--apricot)}
.virtual-contacts{flex:none;overflow:visible}
.virtual-contacts .user-row{height:72px;min-height:0;box-sizing:border-box;margin:0 8px}
.virtual-contacts .section-heading{height:40px;box-sizing:border-box;display:flex;align-items:center}
.virtual-contacts .section-heading h3{margin:0 24px}
.avatar{flex:none;width:44px;height:44px;border-radius:var(--avatar-radius);overflow:hidden;display:flex;align-items:center;justify-content:center;font-weight:700;margin-right:10px;background:var(--apricot);color:var(--on-apricot)}
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

<style scoped>
.directory-feedback{position:absolute;right:14px;bottom:92px;font-size:11px;color:var(--text-secondary);background:var(--panel);border:0;border-radius:8px;padding:3px 6px;z-index:1}
</style>
