<template>
  <div class="page">
    <header class="site-header container">
      <div class="logo">
        <img :src="logoSvg" :alt="$t('app.brand')" class="brand-logo" />
        <span class="brand-text">{{ $t('app.brand') }}</span>
      </div>
      <nav class="nav">
        <router-link to="/">{{ $t('nav.intro') }}</router-link>
        <router-link to="/app">{{ $t('nav.webApp') }}</router-link>
      </nav>
      <div class="header-actions">
        <button class="btn" @click="showRequests = true" style="position:relative">
          👥
          <span v-if="friendRequests.length > 0" class="req-dot"></span>
        </button>
        <button class="btn reward" @click="showReward = true">{{ $t('header.reward') }}</button>
      </div>
    </header>

    <section class="hero">
      <div class="hero-inner container">
        <div class="hero-left">
          <div class="eyebrow">{{ $t('home.hero.eyebrow') }}</div>
          <h2 class="hero-title">{{ $t('home.hero.title') }}</h2>
          <p class="hero-lead">{{ $t('home.hero.lead') }}</p>

          <div class="cta">
            <a class="btn primary" :href="androidDownloadUrl" @click.prevent="handleDownload(androidDownloadUrl)" target="_blank" rel="noreferrer nofollow">{{ $t('home.hero.downloadApk') }}</a>
            <a class="btn ghost" :href="releasePage" target="_blank" rel="noreferrer">{{ $t('home.hero.downloadClient') }}</a>
            <router-link class="btn ghost" to="/app">{{ $t('home.hero.onlineTry') }}</router-link>
          </div>

          <div class="hero-highlights" aria-hidden="false">
            <div v-for="item in highlights" :key="item.title" class="highlight">
              <div class="h-icon">{{ item.icon }}</div>
              <div class="h-body">
                <div class="h-title">{{ item.title }}</div>
                <div class="h-sub">{{ item.sub }}</div>
              </div>
            </div>
          </div>
        </div>

        <div class="hero-right">
          <div class="shot-card">
            <div class="hero-right">
              <div class="demo-card">
                <div class="demo-header">
                  <div class="demo-badge">
                    <span class="demo-dot"></span>
                    {{ $t('home.demo.title') }}
                  </div>
                  <div class="demo-status">{{ $t('home.demo.status') }}</div>
                </div>

                <div class="demo-messages">
                  <div class="msg user">
                    <span class="msg-icon">👤</span>
                    <div class="msg-bubble">{{ $t('home.demo.userMessage') }}</div>
                  </div>
                  <div class="msg agent">
                    <span class="msg-icon">🤖</span>
                    <div class="msg-bubble agent-bubble" v-html="demoAgentMessage"></div>
                  </div>
                </div>

                <div class="demo-input">
                  <span class="input-placeholder">{{ $t('home.demo.inputPlaceholder') }}</span>
                  <span class="send-btn">{{ $t('home.demo.sendBtn') }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="features container">
      <h3 class="section-title">{{ $t('home.features.title') }}</h3>
      <div class="feature-grid">
        <div v-for="feature in features" :key="feature.title" class="feature">
          <div class="icon">{{ feature.icon }}</div>
          <div class="f-title">{{ feature.title }}</div>
          <p>{{ feature.desc }}</p>
        </div>
      </div>
    </section>

    <section class="downloads container">
      <h3 class="section-title">{{ $t('home.downloads.title') }}</h3>
      <div class="download-grid">
        <a v-for="item in downloads" :key="item.title" class="platform" :href="item.href" @click.prevent="handleDownload(item.href)" target="_blank" rel="noreferrer nofollow">
          {{ item.title }}
          <span class="platform-sub">{{ item.label }}</span>
        </a>
      </div>
      <p class="download-note">
        <a :href="releasePage" target="_blank" rel="noreferrer">{{ $t('home.downloads.note') }}</a>
      </p>
    </section>

    <section class="why-choose container">
      <h3 class="section-title">{{ $t('home.whyChoose.title') }}</h3>
      <p class="why-lead">{{ $t('home.whyChoose.lead') }}</p>

      <div class="why-grid">
        <div v-for="item in whyChooseItems" :key="item.title" class="why-card">
          <div class="why-icon">{{ item.icon }}</div>
          <div class="why-body">
            <div class="why-title">{{ item.title }}</div>
            <div class="why-sub">{{ item.sub }}</div>
          </div>
        </div>
      </div>
    </section>

    <footer class="site-footer">
      <div class="footer-inner">
        <div class="footer-copyright">{{ $t('footer.copyright') }}</div>
        <div class="footer-icp">
          <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">
            {{ siteConfig.ICP_NUMBER }}
          </a>
        </div>
        <div class="footer-links">
          <span class="footer-text">{{ $t('footer.policeRecord') }}</span>
        </div>
      </div>
    </footer>

    <!-- reward modal -->
    <div v-if="showReward" class="reward-modal">
      <div class="reward-mask" @click="closeReward"></div>
      <div class="reward-box">
        <button class="reward-close" @click="closeReward">✕</button>
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

<script setup>
import logoSvg from '../assets/logo.svg'
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import siteConfig from '../siteConfig'
import { getFriendRequests, acceptFriend } from '../api'

const { t } = useI18n()

const releasePage = siteConfig.RELEASE_BASE
const installerUrls = siteConfig.INSTALLER_URLS
const downloads = computed(() => [
  { title: t('home.downloads.windows'), href: installerUrls.windows, label: t('home.downloads.msi') },
  { title: t('home.downloads.linux'), href: installerUrls.linux, label: t('home.downloads.deb') },
  { title: t('home.downloads.macos'), href: installerUrls.macos, label: t('home.downloads.dmg') },
  { title: t('home.downloads.android'), href: installerUrls.android, label: t('home.downloads.apk') }
])
const androidDownloadUrl = installerUrls.android

const highlights = computed(() => [
  { icon: '🔗', title: t('home.hero.highlight1Title'), sub: t('home.hero.highlight1Sub') },
  { icon: '🎨', title: t('home.hero.highlight2Title'), sub: t('home.hero.highlight2Sub') },
  { icon: '🤖', title: t('home.hero.highlight3Title'), sub: t('home.hero.highlight3Sub') }
])

const features = computed(() => [
  { icon: '💬', title: t('home.features.feature1Title'), desc: t('home.features.feature1Desc') },
  { icon: '🔒', title: t('home.features.feature2Title'), desc: t('home.features.feature2Desc') },
  { icon: '🤖', title: t('home.features.feature3Title'), desc: t('home.features.feature3Desc') }
])

const whyChooseItems = computed(() => [
  { icon: '⚡', title: t('home.whyChoose.point1Title'), sub: t('home.whyChoose.point1Sub') },
  { icon: '💾', title: t('home.whyChoose.point2Title'), sub: t('home.whyChoose.point2Sub') },
  { icon: '🤖', title: t('home.whyChoose.point3Title'), sub: t('home.whyChoose.point3Sub') },
  { icon: '📦', title: t('home.whyChoose.point4Title'), sub: t('home.whyChoose.point4Sub') },
  { icon: '🖥️', title: t('home.whyChoose.point5Title'), sub: t('home.whyChoose.point5Sub') },
  { icon: '⚙️', title: t('home.whyChoose.point6Title'), sub: t('home.whyChoose.point6Sub') }
])

const demoAgentMessage = computed(() => {
  const summary = t('home.demo.agentSummary', { count: 3 })
  const xiaoming = t('home.demo.contactXiaoMing')
  const design = t('home.demo.contactDesign')
  const product = t('home.demo.contactProduct')
  const progress = t('home.demo.agentProgress')
  const upload = t('home.demo.agentUpload')
  const review = t('home.demo.agentReview')
  return `${summary}<br />• 14:30 <strong>${xiaoming}</strong>：${progress}<br />• 15:20 <strong>${design}</strong>：${upload}<br />• 16:05 <strong>${product}</strong>：${review}`
})

const showReward = ref(false)
const selectedMethod = ref('alipay')
const authorEmail = ref(siteConfig.AUTHOR_EMAIL)
const copyStatus = ref(null) // null | 'copied' | 'failed'

const showRequests = ref(false)
const friendRequests = ref([]) // array of user objects

function closeReward() { showReward.value = false }
function selectMethod(method) { selectedMethod.value = method }

async function copyEmail() {
  try {
    await navigator.clipboard.writeText(authorEmail.value)
    copyStatus.value = 'copied'
  } catch (e) {
    copyStatus.value = 'failed'
  }
  setTimeout(() => { copyStatus.value = null }, 3000)
}

async function loadFriendRequests() {
  try {
    const list = await getFriendRequests()
    friendRequests.value = Array.isArray(list) ? list : []
  } catch (e) {
    friendRequests.value = []
  }
}

async function handleAccept(requesterId, idx) {
  const ok = await acceptFriend(requesterId)
  if (ok) {
    // remove from list
    friendRequests.value.splice(idx, 1)
  } else {
    window.$toast.error(t('sidebar.frAcceptFailed'))
  }
}

function handleDownload(url) {
  // 给URL添加随机时间戳参数，防止浏览器缓存重定向结果
  const timestamp = Date.now()
  const separator = url.includes('?') ? '&' : '?'
  const urlWithTimestamp = `${url}${separator}t=${timestamp}`
  window.open(urlWithTimestamp, '_blank', 'noopener noreferrer')
}

onMounted(() => {
  loadFriendRequests()
})
</script>
<style>
.logo {
  display: flex;
  align-items: center;      /* 垂直居中 */
  gap: 8px;                 /* 图标和文字之间的间距，按需调整 */
}

.brand-logo {
  width: 40px;
  height: 40px;
  display: block;
}

.brand-text {
  font-weight: 800;
  font-size: 20px;
  line-height: 1;
}

.site-header .nav a {
  margin-left: 18px;
  color: var(--muted);
  text-decoration: none;
  padding: 6px 10px;
  border-radius: 8px
}

.site-header .nav a.router-link-active {
  background: rgba(255, 122, 51, 0.08);
  color: var(--accent-2)
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn.reward {
  background: transparent;
  border: 1px solid rgba(51, 65, 85, 0.08);
  color: var(--muted);
}

.top-ribbon {
  width: 100%;
  height: 56px;
  background: linear-gradient(90deg, var(--accent-1), var(--accent-2));
  box-shadow: 0 10px 30px rgba(255, 122, 51, 0.12);
  border-radius: 10px;
  margin: 12px 0 18px
}

.top-ribbon .ribbon-inner {
  height: 100%
}

.hero {
  color: #fff;
  padding: 48px 0;
  border-radius: 16px;
  margin: 0 0 20px;
  position: relative;
  overflow: visible;
  /* 保留原始线性渐变为底色，然后在右下角叠加一个更深的径向渐变以加深右下角 */
  background-image:
    radial-gradient(600px 360px at 88% 88%, rgba(255, 122, 51, 0.20) 0%, rgba(255, 122, 51, 0.08) 30%, rgba(255, 122, 51, 0) 70%),
    linear-gradient(135deg, var(--accent-1) 0%, var(--accent-2) 60%);
  background-repeat: no-repeat;
  background-size: cover;
}

.hero-inner {
  display: flex;
  gap: 28px;
  align-items: flex-start;
}

.hero-left {
  flex: 1;
  padding-top: 30px;
}

.eyebrow {
  font-size: 13px;
  opacity: 0.95;
  margin-bottom: 8px
}

.hero-title {
  font-size: 32px;
  margin: 0 0 10px;
  line-height: 1.05
}

.hero-lead {
  opacity: 0.95;
  max-width: 560px
}

.cta {
  margin-top: 18px
}

.btn {
  display: inline-block;
  padding: 10px 16px;
  border-radius: 10px;
  margin-right: 10px;
  text-decoration: none;
  font-weight: 600
}

.btn.primary {
  background: #fff;
  color: var(--accent-2);
}

.btn.ghost {
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.6);
  color: #fff;
  transition: all 0.2s ease;
}
.btn.ghost:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.8);
}

