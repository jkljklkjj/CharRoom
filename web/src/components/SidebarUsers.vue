<template>
  <div class="users-wrap">
    <div class="users-header">
      <div class="title">联系人</div>
      <div class="header-actions">
        <button class="friend-requests-btn" @click="toggleFriendRequests" :class="{hasRequests: friendRequests.length > 0}">
          📩
          <span v-if="friendRequests.length > 0" class="badge">{{ friendRequests.length }}</span>
        </button>
        <button class="settings-btn" @click="openSettings">⚙️</button>
        <button class="add" @click="onAdd">＋</button>
      </div>
    </div>

    <div class="search-bar">
      <input
        v-model="searchQuery"
        type="search"
        placeholder="搜索好友 / 群聊，支持名称或账号"
      />
    </div>

    <!-- 好友申请弹窗 -->
    <div v-if="showFriendRequests" class="friend-requests-modal" @click.self="toggleFriendRequests">
      <div class="modal-content">
        <div class="modal-header">
          <h3>好友申请</h3>
          <button class="close-btn" @click="toggleFriendRequests">×</button>
        </div>
        <div class="modal-body">
          <div v-if="friendRequests.length === 0" class="empty">暂无好友申请</div>
          <ul v-else class="request-list">
            <li v-for="req in friendRequests" :key="req.id" class="request-item">
              <div class="request-info">
                <div v-if="req.avatarUrl" class="avatar"><img :src="req.avatarUrl" alt="avatar" /></div>
                <div v-else class="avatar">{{ initials(req.username) }}</div>
                <div class="request-meta">
                  <div class="name">{{ req.username }}</div>
                  <div class="desc">账号: {{ req.id }} 申请添加你为好友</div>
                </div>
              </div>
              <div class="request-actions">
                <button class="accept-btn" @click="handleAccept(req.id)">同意</button>
                <button class="reject-btn" @click="handleReject(req.id)">拒绝</button>
              </div>
            </li>
          </ul>
        </div>
      </div>
    </div>

    <ul class="users-list">
      <li v-for="u in filteredUsers" :key="u.id" @click="select(u)" :class="{active: store.state.selectedChatId === u.id}">
        <div v-if="u.avatarUrl" class="avatar"><img :src="avatarSrc(u)" alt="avatar" /></div>
        <div v-else class="avatar">{{ initials(u.name || u.account || u.username) }}</div>
        <div class="meta">
          <div class="name">{{ u.name || u.account || u.username }}</div>
          <div class="sub">{{ u.status || '在线' }}</div>
        </div>
      </li>
    </ul>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useStore } from '../store'
import api from '../api'

const emit = defineEmits(['user-selected', 'open-settings'])

const store = useStore()
const showFriendRequests = ref(false)
const friendRequests = ref([])
const searchQuery = ref('')

const filteredUsers = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase()
  if (!keyword) return store.state.users
  return store.state.users.filter(u => {
    const username = String(u.name || u.account || u.username || '').toLowerCase()
    const id = String(u.id || '').toLowerCase()
    return username.includes(keyword) || id.includes(keyword)
  })
})

function avatarSrc(u) {
  if (!u || !u.avatarUrl) return null
  const v = u.avatarKey
  return v ? (u.avatarUrl + (u.avatarUrl.includes('?') ? '&v=' : '?v=') + encodeURIComponent(v)) : u.avatarUrl
}

function initials(name) {
  if (!name) return 'U'
  return name.split(' ').map(s => s[0]).slice(0,2).join('').toUpperCase()
}

function select(u) {
  store.setSelectedChat(u.id)
  emit('user-selected', u)
}

function onAdd() {
  const account = prompt('输入对方账号（数字ID或邮箱）以添加为好友')
  if (!account) return
  api.addFriend(account).then(ok => { if (ok) alert('已发送好友请求') })
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
  const requests = await api.getFriendRequests()
  console.log('获取到的好友请求原始数据:', requests)
  friendRequests.value = requests
}

async function handleAccept(userId) {
  const ok = await api.acceptFriend(userId)
  if (ok) {
    alert('已同意好友申请')
    // 移除已处理的申请
    friendRequests.value = friendRequests.value.filter(req => req.id !== userId)
    // 刷新好友列表
    const friends = await api.getFriends()
    store.setUsers(friends)
  } else {
    alert('操作失败，请重试')
  }
}

