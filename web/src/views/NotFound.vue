<template>
  <main class="missing-page">
    <h1>{{ $t('seo.missing.title') }}</h1>
    <p>{{ $t('seo.missing.description') }}</p>
    <router-link to="/">{{ $t('nav.intro') }}</router-link>
  </main>
</template>

<style scoped>
.missing-page { max-width: 680px; margin: 15vh auto; padding: 24px; line-height: 1.8; }
</style>