.hero-highlights {
  display: flex;
  gap: 12px;
  margin-top: 26px
}

.highlight {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  background: rgba(255, 255, 255, 0.14);
  padding: 14px 16px;
  border-radius: 14px;
  flex: 1;
  min-width: 0;
  border: 1px solid rgba(255, 255, 255, 0.18);
  box-shadow: 0 10px 30px rgba(15, 15, 15, 0.12);
  backdrop-filter: blur(8px) saturate(1.06);
  -webkit-backdrop-filter: blur(8px) saturate(1.06);
  transition: transform .18s ease, box-shadow .18s ease
}

.highlight:hover {
  transform: translateY(-6px);
  box-shadow: 0 18px 40px rgba(15, 15, 15, 0.14)
}

.h-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.22);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;        /* 稍微加大，视觉更饱满 */
  line-height: 1;          /* 杀死默认行高带来的上下空隙 */
  color: #ffffff;          /* 让图标更亮，与深色背景拉开层次 */
  box-shadow: inset 0 -2px 6px rgba(0, 0, 0, 0.06);
}

.h-body {
  min-width: 0
}

.h-title {
  font-weight: 700;
  font-size: 15px;
  color: rgba(17, 24, 39, 0.95)
}

.h-sub {
  font-size: 13px;
  color: rgba(17, 24, 39, 0.72);
  margin-top: 6px
}

