<template>
  <div class="home-page">
    <header class="home-header home-container">
      <router-link to="/" class="brand-link" :aria-label="$t('app.brand')"><BrandMark /></router-link>
      <nav :aria-label="$t('product.navigation')"><a href="#moments">{{ $t('product.moments') }}</a><a href="#downloads">{{ $t('home.hero.downloadClient') }}</a><router-link to="/app" class="home-open">{{ $t('product.open') }} <span aria-hidden="true">↗</span></router-link></nav>
    </header>
    <main>
      <section class="home-hero home-container">
        <div class="hero-copy"><span class="home-eyebrow">{{ $t('product.eyebrow') }}</span>
          <h1>{{ $t('product.headline') }}<span>{{ $t('product.headlineSecond') }}</span></h1>
          <p class="home-lead">{{ $t('product.lead') }}</p>
          <div class="home-cta"><router-link to="/app" class="paper-button primary">{{ $t('product.open') }} <span aria-hidden="true">↗</span></router-link><a href="#downloads" class="paper-button secondary">{{ $t('home.hero.downloadClient') }}</a></div>
          <p class="hero-footnote">{{ $t('product.browserNote') }}</p>
        </div>
        <div class="hero-conversation"><ConversationPreview :animate="false" /></div>
      </section>
      <section id="moments" class="home-moments home-container">
        <div class="section-heading"><span class="home-eyebrow">{{ $t('product.moments') }}</span><h2>{{ $t('product.momentsTitle') }}</h2><p>{{ $t('product.momentsLead') }}</p></div>
        <div class="moments-layout">
          <div class="moment-options" role="tablist" :aria-label="$t('product.moments')" aria-orientation="vertical">
            <button v-for="(scene,index) in scenes" :key="scene" :id="`moment-${scene}`" role="tab" :aria-selected="selected===scene" :tabindex="selected===scene?0:-1" aria-controls="moment-panel" @click="selected=scene" @keydown="onSceneKey($event,index)"><span class="moment-number">0{{ index+1 }}</span><span><strong>{{ $t(`product.scenes.${scene}.title`) }}</strong><small>{{ $t(`product.scenes.${scene}.body`) }}</small></span></button>
          </div>
          <div id="moment-panel" role="tabpanel" :aria-labelledby="`moment-${selected}`" class="moment-example"><DailyQuestionPreview v-if="selected==='discover'" /><ConversationPreview v-else :scene="selected" :key="selected" compact /></div>
        </div>
      </section>
      <section id="downloads" class="home-downloads home-container">
        <div><span class="home-eyebrow">{{ $t('product.takeAlong') }}</span><h2>{{ $t('product.downloadTitle') }}</h2><p>{{ $t('product.downloadLead') }}</p></div>
        <div class="download-options"><a v-for="item in downloads" :key="item.title" :href="item.href" target="_blank" rel="noopener noreferrer" @click.prevent="handleDownload(item.href)"><span>{{ item.title }}<small>{{ item.label }}</small></span><span aria-hidden="true">↓</span></a></div>
        <a :href="releasePage" target="_blank" rel="noopener noreferrer" class="release-link">{{ $t('home.downloads.note') }} ↗</a>
      </section>
      <section class="home-goodbye home-container"><h2>{{ $t('product.goodbye') }}</h2><router-link to="/app" class="paper-button primary">{{ $t('product.open') }} →</router-link></section>
    </main>
    <footer class="home-footer home-container"><BrandMark /><p>{{ $t('product.footerNote') }}</p><div class="footer-actions"><router-link to="/features">{{ $t('features.title') }}</router-link><router-link to="/faq">{{ $t('product.help') }}</router-link><button @click="showReward=true">{{ $t('header.reward') }}</button><a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">{{ siteConfig.ICP_NUMBER }}</a></div><small>{{ $t('footer.copyright') }} · {{ $t('footer.policeRecord') }}</small></footer>
    <!-- reward modal -->
    <div v-if="showReward" class="reward-modal">
      <div class="reward-mask" @click="closeReward"></div>
      <div ref="rewardDialog" class="reward-box" role="dialog" aria-modal="true" :aria-label="$t('reward.title')" tabindex="-1">
        <button class="reward-close" @click="closeReward" :aria-label="$t('social.close')">✕</button>
        <h3 style="margin:0 0 8px">{{ $t('reward.title') }}</h3>
        <p class="author-email">{{ $t('reward.authorEmail') }}<span>{{ authorEmail }}</span>
          <button class="copy-btn" @click="copyEmail" :disabled="copyStatus === 'copied'">
            {{ copyStatus === 'copied' ? $t('reward.copied') : copyStatus === 'failed' ? $t('reward.copyFailed') : $t('reward.copy') }}
          </button>
        </p>

        <div class="payment-grid">
          <div class="payment-item" :class="{active: selectedMethod === 'alipay'}" @click="selectMethod('alipay')">
            <img :src="siteConfig.PAYMENT_QRCODES.alipay" :alt="$t('reward.alipay')" class="payment-img" />
            <div class="payment-label">{{ $t('reward.alipay') }}</div>
          </div>
          <div class="payment-item" :class="{active: selectedMethod === 'wechat'}" @click="selectMethod('wechat')">
            <img :src="siteConfig.PAYMENT_QRCODES.wechat" :alt="$t('reward.wechat')" class="payment-img" />
            <div class="payment-label">{{ $t('reward.wechat') }}</div>
          </div>
        </div>

        <div class="reward-note">{{ $t('reward.note') }}</div>
      </div>
    </div>

  </div>
