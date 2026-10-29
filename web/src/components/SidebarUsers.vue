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

    <div class="directory-greeting"><h1>{{ $t(`product.greetings.${greetingIndex}.title`) }}</h1><p>{{ $t(`product.greetings.${greetingIndex}.hint`) }}</p></div>

    <div class="sidebar-tabs" role="tablist" :aria-label="$t('sidebar.navigation')">
      <button v-for="item in tabs" :key="item" :id="`sidebar-tab-${item}`" role="tab"
        :aria-selected="tab === item" :tabindex="tab === item ? 0 : -1" aria-controls="sidebar-panel"
        @click="emit('update:tab', item)" @keydown="onTabKeydown($event, item)">{{ $t(`sidebar.${item}`) }}</button>
    </div>
    <form class="search-bar" role="search" @submit.prevent="submitSearch">
      <input ref="searchInput" type="search" enterkeyhint="search" :value="query" :aria-label="$t(tab === 'contacts' ? 'sidebar.searchContacts' : 'sidebar.searchMessages')"
        @compositionstart="searchComposing = true" @compositionend="finishSearchComposition"
        :placeholder="$t(tab === 'contacts' ? 'sidebar.searchContacts' : 'sidebar.searchMessages')" @input="emit('update:query', ($event.target as HTMLInputElement).value)" />
      <button v-if="query" type="button" class="clear-search" :aria-label="$t('sidebar.clearSearch')" @click="emit('update:query', '')">×</button>
      <button type="submit" class="submit-search" :aria-label="$t('polish.searchAction')"><AppIcon name="search" /></button>
    </form>
    <!-- 联系人搜索会同时找"还不是好友的人"：找到后点一下即可发申请，不需要额外的加好友入口 -->
    <span v-if="remoteSearching" class="user-search-feedback" role="status">{{ $t('sidebar.userSearchSearching') }}</span>
    <span v-else-if="searchCooldown > 0" class="user-search-feedback" role="status">{{ $t('sidebar.userSearchCooldown', { seconds: searchCooldown }) }}</span>
    <span v-else-if="remoteError" class="user-search-feedback error" role="alert">{{ remoteError }}</span>

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

    <!-- 搜索结果名片：先看资料，再在名片里决定是否加好友 -->
    <div v-if="previewUser" class="profile-preview-overlay" @click.self="closePreview">
      <div ref="previewDialog" class="profile-preview" role="dialog" aria-modal="true" :aria-label="previewUser.username" tabindex="-1">
        <button class="profile-preview-close" :aria-label="$t('social.close')" @click="closePreview">×</button>
        <SocialProfileCard :user-id="String(previewUser.id)" @chat="onPreviewChat" @blocked="closePreview" @changed="onPreviewChanged" />
      </div>
    </div>

    <span v-if="store.state.directoryPhase === 'refreshing'" class="directory-feedback" role="status">{{ $t('sync.refreshing') }}</span>
    <button v-else-if="store.state.directoryPhase === 'stale'" class="directory-feedback" @click="refreshContacts(true)">{{ $t('sync.stale') }}</button>
    <div ref="directoryPanel" id="sidebar-panel" class="directory-panel" role="tabpanel" :aria-labelledby="`sidebar-tab-${tab}`" :aria-busy="friendsLoading" @scroll="onDirectoryScroll">
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
        <p>{{ $t(query.trim() ? 'sidebar.noMatches' : tab === 'messages' && allUsers.length ? 'sidebar.emptyConversations' : 'polish.emptyContacts') }}</p>
        <button class="cancel-btn" @click="query.trim() ? emit('update:query', '') : tab === 'messages' && allUsers.length ? emit('update:tab', 'contacts') : emit('open-discovery')">{{ $t(query.trim() ? 'sidebar.clearSearch' : tab === 'messages' && allUsers.length ? 'sidebar.openContacts' : 'social.discover') }}</button>
      </div>
      <ul v-else class="users-list virtual-contacts" :aria-label="$t(`sidebar.${tab}`)">
        <li role="presentation" aria-hidden="true" :style="{height:`${directoryLayout.offsets[directoryRange.start]}px`}"></li>
        <template v-for="(row,rowOffset) in visibleRows" :key="row.key">
          <li v-if="row.kind==='heading'" class="section-heading"><h3>{{ row.label }} <span>{{ row.count }}</span></h3></li>
          <template v-else>
          <li v-for="u in [row.user]" :key="u.id" class="user-row" role="button" tabindex="0" :aria-posinset="directoryRange.start+rowOffset+1" :aria-setsize="rows.length"
            :aria-haspopup="tab === 'messages' ? 'menu' : undefined"
            :data-contact-id="ConversationId.forContact(u).key" :aria-current="store.state.selectedChatId === ConversationId.forContact(u).key ? 'true' : undefined"
            @click="select(u)" @keydown="contactKeydown($event,row.key,u)" @keydown.enter.prevent="select(u)" @keydown.space.prevent="select(u)" :class="{active: store.state.selectedChatId === ConversationId.forContact(u).key, pinned: isPinned(u)}"
            @pointerdown="pressStart($event,u)" @pointermove="pressMove" @pointerup="pressEnd" @pointercancel="pressEnd" @pointerleave="pressEnd"
            @contextmenu.prevent="openConversationMenu($event,u)">
            <div v-if="u.avatarUrl" class="avatar"><img :src="avatarSrc(u)" alt="" loading="lazy" decoding="async" width="44" height="44" /></div>
            <div v-else class="avatar" :class="{ 'group-avatar': u.conversationKind==='group' }">{{ initials(contactName(u)) }}</div>
            <div class="meta">
              <div class="name-row">
                <div class="name">{{ contactName(u) }}</div>
                <span v-if="isPinned(u)" class="pin-mark" role="img" :aria-label="$t('sidebar.pinned')"><AppIcon name="pin" /></span>
                <span v-if="tab === 'messages' && unreadCount(u) > 0" class="unread-badge">{{ unreadCount(u) > 99 ? '99+' : unreadCount(u) }}</span>
              </div>
              <div class="sub">{{ subtitle(u) }}</div>
            </div>
          </li>
          </template>
        </template>
        <li role="presentation" aria-hidden="true" :style="{height:`${directoryLayout.total-directoryLayout.offsets[directoryRange.end]}px`}"></li>
      </ul>
      <button v-if="remoteHasMore" type="button" class="remote-more" :disabled="remoteSearching || searchCooldown > 0" @click="loadMoreRemote">{{ $t('sidebar.userSearchMore') }}</button>
    </div>
    <button class="discovery-entry" :aria-label="$t('social.discover')" @click="emit('open-discovery')"><AppIcon name="discover" /><span>{{ $t('social.discover') }}<small>{{ $t('polish.discoverySubtitle') }}</small></span><span aria-hidden="true">›</span></button>
    <div v-if="conversationMenu" ref="conversationMenuEl" class="conversation-menu" role="menu" :aria-label="$t('sidebar.conversationActions')"
      :style="{ left: `${conversationMenu.x}px`, top: `${conversationMenu.y}px` }" @contextmenu.prevent @pointerdown.stop>
      <button type="button" role="menuitem" @click="togglePinnedFromMenu">{{ $t(isPinned(conversationMenu.user) ? 'sidebar.unpin' : 'sidebar.pin') }}</button>
      <button type="button" role="menuitem" class="danger" :title="$t('sidebar.deleteConversationHint')" @click="deleteFromMenu">{{ $t('sidebar.deleteConversation') }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ConversationId} from '../utils/conversationId'