.hero-right {
  width: 420px
}

.shot-card {
  display: flex;
  flex-direction: column;
  gap: 12px
}

.shot-card img {
  width: 100%;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 20px 40px rgba(15, 15, 15, 0.18);
  border: 4px solid rgba(255, 255, 255, 0.06)
}

.features {
  padding: 28px 0
}

.section-title {
  margin-bottom: 14px
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px
}

.feature {
  background: var(--panel);
  padding: 18px;
  border-radius: 12px;
  box-shadow: 0 6px 18px rgba(15, 15, 15, 0.04);
  text-align: center
}

.feature .icon {
  font-size: 28px;
  margin-bottom: 8px
}

.feature .f-title {
  font-weight: 700;
  margin-bottom: 6px
}

.downloads {
  padding: 20px 0
}

.download-grid {
  display: flex;
  gap: 14px
}

.platform {
  flex: 1;
  background: var(--panel);
  padding: 14px;
  border-radius: 10px;
  text-align: center;
  color: var(--muted);
  text-decoration: none;
  box-shadow: 0 6px 14px rgba(15, 15, 15, 0.04)
}

.why-choose {
  background: var(--panel);
  border-radius: 24px;
  padding: 32px 28px;
  margin: 40px auto;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(0, 0, 0, 0.04);
}