</template>
<script setup lang="ts">
import {ref,computed} from 'vue'
import {useI18n} from 'vue-i18n'
import BrandMark from '../components/BrandMark.vue'
import ConversationPreview from '../components/ConversationPreview.vue'
import DailyQuestionPreview from '../components/DailyQuestionPreview.vue'
import siteConfig from '../siteConfig'
import {useDialog} from '../composables/useDialog'
const {t}=useI18n()
const scenes=['friends','discover','assistant'] as const
const selected=ref<typeof scenes[number]>('discover')
function onSceneKey(event:KeyboardEvent,index:number){
  const direction=event.key==='ArrowDown'||event.key==='ArrowRight'?1:event.key==='ArrowUp'||event.key==='ArrowLeft'?-1:0
  if(!direction && event.key!=='Home' && event.key!=='End')return
  event.preventDefault()
  selected.value=scenes[event.key==='Home'?0:event.key==='End'?2:(index+direction+scenes.length)%scenes.length]
  document.getElementById(`moment-${selected.value}`)?.focus()
}
const releasePage=siteConfig.RELEASE_BASE
const downloads=computed(()=>[
  {title:t('home.downloads.windows'),href:siteConfig.INSTALLER_URLS.windows,label:t('home.downloads.msi')},
  {title:t('home.downloads.macos'),href:siteConfig.INSTALLER_URLS.macos,label:t('home.downloads.dmg')},
  {title:t('home.downloads.linux'),href:siteConfig.INSTALLER_URLS.linux,label:t('home.downloads.deb')},
  {title:t('home.downloads.android'),href:siteConfig.INSTALLER_URLS.android,label:t('home.downloads.apk')}
])
const showReward = ref(false)
const rewardDialog=ref<HTMLElement|null>(null)
useDialog(rewardDialog,closeReward)
const selectedMethod = ref('alipay')
const authorEmail = ref(siteConfig.AUTHOR_EMAIL)
const copyStatus = ref<'copied' | 'failed' | null>(null) // null | 'copied' | 'failed'

function closeReward() { showReward.value = false }
function selectMethod(method: string) { selectedMethod.value = method }

async function copyEmail() {
  try {
    await navigator.clipboard.writeText(authorEmail.value)
    copyStatus.value = 'copied'
  } catch (e) {
    copyStatus.value = 'failed'
  }
  setTimeout(() => { copyStatus.value = null }, 3000)
}

function handleDownload(url: string) {
  // 给URL添加随机时间戳参数，防止浏览器缓存重定向结果
  const timestamp = Date.now()
  const separator = url.includes('?') ? '&' : '?'
  const urlWithTimestamp = `${url}${separator}t=${timestamp}`
  window.open(urlWithTimestamp, '_blank', 'noopener noreferrer')
}

