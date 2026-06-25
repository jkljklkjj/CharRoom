<template>
  <div class="page">
    <header class="site-header container">
      <div class="logo">
        <img src="/src/assets/logo.svg" :alt="$t('app.brand')" class="brand-logo" />
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