import type { Id, User } from '../types/chat'
import type { FriendRequest } from '../types/api'
import type { UserSearchHit } from '../api'
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import {contactRows,contactRowHeight} from '../utils/virtualContacts'
import {virtualLayout} from '../utils/virtualLayout'
import { useI18n } from 'vue-i18n'
import { useStore } from '../store'
import api, { UserSearchRateLimitError, USER_SEARCH_PAGE_SIZE, USER_SEARCH_MAX_PAGE } from '../api'
import AppIcon from './AppIcon.vue'
import SocialProfileCard from './SocialProfileCard.vue'
import BrandMark from './BrandMark.vue'
import { useDialog } from '../composables/useDialog'
import chatSocket from '../services/chatSocket'
import { MSG_TYPE, CONVERSATION_TYPE } from '../proto/constants'

import { contactDirectory, contactName, filterContacts, filterContactDirectory, sortConversationUsers, sidebarUsers, chatTargetId, type SidebarTab } from '../utils/contactDirectory'
import { pickDailyGreeting } from '../utils/dailyGreeting'
import { sessionEpoch } from '../services/sessionEpoch'
import { contactSearchBudget } from '../services/contactSearchBudget'

const props = defineProps<{ tab: SidebarTab; query: string }>()
const emit = defineEmits<{ 'user-selected': [user: User]; 'open-settings': []; 'open-discovery': []; 'update:tab': [tab: SidebarTab]; 'update:query': [query: string] }>()
const tabs: SidebarTab[] = ['messages', 'contacts']