.why-lead {
  font-size: 1rem;
  color: var(--muted);
  margin-bottom: 32px;
  padding-bottom: 8px;
  border-bottom: 2px solid var(--accent-2);
  display: inline-block;
}

.why-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.why-card {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  padding: 8px 4px;
  transition: all 0.2s ease;
}

.why-card:hover {
  transform: translateX(4px);
}

.why-icon {
  width: 44px;
  height: 44px;
  background: rgba(255, 122, 51, 0.1);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}

.why-title {
  font-weight: 700;
  font-size: 1rem;
  margin-bottom: 6px;
}

.why-sub {
  font-size: 0.85rem;
  color: var(--muted);
  line-height: 1.4;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .container {
    padding: 0 16px;
  }

  /* Header适配 */
  .site-header {
    flex-wrap: wrap;
    padding: 12px 0;
  }
  .site-header .nav {
    order: 3;
    width: 100%;
    justify-content: center;
    margin-top: 8px;
  }
  .site-header .nav a {
    margin: 0 8px;
  }
  .header-actions {
    margin-left: auto;
  }

  /* Hero区域适配 */
  .hero {
    padding: 32px 16px;
    margin: 0 12px 20px;
    background-image: linear-gradient(135deg, var(--accent-1) 0%, var(--accent-2) 60%);
    background-size: auto;
  }
  .hero-inner {
    flex-direction: column;
    gap: 32px;
    width: 100%;
    max-width: 100%;
    overflow: hidden;
  }
  .hero-left {
    padding-top: 0;
    width: 100%;
    max-width: 100%;
  }
  .hero-title {
    font-size: 28px;
  }
  .hero-highlights {
    flex-direction: column;
    gap: 12px;
    width: 100%;
    max-width: 100%;
    box-sizing: border-box;
  }
  .highlight {
    box-sizing: border-box;
    margin: 0 auto;
  }
  .hero-right {
    width: 100% !important;
    max-width: 100%;
    margin: 0 auto;
    box-sizing: border-box;
  }
  .demo-card {
    margin: 0 auto;
    max-width: 100%;
    width: 100%;
    box-sizing: border-box;
  }

  /* 功能区域适配 */
  .feature-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  /* 下载区域适配 */
  .download-grid {
    flex-direction: column;
    gap: 12px;
  }

  /* Why选择区域适配 */
  .why-grid {
    grid-template-columns: 1fr;
    gap: 20px;
  }
  .why-choose {
    margin: 20px 16px;
    padding: 24px 20px;
  }
  .why-lead {
    font-size: 0.95rem;
  }

  /* CTA按钮适配 */
  .cta {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .btn {
    text-align: center;
    margin-right: 0;
    padding: 12px 16px;
  }
  .btn.primary {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  }
  .btn.ghost {
    background: rgba(255, 255, 255, 0.1);
    border: 1px solid rgba(255, 255, 255, 0.7);
  }
}

@media (max-width: 768px) {
  /* 各section在移动端保持左右边距 */
  .features.container,
  .downloads.container,
  .why-choose.container {
    padding-left: 16px;
    padding-right: 16px;
  }
}

@media (max-width: 480px) {
  .hero {
    margin: 0 12px 16px;
    padding: 24px 16px;
    background-image: linear-gradient(135deg, var(--accent-1) 0%, var(--accent-2) 60%);
  }
  .hero-title {
    font-size: 22px;
  }
  .hero-lead {
    font-size: 14px;
  }
  .demo-card {
    max-width: 100%;
  }
  .site-header {
    padding: 12px 16px;
  }
  .features.container,
  .downloads.container {
    padding: 20px 16px;
  }
  .why-choose.container {
    margin: 20px 12px;
    padding: 20px 16px;
  }
}

/* 超小屏幕适配 */
@media (max-width: 375px) {
  .hero-title {
    font-size: 20px;
  }
  .highlight {
    padding: 12px;
  }
  .h-icon {
    width: 40px;
    height: 40px;
    font-size: 20px;
  }
  .h-title {
    font-size: 14px;
  }
  .h-sub {
    font-size: 12px;
  }
  .demo-card {
    transform: scale(0.9);
    transform-origin: center center;
    margin: 0 auto;
  }
  .demo-messages {
    min-height: 220px;
    padding: 16px;
  }
}

/* 右侧演示卡片 */
.hero-right {
  width: 420px;
}

.demo-card {
  background: #fff;
  border-radius: 24px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  overflow: hidden;
  backdrop-filter: blur(0px);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.demo-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 30px 60px -12px rgba(0, 0, 0, 0.3);
}

.demo-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: #f8fafc;
  border-bottom: 1px solid #eef2f6;
  gap: 12px;
}

