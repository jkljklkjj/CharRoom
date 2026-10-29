<template>
  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <template v-if="name === 'search'"><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 4 4" /></template>
    <template v-else-if="name === 'discover'"><circle cx="12" cy="12" r="9" /><path d="m16 8-2.5 5.5L8 16l2.5-5.5Z" /></template>
    <template v-else-if="name === 'requests'"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" /></template>
    <template v-else-if="name === 'settings'"><path d="m9 3-1 3-3 1v4l-2 1 2 2v3l3 1 1 3h6l1-3 3-1v-3l2-2-2-1V7l-3-1-1-3Z" /><circle cx="12" cy="12" r="3" /></template>
    <template v-else><path d="M12 5v14M5 12h14" /></template>
  </svg>
</template>
<script setup lang="ts">
defineProps<{ name: 'discover' | 'requests' | 'settings' | 'add' | 'search' }>()
</script>