const store = useStore()
const { t, locale } = useI18n()
// 顶部问候语：10 条文案每天随机一条，三天内不重复（localStorage 记录，当天刷新不变）
const greetingIndex = ref(0)
onMounted(() => { greetingIndex.value = pickDailyGreeting() })
const showFriendRequests = ref(false)
const friendRequests = ref<FriendRequest[]>([])
const friendsLoading = computed(() => store.state.directoryPhase === 'loading')
const friendsError = computed(() => store.state.directoryPhase === 'error')
const requestsDialog = ref<HTMLElement | null>(null)
useDialog(requestsDialog, () => { showFriendRequests.value = false })

const allUsers = computed(() => sidebarUsers(store.state.users, store.state.groups, store.state.accountId))

// 消息页不展示被“删除”的会话；联系人页仍能看到好友，重新打开后历史还在
const conversationUsers = computed(() => {
  if (props.tab !== 'messages') return allUsers.value
  const hidden = new Set(store.state.hiddenConversations)
  return allUsers.value.filter(user => !hidden.has(ConversationId.forContact(user).key))
})
const isPinnedKey = (key: string) => store.state.pinnedConversations.includes(key)
const isPinned = (user: User) => isPinnedKey(ConversationId.forContact(user).key)
const sortedUsers = computed(() => sortConversationUsers(conversationUsers.value, key => store.state.conversationStates[key], isPinnedKey))

const sortedDirectory = computed(() => contactDirectory(allUsers.value, store.state.accountId, locale.value))
const directory = computed(() => filterContactDirectory(sortedDirectory.value, props.query))

// 服务端按昵称分页搜人：结果并入联系人列表，滚动到底部继续翻页，点击直接发好友申请
const remoteHits = ref<UserSearchHit[]>([])
const remotePage = ref(0)
const remoteHasMore = ref(false)
const remoteSearching = ref(false)
const remoteError = ref('')
const searchInput = ref<HTMLInputElement | null>(null)
const searchComposing = ref(false)
let remoteController: AbortController | undefined
let remoteTimer: ReturnType<typeof setTimeout> | undefined
let remoteKeyword = ''
const searchRetryAt = ref(0), searchClock = ref(Date.now())
const searchCooldown = computed(() => Math.max(0, Math.ceil((searchRetryAt.value - searchClock.value) / 1000)))
let cooldownTimer: ReturnType<typeof setInterval> | undefined
function showSearchCooldown(millis: number) {
  searchClock.value = Date.now(); searchRetryAt.value = searchClock.value + millis
  clearInterval(cooldownTimer)
  cooldownTimer = setInterval(() => {
    searchClock.value = Date.now()
    if (!searchCooldown.value) clearInterval(cooldownTimer)
  }, 1000)
}
const contactKeys = computed(() => new Set(allUsers.value.map(u => ConversationId.forContact(u).key)))
const remoteState = computed(() => new Map(remoteHits.value.map(hit => [String(hit.id), hit])))
const remoteUsers = computed<User[]>(() => remoteHits.value
  .filter(hit => !contactKeys.value.has(ConversationId.forContact({ id: hit.id } as User).key))
  .map(hit => ({ id: hit.id, username: hit.username, avatarUrl: hit.avatarUrl, signature: hit.signature })))