.demo-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: clamp(11px, 2.5vw, 13px);
  font-weight: 600;
  background: #eef2ff;
  padding: 5px 12px;
  border-radius: 40px;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 0;
}

.demo-dot {
  width: 8px;
  height: 8px;
  background: #10b981;
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0% {
    opacity: 0.4;
    transform: scale(0.8);
  }

  100% {
    opacity: 1;
    transform: scale(1.2);
  }
}

.demo-status {
  font-size: clamp(10px, 2vw, 12px);
  color: #475569;
  background: #f1f5f9;
  padding: 4px 10px;
  border-radius: 30px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 1;
  min-width: 0;
}

.demo-messages {
  padding: 20px;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: 260px;
}

.msg {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.msg-icon {
  width: 32px;
  height: 32px;
  background: #f1f5f9;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}

.msg-bubble {
  background: #f1f5f9;
  padding: 10px 14px;
  border-radius: 18px;
  border-top-left-radius: 4px;
  font-size: 13px;
  line-height: 1.5;
  color: #0f172a;
  max-width: 85%;
}

.agent {
  flex-direction: row;
}

.agent-bubble {
  background: #eff6ff;
  border-top-left-radius: 18px;
  border-top-right-radius: 4px;
  color: #0c4a6e;
}

.agent .msg-bubble {
  border-top-left-radius: 18px;
  border-top-right-radius: 4px;
}

.user {
  flex-direction: row-reverse;
}

.user .msg-bubble {
  background: #ff7a33;
  color: white;
  border-top-left-radius: 18px;
  border-top-right-radius: 4px;
}

.demo-input {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: #fff;
  border-top: 1px solid #edf2f7;
  gap: 12px;
}

.input-placeholder {
  font-size: 13px;
  color: #94a3b8;
  flex: 1;
}

.send-btn {
  background: #ff7a33;
  color: white;
  padding: 6px 16px;
  border-radius: 30px;
  font-size: 12px;
  font-weight: 500;
  cursor: default;
  opacity: 0.7;
}

.site-footer {
  background: linear-gradient(180deg, #fbfdff 0%, #f7fafc 100%);
  border-top: 1px solid rgba(226,232,240,0.9);
  padding: 30px 20px;
  margin-top: 48px;
  font-size: 13px;
  color: #334155;
  box-shadow: 0 -8px 28px rgba(2,6,23,0.03);
  transition: background .18s ease, color .18s ease;
}

.footer-inner {
  max-width: 1100px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

/* reward modal styles */
.reward-modal {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1200;
}
.reward-mask {
  position: absolute;
  inset: 0;
  background: rgba(0,0,0,0.5);
}
.reward-box {
  position: relative;
  background: #fff;
  border-radius: 12px;
  padding: 18px;
  max-width: 90vw;
  max-height: 90vh;
  box-shadow: 0 20px 50px rgba(0,0,0,0.3);
  z-index: 1201;
}
.reward-img {
  display: block;
  max-width: 80vw;
  max-height: 70vh;
  border-radius: 8px;
}
.reward-close {
  position: absolute;
  right: 8px;
  top: 8px;
  background: transparent;
  border: none;
  font-size: 18px;
  cursor: pointer;
}

.req-dot {
  position: absolute;
  right: 6px;
  top: 6px;
  width: 10px;
  height: 10px;
  background: #ff3b30;
  border-radius: 50%;
  box-shadow: 0 0 0 3px rgba(255,59,48,0.12);
}

.author-email {
  margin: 6px 0 12px;
  color: #334155;
  font-size: 14px;
}
.author-email .copy-btn {
  margin-left: 8px;
  padding: 4px 8px;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  background: #fff;
  cursor: pointer;
}

.payment-grid {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}
.payment-item {
  flex: 1;
  text-align: center;
  padding: 8px;
  border-radius: 8px;
  border: 1px solid #eef2f7;
  cursor: pointer;
  transition: transform .12s ease, box-shadow .12s ease;
}
.payment-item.active {
  box-shadow: 0 8px 24px rgba(17,24,39,0.08);
  transform: translateY(-4px);
  border-color: rgba(255,122,51,0.14);
}
.payment-img {
  width: 160px;
  height: 160px;
  object-fit: cover;
  border-radius: 8px;
  display: block;
  margin: 0 auto 8px;
}
.payment-label {
  font-weight: 600;
  color: #0f172a;
}

.footer-copyright {
  font-weight: 500;
  color: #334155;
}

.footer-icp a {
  color: #475569;
  text-decoration: none;
  transition: color 0.18s ease, border-color 0.18s ease;
  border-bottom: 1px dashed rgba(203,213,225,0.9);
  padding-bottom: 2px;
}

.footer-icp a:hover {
  color: #ff7a33;
  border-bottom-color: rgba(255,122,51,0.8);
}

.footer-links {
  display: flex;
  gap: 16px;
  align-items: center;
}

.footer-text {
  color: #64748b;
  font-size: 12.5px;
}

/* 可选的脚注文本样式，若需额外说明可在模板中加入 .footer-note */
.footer-note {
  width: 100%;
  text-align: center;
  margin-top: 10px;
  color: #94a3b8;
  font-size: 12px;
}

@media (max-width: 760px) {
  .footer-inner {
    flex-direction: column;
    text-align: center;
    gap: 8px;
  }
  .footer-icp,
  .footer-links {
    width: 100%;
    justify-content: center;
  }
}
</style>