</script>
<style scoped>
.home-page{overflow:clip}
.home-container{width:min(1120px,calc(100% - 64px));margin-inline:auto;box-sizing:border-box}
.home-header{display:flex;align-items:center;justify-content:space-between;padding-block:28px;gap:20px}
.brand-link{text-decoration:none}
.home-header nav{display:flex;align-items:center;gap:30px}
.home-header nav a{font-size:13px;text-decoration:none;color:var(--text-secondary)}
.home-header nav .home-open{color:var(--text-primary);border-bottom:1px solid var(--surface-border-strong);padding:10px 0}
.home-hero{display:grid;grid-template-columns:1fr 1fr;align-items:start;gap:100px;padding-block:58px 110px}
.hero-copy{padding-top:58px}
.home-eyebrow{font-size:11px;letter-spacing:.08em;color:var(--muted)}
.home-hero .home-eyebrow{display:inline-block;border-left:2px solid var(--accent-1);padding-left:10px}
.home-hero h1{font-size:clamp(36px,4.5vw,58px);letter-spacing:-.045em;line-height:1.32;font-weight:600;margin:24px 0}
.home-hero h1 span{display:block}
.home-lead{font-size:16px;line-height:1.9;max-width:380px;color:var(--text-secondary)}
.home-cta{display:flex;gap:12px;flex-wrap:wrap;margin-top:30px}
.hero-footnote{font-size:12px;color:var(--muted);margin:16px 0 0}
.hero-conversation{padding-top:0}
.home-moments{padding-block:55px 90px;scroll-margin-top:32px;border-top:1px solid var(--surface-border)}
.section-heading{max-width:620px}
.section-heading h2,.home-downloads h2,.home-goodbye h2{font-size:clamp(25px,2.8vw,32px);letter-spacing:-.035em;line-height:1.5;font-weight:600;margin:13px 0}
.section-heading p,.home-downloads p{color:var(--muted);font-size:14px;line-height:1.9}
.moments-layout{display:grid;grid-template-columns:1fr 1fr;gap:100px;align-items:start;margin-top:38px}
.moment-options{display:grid}
.moment-options button{display:flex;align-items:flex-start;gap:22px;padding:25px 0;border:0;border-bottom:1px solid var(--surface-border);text-align:left;color:var(--text-primary);background:transparent;cursor:pointer}
.moment-options button:first-child{padding-top:10px}
.moment-options button:last-child{border-bottom:0}
.moment-options button[aria-selected=true] .moment-number{color:var(--action)}
.moment-options button[aria-selected=true] .moment-number::after{content:'';display:block;width:18px;height:2px;background:var(--action);margin-top:10px}
.moment-options button:hover strong{color:var(--action)}
.moment-number{color:var(--muted);font-size:12px;padding-top:5px;font-variant-numeric:tabular-nums}
.moment-options button>span:nth-child(2){flex:1}
.moment-options strong{display:block;font-size:17px;font-weight:600;line-height:1.6}
.moment-options small{display:block;max-width:340px;font-size:13px;color:var(--muted);line-height:1.9;margin-top:8px}
.moment-example{width:100%;max-width:430px;min-height:440px;justify-self:end}
.home-downloads{border-top:1px solid var(--surface-border);padding-block:55px 65px;scroll-margin-top:30px}
.download-options{display:grid;grid-template-columns:repeat(4,1fr);gap:28px;margin-top:28px}
.download-options a{display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid var(--surface-border-strong);padding:20px 0;color:var(--text-primary);text-decoration:none;font-size:15px}
.download-options a:hover{border-color:var(--action)}
.download-options small{display:block;font-size:11px;color:var(--muted);margin-top:7px}
.release-link{display:inline-block;color:var(--muted);font-size:12px;margin-top:20px}
.home-goodbye{display:flex;align-items:center;justify-content:space-between;gap:24px;padding-block:48px;border-top:1px solid var(--surface-border);margin-bottom:65px}
.home-goodbye h2{margin:0;font-weight:500}
.home-goodbye .paper-button{flex-shrink:0}
.home-footer{display:flex;align-items:center;flex-wrap:wrap;gap:22px;padding-bottom:32px}
.home-footer p{font-size:12px;color:var(--muted)}
.footer-actions{margin-left:auto;display:flex;flex-wrap:wrap;gap:20px}
.footer-actions a,.footer-actions button{font:inherit;font-size:12px;color:var(--muted);background:none;border:0;padding:0;text-decoration:none;cursor:pointer}
.home-footer>small{width:100%;font-size:10px;color:var(--muted)}
.reward-modal{position:fixed;inset:0;z-index:1000;display:grid;place-items:center;padding:20px}.reward-mask{position:absolute;inset:0;background:#211d1a80}.reward-box{position:relative;background:var(--panel);padding:28px;border-radius:22px;width:min(460px,100%);box-sizing:border-box}.reward-close{position:absolute;right:14px;top:14px;border:0;background:none;color:var(--text-primary);font-size:20px;cursor:pointer}.author-email{font-size:13px;overflow-wrap:anywhere;line-height:1.8}.copy-btn{border:0;background:var(--apricot);color:var(--on-apricot);padding:5px 9px;border-radius:8px;margin-left:6px;cursor:pointer}.payment-grid{display:flex;gap:18px;margin:22px 0}.payment-item{flex:1;min-width:0;text-align:center}.payment-img{width:100%;border-radius:12px}.payment-label,.reward-note{font-size:12px;color:var(--muted);line-height:1.8}

@media(max-width:850px){
  .home-hero{gap:36px;padding-block:35px 75px}.hero-copy{padding-top:24px}
  .moments-layout{gap:36px}.home-header nav{gap:16px}
}
@media(max-width:640px){
  .home-container{width:calc(100% - 40px)}.home-header{padding-block:22px}
  .home-header nav>a:not(.home-open){display:none}
  .home-hero{grid-template-columns:1fr;gap:36px;padding-block:26px 65px}
  .hero-copy{max-width:460px;padding-top:0}.home-hero h1{font-size:38px}
  .hero-conversation{max-width:420px;width:100%;justify-self:center}
  .moments-layout{grid-template-columns:1fr;gap:26px;margin-top:25px}
  .moment-options button{padding:20px 0;gap:18px}.moment-options button:first-child{padding-top:0}
  .moment-example{min-height:0;justify-self:center}
  .home-moments{padding-block:35px 50px}.download-options{grid-template-columns:repeat(2,1fr);gap:4px 24px}
  .home-downloads{padding-block:35px 42px}.home-goodbye{flex-direction:column;align-items:flex-start;margin-bottom:35px;padding-block:34px}
  .home-footer{gap:14px}.footer-actions{width:100%;margin-left:0;gap:14px}
  .home-lead{font-size:15px}.home-cta{gap:8px}.home-cta .paper-button{padding:13px 15px}
}
</style>