const sections = computed(() => {
  if (props.tab !== 'contacts') return [{ key: 'messages', label: '', users: filterContacts(sortedUsers.value, props.query) }]
  const rows = [
    { key: 'friends', label: t('sidebar.friends'), users: directory.value.friends },
    { key: 'groups', label: t('sidebar.groups'), users: directory.value.groups }
  ]
  if (remoteUsers.value.length) rows.push({ key: 'remote', label: t('sidebar.userSearchRemote'), users: remoteUsers.value })
  return rows
})

// ── 会话右键/长按菜单 ───────────────────────────
interface ConversationMenuState { user: User; x: number; y: number }
const conversationMenu = ref<ConversationMenuState | null>(null)
const conversationMenuEl = ref<HTMLElement | null>(null)
let pressTimer: ReturnType<typeof setTimeout> | undefined
let pressOrigin: { x: number; y: number } | null = null
let suppressClickUntil = 0
let menuTrigger: HTMLElement | null = null
const canUseConversationMenu = () => props.tab === 'messages'

function menuPosition(x: number, y: number) {
  const width = 176, height = 112, pad = 8
  return { x: Math.max(pad, Math.min(x, window.innerWidth - width - pad)),
    y: Math.max(pad, Math.min(y, window.innerHeight - height - pad)) }
}
function openConversationMenuAt(x: number, y: number, user: User, trigger: HTMLElement | null = null) {
  if (!canUseConversationMenu()) return
  pressEnd()
  menuTrigger = trigger
  conversationMenu.value = { user, ...menuPosition(x, y) }
  void nextTick(() => conversationMenuEl.value?.querySelector<HTMLButtonElement>('button')?.focus())
}
function openConversationMenu(event: MouseEvent, user: User) {
  if (!canUseConversationMenu()) return
  // 触屏长按可能先触发原生 contextmenu；同样抑制随后可能到达的 click
  suppressClickUntil = Date.now() + 800
  openConversationMenuAt(event.clientX, event.clientY, user, event.currentTarget as HTMLElement)
}
function closeConversationMenu(restoreFocus = false) {
  if (!conversationMenu.value) return
  conversationMenu.value = null
  if (restoreFocus && menuTrigger?.isConnected) menuTrigger.focus({ preventScroll: true })
  menuTrigger = null
}
/** 触屏/手写笔长按 500ms 打开；鼠标走右键，避免按住拖动时误触。 */
function pressStart(event: PointerEvent, user: User) {
  if (!canUseConversationMenu() || event.pointerType === 'mouse' || event.button !== 0) return
  pressEnd()
  pressOrigin = { x: event.clientX, y: event.clientY }
  pressTimer = setTimeout(() => {
    pressTimer = undefined
    if (!pressOrigin) return
    suppressClickUntil = Date.now() + 800
    openConversationMenuAt(pressOrigin.x, pressOrigin.y, user)
    pressOrigin = null
  }, 500)
}
function pressMove(event: PointerEvent) {
  if (!pressOrigin) return
  if (Math.hypot(event.clientX - pressOrigin.x, event.clientY - pressOrigin.y) > 12) pressEnd()
}
function pressEnd() {
  if (pressTimer) clearTimeout(pressTimer)
  pressTimer = undefined
  pressOrigin = null
}
function togglePinnedFromMenu() {
  const menu = conversationMenu.value
  if (!menu) return
  store.toggleConversationPin(ConversationId.forContact(menu.user).key)
  closeConversationMenu(true)
}
function deleteFromMenu() {
  const menu = conversationMenu.value
  if (!menu) return
  const key = ConversationId.forContact(menu.user).key
  // 只隐藏列表项，不删除 IndexedDB 历史；下一条消息到达会自动恢复
  store.hideConversation(key)
  if (store.state.selectedChatId === key) store.setSelectedChat(null)
  closeConversationMenu(true)
}
function onWindowPointerDown(event: PointerEvent) {
  if (!conversationMenu.value) return
  if (conversationMenuEl.value?.contains(event.target as Node)) return
  closeConversationMenu()
}
function onWindowKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') closeConversationMenu(true)
}
onMounted(() => {
  window.addEventListener('pointerdown', onWindowPointerDown, true)
  window.addEventListener('keydown', onWindowKeydown)
})
onUnmounted(() => {
  window.removeEventListener('pointerdown', onWindowPointerDown, true)
  window.removeEventListener('keydown', onWindowKeydown)
  pressEnd()
})