async function handleReject(userId) {
  const ok = await api.rejectFriend(userId)
  if (ok) {
    alert('已拒绝好友申请')
    // 移除已处理的申请
    friendRequests.value = friendRequests.value.filter(req => req.id !== userId)
  } else {
    alert('操作失败，请重试')
  }
}

onMounted(() => {
  // 页面加载时检查是否有好友申请和加载好友列表
  if (store.state.token) {
    loadFriendRequests()
    loadFriends()
  }
})

async function loadFriends() {
  const friends = await api.getFriends()
  store.setUsers(friends)
}
</script>

<style scoped>
.users-wrap{height:100%;display:flex;flex-direction:column}
.users-header{display:flex;align-items:center;justify-content:space-between;padding:12px}
.header-actions{display:flex;gap:8px;align-items:center}
.friend-requests-btn{position:relative;background:transparent;border:0;font-size:18px;cursor:pointer;padding:4px 8px;border-radius:4px}
.friend-requests-btn:hover{background:rgba(0,0,0,0.05)}
.friend-requests-btn.hasRequests{color:#ff7a33}
.badge{position:absolute;top:-2px;right:-2px;background:#ff4757;color:white;font-size:10px;padding:1px 4px;border-radius:8px;min-width:12px;text-align:center}
.settings-btn, .add{background:transparent;border:0;font-size:18px;cursor:pointer;padding:4px 8px;border-radius:4px}
.settings-btn:hover, .add:hover{background:rgba(0,0,0,0.05)}
.users-list{list-style:none;padding:0;margin:0;overflow:auto;flex:1}
.users-list li{display:flex;align-items:center;padding:10px 12px;cursor:pointer}
.users-list li.active{background:rgba(255,122,51,0.08)}
.avatar{width:40px;height:40px;border-radius:8px;overflow:hidden;display:flex;align-items:center;justify-content:center;font-weight:700;margin-right:10px;background:#f0f0f0}
.avatar img{width:100%;height:100%;object-fit:cover}
.meta .name{font-weight:600}
.meta .sub{font-size:12px;color:var(--muted)}

/* 搜索框样式 */
.search-bar{padding: 0 12px 12px;}
.search-bar input{
  width: 100%;
  padding: 10px 16px 10px 40px;
  border: 1px solid #e8e8e8;
  border-radius: 24px;
  font-size: 14px;
  background: #fafafa url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="%23999" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"></circle><path d="m21 21-4.35-4.35"></path></svg>') no-repeat 14px center;
  transition: all 0.3s ease;
  box-sizing: border-box;
}
.search-bar input:focus{
  outline: none;
  border-color: #ff7a33;
  background-color: #fff;
  box-shadow: 0 0 0 3px rgba(255, 122, 51, 0.1);
}
.search-bar input:hover{
  border-color: #d0d0d0;
  background-color: #fff;
}
.search-bar input::placeholder{
  color: #bbb;
}

/* 好友申请弹窗 */
.friend-requests-modal{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;z-index:1000}
.modal-content{background:white;border-radius:12px;width:400px;max-width:90%;box-shadow:0 4px 20px rgba(0,0,0,0.15)}
.modal-header{display:flex;align-items:center;justify-content:space-between;padding:16px 20px;border-bottom:1px solid #eee}
.modal-header h3{margin:0;font-size:16px}
.close-btn{background:transparent;border:0;font-size:20px;cursor:pointer;color:#999;padding:0 4px}
.close-btn:hover{color:#333}
.modal-body{padding:12px 0}
.empty{text-align:center;padding:40px 20px;color:#999;font-size:14px}
.request-list{list-style:none;padding:0;margin:0}
.request-item{display:flex;align-items:center;justify-content:space-between;padding:12px 20px;border-bottom:1px solid #f5f5f5}
.request-item:last-child{border-bottom:0}
.request-info{display:flex;align-items:center}
.request-meta .name{font-weight:600;font-size:14px}
.request-meta .desc{font-size:12px;color:#666;margin-top:2px}
.request-actions{display:flex;gap:8px}
.accept-btn{background:#52c41a;color:white;border:0;padding:6px 12px;border-radius:4px;cursor:pointer;font-size:12px}
.accept-btn:hover{background:#43a047}
.reject-btn{background:#f5f5f5;color:#666;border:0;padding:6px 12px;border-radius:4px;cursor:pointer;font-size:12px}
.reject-btn:hover{background:#e0e0e0}
</style>
