<template>
  <router-view />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { initTheme } from './utils/theme'

// 全局初始化主题：所有页面（首页/FAQ/功能页等）统一应用，避免继承 /app 残留的陈旧 data-theme
onMounted(() => {
  initTheme()
})
</script>

<style>
/* global styles can be in styles.css */
</style>