function resetRemoteSearch() {
  clearTimeout(remoteTimer)
  remoteController?.abort()
  remoteController = undefined
  remoteKeyword = ''
  remoteHits.value = []
  remotePage.value = 0
  remoteHasMore.value = false
  remoteSearching.value = false
  remoteError.value = ''
}
async function runRemoteSearch(force = false, append = false) {
  clearTimeout(remoteTimer)
  const keyword = props.query.trim()
  if (searchComposing.value || props.tab !== 'contacts' || !keyword) return
  if (append) {
    if (!remoteHasMore.value || remotePage.value >= USER_SEARCH_MAX_PAGE) return
    if (remoteSearching.value || remoteKeyword !== keyword) return
  } else if (!force && remoteSearching.value && remoteKeyword === keyword) return
  remoteController?.abort()
  const owner = String(store.state.accountId)
  const cooldown = contactSearchBudget.reserve(owner)
  if (cooldown > 0) { remoteSearching.value = false; showSearchCooldown(cooldown); return }
  const controller = new AbortController(), session = sessionEpoch.capture()
  remoteController = controller
  remoteKeyword = keyword
  remoteSearching.value = true
  remoteError.value = ''
  const page = append ? remotePage.value + 1 : 1
  const active = () => remoteController === controller && !controller.signal.aborted
    && session.isCurrent() && props.tab === 'contacts' && props.query.trim() === keyword
  try {
    const hits = await api.searchUsers(keyword, page, controller.signal)
    if (active()) {
      const merged = append ? [...remoteHits.value, ...hits] : hits
      remoteHits.value = [...new Map(merged.map(hit => [String(hit.id), hit])).values()]
      remotePage.value = page
      // 服务端每页 6 条：整页返回才认为后面还有；第 4 页后强制停止
      remoteHasMore.value = page < USER_SEARCH_MAX_PAGE && hits.length === USER_SEARCH_PAGE_SIZE
    }
  } catch (error) {
    if (active() && error instanceof UserSearchRateLimitError) {
      contactSearchBudget.defer(owner, error.retryAfterSeconds * 1000)
      showSearchCooldown(error.retryAfterSeconds * 1000)
    } else if (active()) remoteError.value = t('sidebar.userSearchFailed')
  } finally {
    if (remoteController === controller) {
      remoteSearching.value = false
      remoteController = undefined
    }
  }
}
function loadMoreRemote() {
  if (remoteSearching.value || searchCooldown.value > 0) return
  void runRemoteSearch(false, true)
}
function finishSearchComposition() {
  searchComposing.value = false
  clearTimeout(remoteTimer)
  remoteTimer = setTimeout(() => { void runRemoteSearch() }, 3000)
}
async function submitSearch() {
  // A composing Enter confirms an IME candidate; it must not submit the search.
  if (searchComposing.value) return
  const value = searchInput.value?.value ?? props.query
  if (value !== props.query) emit('update:query', value)
  await nextTick() // Let the parent query update and its debounce watcher settle first.
  void runRemoteSearch()
  if (window.matchMedia('(pointer: coarse)').matches || window.innerWidth < 700) searchInput.value?.blur()
}
watch(() => [props.tab, props.query, store.state.accountId] as const, ([tab, query]) => {
  resetRemoteSearch()
  const keyword = query.trim()
  if (tab !== 'contacts' || !keyword) return
  remoteTimer = setTimeout(() => { void runRemoteSearch() }, 3000)
})
onUnmounted(() => { resetRemoteSearch(); clearInterval(cooldownTimer) })
watch(() => store.state.accountId, () => { clearInterval(cooldownTimer); searchRetryAt.value = 0 })

