<template>
  <nav class="public-breadcrumbs" :aria-label="$t('seo.breadcrumb.label')">
    <ol><li><router-link to="/">{{ $t('seo.breadcrumb.home') }}</router-link></li><li aria-current="page">{{ title }}</li></ol>
  </nav>
</template>

<script setup lang="ts">
defineProps<{ title: string }>()
</script>

<style scoped>
.public-breadcrumbs{margin-bottom:24px;font-size:14px;color:var(--text-secondary)}
ol{display:flex;flex-wrap:wrap;gap:8px;list-style:none;padding:0;margin:0}
li+li::before{content:'/';margin-right:8px;color:var(--muted)}
a{color:var(--accent-1);text-decoration:none}a:hover{text-decoration:underline}
</style>
