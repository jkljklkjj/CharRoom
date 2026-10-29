<template>
  <span class="public-source-links">
    <a href="https://github.com/jkljklkjj/CharRoom" target="_blank" rel="noopener noreferrer">{{ $t('seo.source.client') }}</a>
    <a href="https://gitee.com/zhenye55/chat-room" target="_blank" rel="noopener noreferrer">{{ $t('seo.source.server') }}</a>
  </span>
</template>

<style scoped>
.public-source-links{display:inline-flex;flex-wrap:wrap;gap:14px;font-size:12px}
a{color:var(--muted);text-decoration:none}a:hover{text-decoration:underline}
</style>
