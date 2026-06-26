<template>
  <div class="seo-page">
    <div class="seo-container">
      <h1>{{ $t('faq.title') }}</h1>
      <p class="seo-lead">{{ $t('faq.lead') }}</p>

      <div class="faq-list">
        <details v-for="(item, i) in faqItems" :key="i" class="faq-item">
          <summary class="faq-q">{{ item.q }}</summary>
          <div class="faq-a">{{ item.a }}</div>
        </details>
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

const faqItems = computed(() => [
  { q: t('faq.q1'), a: t('faq.a1') },
  { q: t('faq.q2'), a: t('faq.a2') },
  { q: t('faq.q3'), a: t('faq.a3') },
  { q: t('faq.q4'), a: t('faq.a4') },
  { q: t('faq.q5'), a: t('faq.a5') },
  { q: t('faq.q6'), a: t('faq.a6') },
  { q: t('faq.q7'), a: t('faq.a7') },
  { q: t('faq.q8'), a: t('faq.a8') },
])
</script>

<style scoped>
.seo-page {
  min-height: 100vh;
  background: var(--bg, #fff8f5);
  padding: 48px 20px;
}
.seo-container {
  max-width: 720px;
  margin: 0 auto;
}
h1 { font-size: 28px; margin-bottom: 8px; color: var(--text-primary); }
.seo-lead { color: var(--text-secondary, #666); margin-bottom: 32px; line-height: 1.6; }
.faq-list { display: flex; flex-direction: column; gap: 12px; }
.faq-item {
  background: var(--panel, #fff);
  border-radius: 12px;
  padding: 16px 20px;
  cursor: pointer;
  border: 1px solid var(--surface-border, rgba(0,0,0,0.04));
  transition: box-shadow 0.2s;
}
.faq-item:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.06); }
.faq-q {
  font-weight: 600;
  font-size: 16px;
  color: var(--text-primary);
  cursor: pointer;
  list-style: none;
  display: flex;
  align-items: center;
  gap: 8px;
}
.faq-q::before { content: "❓"; font-size: 14px; }
.faq-q::-webkit-details-marker { display: none; }
.faq-a {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--surface-border);
  color: var(--text-secondary, #666);
  line-height: 1.7;
  font-size: 15px;
}
.seo-footer {
  max-width: 720px;
  margin: 48px auto 0;
  padding-top: 24px;
  border-top: 1px solid var(--surface-border);
  display: flex;
  gap: 24px;
  justify-content: center;
}
.seo-footer a { color: var(--accent-1); text-decoration: none; }
.seo-footer a:hover { text-decoration: underline; }
</style>
