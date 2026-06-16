<template>
  <div class="toast-container" :class="{ visible: visible }">
    <transition-group name="toast-fade">
      <div v-for="toast in toasts" :key="toast.id" class="toast" :class="toast.type">
        <div class="toast-icon">{{ getToastIcon(toast.type) }}</div>
        <div class="toast-message">{{ toast.message }}</div>
        <button class="toast-close" @click="remove(toast.id)">×</button>
      </div>
    </transition-group>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const toasts = ref([])
const visible = ref(false)
let toastId = 0

// 暴露方法给父组件
defineExpose({
  show,
  success,
  error,
  info,
  warning
})

// 将 Toast 方法挂载到全局 window 对象
onMounted(() => {
  window.$toast = {
    show: (message, type = 'info', duration = 3000) => show(message, type, duration),
    success: (message, duration = 3000) => success(message, duration),
    error: (message, duration = 3000) => error(message, duration),
    info: (message, duration = 3000) => info(message, duration),
    warning: (message, duration = 3000) => warning(message, duration)
  }
})

onUnmounted(() => {
  delete window.$toast
})

function getToastIcon(type) {
  switch (type) {
    case 'success': return '✓'
    case 'error': return '!'
    case 'warning': return '⚠'
    default: return 'ℹ'
  }
}

function show(message, type = 'info', duration = 3000) {
  const id = ++toastId
  const toast = { id, message, type }
  toasts.value.push(toast)
  visible.value = true

  if (duration > 0) {
    setTimeout(() => remove(id), duration)
  }

  return id
}

function success(message, duration = 3000) {
  return show(message, 'success', duration)
}

function error(message, duration = 3000) {
  return show(message, 'error', duration)
}

function info(message, duration = 3000) {
  return show(message, 'info', duration)
}

function warning(message, duration = 3000) {
  return show(message, 'warning', duration)
}

function remove(id) {
  const index = toasts.value.findIndex(t => t.id === id)
  if (index > -1) {
    toasts.value.splice(index, 1)
    if (toasts.value.length === 0) {
      visible.value = false
    }
  }
}
</script>

<style scoped>
.toast-container {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 10px;
  pointer-events: none;
}

.toast-container.visible {
  pointer-events: auto;
}

.toast {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: var(--panel);
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  min-width: 280px;
  max-width: 400px;
  backdrop-filter: blur(10px);
  border: 1px solid var(--surface-border);
  animation: slideDown 0.3s ease-out;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.toast.success {
  border-left: 4px solid #52c41a;
}

.toast.error {
  border-left: 4px solid #ff4757;
}

.toast.warning {
  border-left: 4px solid #ff9500;
}

.toast.info {
  border-left: 4px solid #1890ff;
}

.toast-icon {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
}

.toast.success .toast-icon {
  background: rgba(82, 196, 26, 0.15);
  color: #52c41a;
}

.toast.error .toast-icon {
  background: rgba(255, 71, 87, 0.15);
  color: #ff4757;
}

.toast.warning .toast-icon {
  background: rgba(255, 149, 0, 0.15);
  color: #ff9500;
}

.toast.info .toast-icon {
  background: rgba(24, 144, 255, 0.15);
  color: #1890ff;
}

.toast-message {
  flex: 1;
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.4;
}

.toast-close {
  background: transparent;
  border: none;
  font-size: 18px;
  cursor: pointer;
  color: var(--muted);
  padding: 0 4px;
  transition: color 0.2s;
}

.toast-close:hover {
  color: var(--text-primary);
}

/* 过渡动画 */
.toast-fade-enter-active,
.toast-fade-leave-active {
  transition: all 0.3s ease;
}

.toast-fade-enter-from {
  opacity: 0;
  transform: translateY(-20px);
}

.toast-fade-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}

/* 移动端适配 */
@media (max-width: 768px) {
  .toast-container {
    top: 12px;
    left: 12px;
    right: 12px;
    transform: none;
  }

  .toast {
    min-width: auto;
    max-width: none;
  }
}
</style>
