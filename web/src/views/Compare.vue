<template>
  <div class="seo-page">
    <div class="seo-container">
      <h1>{{ $t('compare.title') }}</h1>
      <p class="seo-lead">{{ $t('compare.lead') }}</p>

      <div class="comparison-table">
        <table>
          <thead>
            <tr>
              <th>{{ $t('compare.feature') }}</th>
              <th class="highlight">轻聊</th>
              <th>微信</th>
              <th>Telegram</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, i) in rows" :key="i">
              <td class="row-label">{{ row.label }}</td>
              <td class="highlight">{{ row.us }}</td>
              <td>{{ row.wx }}</td>
              <td>{{ row.tg }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <p class="compare-note">{{ $t('compare.note') }}</p>
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
const rows = computed(() => [
  {
    label: t('compare.r1Label'), us: t('compare.r1Us'), wx: t('compare.r1Wx'), tg: t('compare.r1Tg')
  },
  {
    label: t('compare.r2Label'), us: t('compare.r2Us'), wx: t('compare.r2Wx'), tg: t('compare.r2Tg')
  },
  {
    label: t('compare.r3Label'), us: t('compare.r3Us'), wx: t('compare.r3Wx'), tg: t('compare.r3Tg')
  },
  {
    label: t('compare.r4Label'), us: t('compare.r4Us'), wx: t('compare.r4Wx'), tg: t('compare.r4Tg')
  },
  {
    label: t('compare.r5Label'), us: t('compare.r5Us'), wx: t('compare.r5Wx'), tg: t('compare.r5Tg')
  },
  {
    label: t('compare.r6Label'), us: t('compare.r6Us'), wx: t('compare.r6Wx'), tg: t('compare.r6Tg')
  },
])
</script>

<style scoped>
.seo-page { min-height: 100vh; background: var(--bg, #fff8f5); padding: 48px 20px; }
.seo-container { max-width: 800px; margin: 0 auto; }
h1 { font-size: 28px; margin-bottom: 8px; }
.seo-lead { color: var(--text-secondary, #666); margin-bottom: 32px; line-height: 1.6; }
.comparison-table { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; background: var(--panel, #fff); border-radius: 12px; overflow: hidden; border: 1px solid var(--surface-border); }
th, td { padding: 14px 16px; text-align: left; border-bottom: 1px solid var(--surface-border); font-size: 14px; }
th { background: #faf5f0; font-weight: 600; color: var(--text-primary); }
td:first-child { font-weight: 500; color: var(--text-primary); }
tr:last-child td { border-bottom: none; }
.highlight { color: var(--accent-2, #ff7a33); font-weight: 600; }
.compare-note { margin-top: 24px; color: var(--text-secondary, #666); font-size: 14px; line-height: 1.6; }
.seo-footer { max-width: 800px; margin: 48px auto 0; padding-top: 24px; border-top: 1px solid var(--surface-border); display: flex; gap: 24px; justify-content: center; }
.seo-footer a { color: var(--accent-1); text-decoration: none; }
.seo-footer a:hover { text-decoration: underline; }
</style>
