<template>
  <div class="device-management">
    <div class="header">
      <h2>设备管理</h2>
      <p class="subtitle">管理已登录的设备，支持踢下线操作</p>
    </div>

    <div class="device-list" v-if="devices.length > 0">
      <div
        v-for="device in devices"
        :key="device.deviceId"
        class="device-item"
        :class="{ 'current-device': device.deviceType === currentDeviceType }"
      >
        <div class="device-icon">
          <span v-if="device.deviceType === 'desktop'">💻</span>
          <span v-else-if="device.deviceType === 'mobile'">📱</span>
          <span v-else-if="device.deviceType === 'web'">🌐</span>
          <span v-else-if="device.deviceType === 'cli'">⌨️</span>
          <span v-else>📟</span>
        </div>
        <div class="device-info">
          <div class="device-name">
            {{ getDeviceTypeName(device.deviceType) }}
            <span v-if="device.deviceType === currentDeviceType" class="current-badge">当前设备</span>
          </div>
          <div class="device-id">ID: {{ device.deviceId.substring(0, 8) }}...</div>
          <div class="device-time">最后活跃: {{ formatTime(device.lastActiveTime) }}</div>
        </div>
      </div>
    </div>

    <div class="empty-state" v-else>
      <p>暂无已注册设备</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getDevices } from '../api'
import { DEVICE_TYPE } from '../utils/device'

const devices = ref([])
const currentDeviceType = DEVICE_TYPE

onMounted(async () => {
  await loadDevices()
})

async function loadDevices() {
  try {
    devices.value = await getDevices()
  } catch (e) {
    console.error('加载设备列表失败:', e)
  }
}

function getDeviceTypeName(type) {
  const names = {
    desktop: '桌面端',
    mobile: '移动端',
    web: '网页端',
    cli: '命令行'
  }
  return names[type] || type
}

function formatTime(timestamp) {
  if (!timestamp) return '未知'
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN')
}
</script>

<style scoped>
.device-management {
  padding: 24px;
  max-width: 600px;
  margin: 0 auto;
}

.header {
  margin-bottom: 24px;
}

.header h2 {
  margin: 0 0 8px 0;
  font-size: 24px;
  color: #333;
}

.subtitle {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.device-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.device-item {
  display: flex;
  align-items: center;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 12px;
  transition: background 0.2s;
}

.device-item:hover {
  background: #e9ecef;
}

.device-item.current-device {
  background: #e3f2fd;
  border: 1px solid #2196f3;
}

.device-icon {
  font-size: 32px;
  margin-right: 16px;
}

.device-info {
  flex: 1;
}

.device-name {
  font-weight: 600;
  font-size: 16px;
  color: #333;
  display: flex;
  align-items: center;
  gap: 8px;
}

.current-badge {
  font-size: 12px;
  padding: 2px 8px;
  background: #2196f3;
  color: white;
  border-radius: 12px;
}

.device-id {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.device-time {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}

.empty-state {
  text-align: center;
  padding: 48px;
  color: #999;
}
</style>
