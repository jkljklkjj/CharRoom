<template>
  <div class="seo-page">
    <div class="seo-container">
      <h1>{{ $t('features.title') }}</h1>
      <p class="seo-lead">{{ $t('features.lead') }}</p>

      <div v-for="(feat, i) in featureList" :key="i" class="feature-card">
        <div class="feat-icon">{{ feat.icon }}</div>
        <h2>{{ feat.title }}</h2>
        <p>{{ feat.desc }}</p>
        <ul class="feat-details">
          <li v-for="(d, j) in feat.details" :key="j">{{ d }}</li>
        </ul>
      </div>
    </div>
    <footer class="seo-footer">
      <router-link to="/">{{ $t('nav.intro') }}</router-link>
      <router-link to="/app">{{ $t('nav.webApp') }}</router-link>
    </footer>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const featureList = computed(() => [
  { icon: '💬', title: t('features.f1Title'), desc: t('features.f1Desc'), details: [t('features.f1d1'), t('features.f1d2'), t('features.f1d3')] },
  { icon: '🔒', title: t('features.f2Title'), desc: t('features.f2Desc'), details: [t('features.f2d1'), t('features.f2d2'), t('features.f2d3')] },
  { icon: '🤖', title: t('features.f3Title'), desc: t('features.f3Desc'), details: [t('features.f3d1'), t('features.f3d2'), t('features.f3d3')] },
  { icon: '📱', title: t('features.f4Title'), desc: t('features.f4Desc'), details: [t('features.f4d1'), t('features.f4d2')] },
])
</script>

<style scoped>
.seo-page {
  min-height: 100vh;
  background: var(--bg, #fff8f5);
  padding: 48px 20px;
}
.seo-container { max-width: 720px; margin: 0 auto; }
h1 { font-size: 28px; margin-bottom: 8px; }
.seo-lead { color: var(--text-secondary, #666); margin-bottom: 32px; line-height: 1.6; }
.feature-card {
  background: var(--panel, #fff);
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 16px;
  border: 1px solid var(--surface-border, rgba(0,0,0,0.04));
}
.feat-icon { font-size: 32px; margin-bottom: 8px; }
h2 { font-size: 20px; margin: 0 0 8px; color: var(--text-primary); }
p { color: var(--text-secondary, #666); line-height: 1.6; margin: 0 0 12px; }
.feat-details { margin: 0; padding-left: 20px; color: var(--text-secondary, #666); line-height: 1.8; }
.seo-footer { max-width: 720px; margin: 48px auto 0; padding-top: 24px; border-top: 1px solid var(--surface-border); display: flex; gap: 24px; justify-content: center; }
.seo-footer a { color: var(--accent-1); text-decoration: none; }
.seo-footer a:hover { text-decoration: underline; }
</style>
