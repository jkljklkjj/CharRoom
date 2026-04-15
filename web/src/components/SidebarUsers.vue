<template>
  <div class="users-wrap">
    <div class="users-header">
      <div class="title">联系人</div>
      <button class="add" @click="onAdd">＋</button>
    </div>
    <ul class="users-list">
      <li v-for="u in store.state.users" :key="u.id" @click="select(u)" :class="{active: selected === u.id}">
        <div class="avatar">{{ initials(u.name || u.account) }}</div>
        <div class="meta">
          <div class="name">{{ u.name || u.account }}</div>
          <div class="sub">{{ u.status || '在线' }}</div>
        </div>
      </li>
    </ul>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useStore } from '../store'
import api from '../api'

const store = useStore()

function initials(name) {
  if (!name) return 'U'
  return name.split(' ').map(s => s[0]).slice(0,2).join('').toUpperCase()
}

function select(u) {
  store.setSelectedChat(u.id)
}

function onAdd() {
  const id = prompt('输入对方账号以添加为好友')
  if (!id) return
  api.addFriend(id).then(ok => { if (ok) alert('已发送好友请求') })
}
</script>

<style scoped>
.users-wrap{height:100%;display:flex;flex-direction:column}
.users-header{display:flex;align-items:center;justify-content:space-between;padding:12px}
.users-list{list-style:none;padding:0;margin:0;overflow:auto}
.users-list li{display:flex;align-items:center;padding:10px 12px;cursor:pointer}
.users-list li.active{background:rgba(255,122,51,0.08)}
.avatar{width:40px;height:40px;border-radius:8px;background:linear-gradient(180deg,var(--accent-1),var(--accent-2));color:#fff;display:flex;align-items:center;justify-content:center;font-weight:700;margin-right:10px}
.meta .name{font-weight:600}
.meta .sub{font-size:12px;color:var(--muted)}
.add{background:transparent;border:0;font-size:18px}
</style>
