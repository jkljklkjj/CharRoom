<template>
  <div class="verify-wrap">
    <div class="panel">
      <h3>{{ $t('verify.title') }}</h3>
      <i18n-t v-if="email" keypath="verify.sentTo" tag="p">
        <template #email>
          <strong>{{ email }}</strong>
        </template>
      </i18n-t>
      <p v-else>{{ $t('verify.noPending') }}</p>
      <input v-model="code" :placeholder="$t('verify.placeholder')" />

      <div class="actions">
        <button class="primary" :disabled="submitting" @click="submit">
          {{ submitting ? t('verify.submitting') : t('verify.submit') }}
        </button>
        <button class="toggle-mode" :disabled="cooldown > 0" @click="resend">
          <span v-if="cooldown <= 0">{{ $t('verify.resend') }}</span>
          <span v-else>{{ $t('verify.resendCooldown', { seconds: cooldown }) }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import api from '../api'
import { useStore } from '../store'

const { t } = useI18n()
const code = ref('')
const router = useRouter()
const store = useStore()
const email = ref('')
let password = ''
const submitting = ref(false)
const cooldown = ref(0)
let cooldownTimer = null

onMounted(() => {
  const pending = store.state.pendingRegister
  if (!pending || !pending.email || !pending.password) {
    window.$toast.warning(t('verify.noPendingInfo'))
    router.push({ name: 'WebApp' })
    return
  }
  email.value = pending.email
  password = pending.password
  // 读取本地冷却（由发送验证码时设置），启动倒计时
  try {
    const key = 'verify:email:cooldown:' + email.value
    const expireAt = parseInt(localStorage.getItem(key) || '0')
    const now = Date.now()
    if (expireAt > now) {
      startCooldown(Math.ceil((expireAt - now) / 1000))
    }
  } catch (e) {}
})

onBeforeUnmount(() => {
  if (cooldownTimer) clearInterval(cooldownTimer)
})

async function submit() {
  if (!code.value) { window.$toast.warning(t('verify.inputCode')); return }
  if (submitting.value) return
  submitting.value = true
  try {
    const id = await api.verifyRegister(email.value, code.value, password)
    if (id && id !== -1) {
      window.$toast.success(t('verify.success'))
      store.clearPendingRegister()
      router.push({ name: 'WebApp' })
    } else {
      window.$toast.error(t('verify.failed'))
    }
  } finally {
    submitting.value = false
  }
}

async function resend() {
  if (!email.value) return
  if (cooldown.value > 0) return
  const ok = await api.sendVerifyCode(email.value)
  if (ok) {
    // 本地记录冷却到期时间（120s）并启动倒计时
    try { localStorage.setItem('verify:email:cooldown:' + email.value, String(Date.now() + 120000)); } catch (e) {}
    startCooldown(120)
    window.$toast.success(t('verify.codeResent'))
  } else {
    window.$toast.error(t('verify.sendFailed'))
  }
}

function startCooldown(seconds) {
  cooldown.value = seconds
  if (cooldownTimer) clearInterval(cooldownTimer)
  cooldownTimer = setInterval(() => {
    cooldown.value = Math.max(0, cooldown.value - 1)
    if (cooldown.value <= 0 && cooldownTimer) {
      clearInterval(cooldownTimer)
      cooldownTimer = null
    }
  }, 1000)
}
</script>

<style scoped>
.verify-wrap{display:flex;align-items:center;justify-content:center;height:100%}
.panel{width:420px;background:#fff;padding:22px;border-radius:12px;box-shadow:0 8px 24px rgba(0,0,0,0.06)}
.panel h3{margin:0 0 12px}
.panel input{width:100%;padding:12px 14px;border-radius:10px;border:1px solid #eee;margin-top:10px;box-sizing:border-box}
.actions{margin-top:18px;display:flex;flex-direction:column;gap:8px}
.primary{background:linear-gradient(180deg,var(--accent-1),var(--accent-2));color:#fff;border:0;padding:12px;border-radius:10px;font-weight:700}
.toggle-mode{background:transparent;border:0;color:var(--accent-2);text-align:center;padding:6px 0;font-weight:600;cursor:pointer}
</style>
