<template>
  <div class="verify-wrap">
    <div class="panel">
      <router-link to="/" class="verify-brand"><BrandMark /></router-link>
      <h3>{{ $t('verify.title') }}</h3>
      <i18n-t v-if="email" keypath="verify.sentTo" tag="p">
        <template #email>
          <strong>{{ email }}</strong>
        </template>
      </i18n-t>
      <p v-else>{{ $t('verify.noPending') }}</p>
      <input v-model="code" autocomplete="one-time-code" inputmode="numeric" :aria-label="$t('verify.placeholder')" :placeholder="$t('verify.placeholder')" />

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

<script setup lang="ts">
import { toast } from '../utils/toast'
import BrandMark from '../components/BrandMark.vue'
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
let cooldownTimer: ReturnType<typeof setTimeout> | undefined

onMounted(() => {
  const pending = store.state.pendingRegister
  if (!pending || !pending.email || !pending.password) {
    toast.warning(t('verify.noPendingInfo'))
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
  if (!code.value) { toast.warning(t('verify.inputCode')); return }
  if (submitting.value) return
  submitting.value = true
  try {
    const result = await api.verifyRegister(email.value, code.value, password)
    if (result.ok) {
      toast.success(t('verify.success'))
      store.clearPendingRegister()
      router.push({ name: 'WebApp' })
    } else {
      toast.error(result.error || t('verify.failed'))
    }
  } finally {
    submitting.value = false
  }
}

async function resend() {
  if (!email.value) return
  if (cooldown.value > 0) return
  const result = await api.sendVerifyCode(email.value)
  if (result.ok) {
    // 本地记录冷却到期时间（120s）并启动倒计时
    try { localStorage.setItem('verify:email:cooldown:' + email.value, String(Date.now() + 120000)); } catch (e) {}
    startCooldown(120)
    toast.success(t('verify.codeResent'))
  } else {
    toast.error(result.error || t('verify.sendFailed'))
  }
}

function startCooldown(seconds: number) {
  cooldown.value = seconds
  if (cooldownTimer) clearInterval(cooldownTimer)
  cooldownTimer = setInterval(() => {
    cooldown.value = Math.max(0, cooldown.value - 1)
    if (cooldown.value <= 0 && cooldownTimer) {
      clearInterval(cooldownTimer)
      cooldownTimer = undefined
    }
  }, 1000)
}
</script>

<style scoped>
.verify-wrap{display:flex;align-items:center;justify-content:center;min-height:100%;box-sizing:border-box;padding:24px;background:var(--bg)}.verify-brand{display:block;text-decoration:none;margin-bottom:28px}
.panel{width:min(420px,100%);box-sizing:border-box;background:var(--panel);padding:28px;border-radius:24px;border:1px solid var(--surface-border);color:var(--text-primary)}.panel p{color:var(--muted);font-size:14px;line-height:1.8;overflow-wrap:anywhere}
.panel h3{margin:0 0 12px}
.panel input{width:100%;padding:12px 14px;border-radius:10px;border:1px solid var(--input-border);margin-top:10px;box-sizing:border-box;background:var(--input-bg);color:var(--text-primary);font-size:16px}
.actions{margin-top:18px;display:flex;flex-direction:column;gap:8px}
.primary{background:var(--action);color:var(--on-action);border:0;padding:12px;border-radius:10px;font-weight:700}
.toggle-mode{background:transparent;border:0;color:var(--accent-2);text-align:center;padding:6px 0;font-weight:600;cursor:pointer}
</style>
