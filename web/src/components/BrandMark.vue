<template>
  <span class="brand-mark"><img :src="logo" alt="" width="32" height="32" /><span>{{ $t('app.brand') }}</span></span>
</template>
<script setup lang="ts">
import logo from '../assets/logo.svg'
</script>
<style scoped>
.brand-mark{display:inline-flex;align-items:center;gap:9px;font-size:20px;font-weight:700;letter-spacing:-.04em;color:var(--text-primary);white-space:nowrap}
.brand-mark img{display:block;border-radius:10px}
</style>