function remoteHint(user: User): string | null {
  const hit = remoteState.value.get(String(user.id))
  if (!hit) return null
  if (hit.relation === 'FRIEND') return t('sidebar.userSearchFriend')
  if (hit.relation === 'PENDING') return t('sidebar.userSearchSent')
  return t('sidebar.userSearchViewCard')
}

// 点搜索结果先看名片：资料确认无误后，在名片里点"加为好友"发申请
const previewUser = ref<User | null>(null)
const previewDialog = ref<HTMLElement | null>(null)
useDialog(previewDialog, () => closePreview())
function openPreview(user: User) {
  previewUser.value = user
}
function closePreview() {
  previewUser.value = null
}
function onPreviewChat(profile: { id: string; username: string }) {
  const user: User = { id: profile.id, username: profile.username }
  store.mergeUsers([user])
  closePreview()
  select(user)
}
// 名片里加了好友/拉黑后：刷新联系人并重跑当前搜索，状态行跟着更新
async function onPreviewChanged() {
  await refreshContacts(true)
  if (props.query.trim() && props.tab === 'contacts') {
    await runRemoteSearch(true)
  }
}
const visibleCount = computed(() => sections.value.reduce((total, section) => total + section.users.length, 0))
const directoryPanel=ref<HTMLElement|null>(null),directoryTop=ref(0),directoryHeight=ref(700)
const rows=computed(()=>contactRows(sections.value))
const directoryLayout=computed(()=>virtualLayout(rows.value.map(row=>row.key),new Map(rows.value.map(row=>[row.key,contactRowHeight(row)]))))
const directoryRange=computed(()=>directoryLayout.value.range(directoryTop.value,directoryHeight.value,300))
const visibleRows=computed(()=>rows.value.slice(directoryRange.value.start,directoryRange.value.end))
function onDirectoryScroll() {
  closeConversationMenu()
  directoryScrolled(true)
}
function directoryScrolled(allowMore = false) {
  const el = directoryPanel.value
  if (!el) return
  directoryTop.value = el.scrollTop
  directoryHeight.value = el.clientHeight
  // 只有真实滚动才触发翻页；列表不足一屏时用底部“加载更多”按钮补齐
  if (allowMore && el.scrollHeight - el.scrollTop - el.clientHeight < 200) loadMoreRemote()
}
let directoryObserver:ResizeObserver|undefined
onMounted(()=>{directoryObserver=new ResizeObserver(() => directoryScrolled());if(directoryPanel.value)directoryObserver.observe(directoryPanel.value);directoryScrolled()})
onUnmounted(()=>directoryObserver?.disconnect())
watch(()=>[props.tab,props.query],()=>{closeConversationMenu();if(directoryPanel.value)directoryPanel.value.scrollTop=0;directoryTop.value=0})
async function contactKeydown(event:KeyboardEvent,key:string,user:User){
  if(event.key==='ContextMenu'||(event.shiftKey&&event.key==='F10')){
    if(!canUseConversationMenu())return
    event.preventDefault()
    const rect=(event.currentTarget as HTMLElement).getBoundingClientRect()
    openConversationMenuAt(rect.left+20,rect.top+16,user,event.currentTarget as HTMLElement)
    return
  }
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
  const hint = remoteHint(user)
  if (hint) return hint
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
  if (Date.now() < suppressClickUntil) return
  // 搜索结果里"还不是好友的人"：先打开名片，加好友在名片里操作
  if (remoteState.value.has(String(u.id))) { openPreview(u); return }
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
.user-search-feedback{display:block;margin:-4px 12px 8px;font-size:12px;color:var(--muted)}
.profile-preview-overlay{position:fixed;inset:0;z-index:1000;display:flex;align-items:center;justify-content:center;padding:24px;background:rgba(0,0,0,.4)}
.profile-preview{position:relative;width:min(420px,100%);max-height:calc(100dvh - 48px);overflow:auto;background:var(--panel);border:1px solid var(--surface-border);border-radius:18px;box-shadow:0 24px 60px rgba(52,43,37,.24)}
.profile-preview-close{position:absolute;top:8px;right:8px;width:36px;height:36px;border:0;border-radius:10px;background:transparent;color:inherit;font-size:20px;cursor:pointer;z-index:1}
.profile-preview-close:hover{background:var(--hover)}
.user-search-feedback.error{color:#b53b27}
.remote-more{display:block;margin:4px auto 16px;padding:8px 18px;border:1px solid var(--surface-border);border-radius:10px;background:var(--panel);color:var(--text-secondary);font:inherit;font-size:13px;cursor:pointer}
.remote-more:disabled{opacity:.5;cursor:not-allowed}
.search-bar{display:flex;align-items:center;margin:0 12px 10px;border:1px solid var(--surface-border);background:var(--panel);border-radius:12px;min-height:44px}
.search-bar:focus-within{border-color:var(--accent-2)}
.search-bar input{width:100%;min-width:0;background:transparent;color:var(--text-primary);border:0;outline:0;padding:12px;font:inherit;font-size:13px}
.search-bar input::-webkit-search-cancel-button{display:none}
.clear-search{border:0;background:transparent;color:var(--text-secondary);font-size:20px;min-width:44px;min-height:44px;cursor:pointer}
.submit-search{display:grid;place-items:center;flex:none;width:44px;min-height:44px;margin-right:2px;padding:0;border:0;border-radius:10px;background:transparent;color:var(--action);cursor:pointer}
.submit-search:hover{background:var(--hover)}
.submit-search:focus-visible,.clear-search:focus-visible{outline:2px solid var(--accent-2);outline-offset:-3px}
@media(max-width:699px){.search-bar input{font-size:16px}}
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
.users-list .user-row{display:flex;align-items:center;padding:12px;margin:2px 8px;border-radius:10px;cursor:pointer;user-select:none;-webkit-touch-callout:none;min-height:44px}
.users-list .user-row:hover{background:var(--hover)}
.users-list .user-row.active{background:var(--apricot)}
.pin-mark{display:inline-flex;flex:none;width:13px;height:13px;color:var(--accent-2)}
.conversation-menu{position:fixed;z-index:1200;min-width:160px;padding:6px;display:flex;flex-direction:column;gap:2px;background:var(--panel);border:1px solid var(--surface-border);border-radius:12px;box-shadow:0 18px 40px rgba(52,43,37,.2)}
.conversation-menu button{display:flex;align-items:center;width:100%;min-height:40px;padding:0 12px;border:0;border-radius:8px;background:transparent;color:var(--text-primary);font:inherit;font-size:13px;text-align:left;cursor:pointer}
.conversation-menu button:hover,.conversation-menu button:focus-visible{background:var(--hover);outline:none}
.conversation-menu button.danger{color:#b53b27}
.virtual-contacts{flex:none;overflow:visible}
.virtual-contacts .user-row{height:72px;min-height:0;box-sizing:border-box;margin:0 8px}
.virtual-contacts .section-heading{height:40px;box-sizing:border-box;display:flex;align-items:center}
.virtual-contacts .section-heading h3{margin:0 24px}
.avatar{flex:none;width:44px;height:44px;border-radius:var(--avatar-radius);overflow:hidden;display:flex;align-items:center;justify-content:center;font-weight:700;margin-right:10px;background:var(--apricot);color:var(--on-apricot)}
.avatar img{width:100%;height:100%;object-fit:cover}
.meta{flex:1;min-width:0}
.name-row{display:flex;align-items:center;justify-content:space-between;gap:8px}
.name-row .name{flex:1;min-width:0}
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
