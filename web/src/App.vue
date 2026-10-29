<template>
  <router-view />
</template>

<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { applyPageSeo } from './services/pageSeo'
import { initTheme } from './utils/theme'
import { syncPageViewport } from './services/pageViewport'

const route = useRoute()
const { locale, t } = useI18n()
watch([() => route.path, locale], () => {
  applyPageSeo(route.path, locale.value, t)
  syncPageViewport(route.path)
}, { immediate: true })

// 全局初始化主题：所有页面（首页/FAQ/功能页等）统一应用，避免继承 /app 残留的陈旧 data-theme
onMounted(() => {
  initTheme()
})
</script>

<style>
/* global styles can be in styles.css */
</style>
