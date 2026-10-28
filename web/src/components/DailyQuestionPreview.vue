<template>
  <section class="daily-preview" :aria-label="$t('product.example')">
    <header><span>{{ $t('product.demo.discover.name') }}</span><small>{{ $t('product.example') }}</small></header>
    <h3>{{ $t('product.demo.discover.first') }}</h3>
    <article v-for="person in ['one','two']" :key="person">
      <p>{{ $t(`product.dailyPreview.${person}.answer`) }}</p>
      <div class="answer-author"><span class="paper-avatar small" :class="{sage:person==='one'}">{{ $t(`product.dailyPreview.${person}.initial`) }}</span><span>{{ $t(`product.dailyPreview.${person}.name`) }}</span></div>
    </article>
  </section>
</template>
<style scoped>
.daily-preview{background:var(--panel);border:1px solid var(--surface-border);border-radius:18px;padding:28px;color:var(--text-primary)}
header{display:flex;align-items:center;justify-content:space-between;gap:14px;flex-wrap:wrap}
header>span{color:var(--sage-ink);font-size:12px}header small{color:var(--muted);font-size:10px}
h3{font-size:25px;font-weight:600;line-height:1.6;letter-spacing:-.025em;margin:24px 0 12px;max-width:310px}
article{padding:20px 0;border-top:1px solid var(--surface-border)}article:last-child{padding-bottom:0}
article p{font-size:14px;line-height:1.9;margin:0 0 13px;max-width:340px}
.answer-author{display:flex;gap:9px;align-items:center;color:var(--muted);font-size:12px}
@media(max-width:640px){.daily-preview{padding:22px}h3{font-size:23px}}
</style>
