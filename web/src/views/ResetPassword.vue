<template>
  <div class="reset-wrap">
    <div class="panel">
      <router-link to="/" class="reset-brand"><BrandMark /></router-link>
      <h3>{{ $t('reset.title') }}</h3>
      <p class="reset-intro">{{ $t('reset.intro') }}</p>
      <form @submit.prevent="submit">
        <label for="reset-email">{{ $t('reset.emailLabel') }}</label>
        <input id="reset-email" v-model="email" type="email" autocomplete="username" autocapitalize="none" spellcheck="false" required :placeholder="$t('reset.emailPlaceholder')" :aria-invalid="Boolean(emailError)" aria-describedby="reset-email-error" @input="emailError = ''" />
        <div v-if="emailError" id="reset-email-error" class="error-hint" role="status">{{ $t(emailError) }}</div>

        <label for="reset-code">{{ $t('reset.codeLabel') }}</label>
        <div class="code-row">
          <input id="reset-code" v-model="code" inputmode="numeric" autocomplete="one-time-code" required maxlength="6" :placeholder="$t('reset.codePlaceholder')" />
          <!-- 冷却期只显示剩余秒数，按钮宽度固定，避免文案切换时整行抖动 -->
          <button type="button" class="send" :disabled="sending || cooldown > 0 || submitting" @click="sendCode">
            <span v-if="cooldown > 0" class="countdown">{{ $t('reset.cooldownShort', { seconds: cooldown }) }}</span>
            <span v-else-if="sending">{{ $t('reset.sending') }}</span>
            <span v-else>{{ $t('reset.sendCode') }}</span>
          </button>
        </div>

        <label for="reset-password">{{ $t('reset.passwordLabel') }}</label>
        <input id="reset-password" v-model="password" type="password" autocomplete="new-password" required :placeholder="$t('reset.passwordPlaceholder')" :aria-invalid="Boolean(passwordError)" @input="onPasswordInput" />
        <div v-if="passwordError" class="error-hint" role="status">{{ $t(passwordError) }}</div>

        <button class="primary" type="submit" :disabled="submitting">
          <span v-if="submitting" class="spinner" aria-hidden="true"></span>
          {{ submitting ? $t('reset.submitting') : $t('reset.submit') }}
        </button>
      </form>
      <button type="button" class="back" @click="router.push('/app')">{{ $t('reset.back') }}</button>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import BrandMark from '../components/BrandMark.vue'
import api from '../api'
import { toast } from '../utils/toast'
import { validateEmail, validatePassword } from '../utils/validation'

const { t } = useI18n()
const router = useRouter()
const email = ref('')
const code = ref('')
const password = ref('')
const passwordError = ref('')
const emailError = ref('')
const submitting = ref(false)
const sending = ref(false)
const cooldown = ref(0)
let timer: ReturnType<typeof setInterval> | undefined

function startCooldown(seconds: number) {
  cooldown.value = seconds
  clearInterval(timer)
  timer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0) clearInterval(timer)
  }, 1000)
}

onUnmounted(() => clearInterval(timer))

function onPasswordInput() {
  if (passwordError.value) passwordError.value = ''
}

async function sendCode() {
  if (sending.value || cooldown.value > 0) return
  const target = email.value.trim()
  // 非法/空邮箱本地拦下，不再把「发送成功」的假象抛给用户
  const emailCheck = validateEmail(target)
  if (!emailCheck.valid) {
    emailError.value = emailCheck.message
    toast.warning(t(emailCheck.message))
    return
  }
  emailError.value = ''
  sending.value = true
  try {
    const result = await api.sendResetCode(target)
    if (result.ok) {
      toast.success(t('reset.codeSent'))
      startCooldown(120)
    } else {
      toast.error(result.error || t('reset.codeFailed'))
    }
  } finally {
    sending.value = false
  }
}

async function submit() {
  if (submitting.value) return
  const target = email.value.trim()
  const emailCheck = validateEmail(target)
  if (!emailCheck.valid) {
    emailError.value = emailCheck.message
    toast.warning(t(emailCheck.message))
    return
  }
  if (!code.value.trim()) { toast.warning(t('reset.codeRequired')); return }
  // 与注册使用同一套规则：仅长度 8–64 位
  const check = validatePassword(password.value)
  if (!check.valid) { passwordError.value = check.message; return }
  submitting.value = true
  try {
    const result = await api.resetPassword(target, code.value.trim(), password.value)
    if (result.ok) {
      toast.success(t('reset.success'))
      router.push('/app')
    } else {
      toast.error(result.error || t('reset.failed'))
    }
  } finally {
    submitting.value = false
  }
}
</script>
<style scoped>
.reset-wrap{min-height:100dvh;display:grid;place-items:center;background:var(--bg);padding:32px 20px;box-sizing:border-box}
.panel{background:var(--panel);border:1px solid var(--surface-border);border-radius:18px;padding:34px;box-sizing:border-box;width:min(420px,100%)}
.reset-brand{display:inline-block;margin-bottom:18px}
.panel h3{margin:0 0 8px;font-size:22px;font-weight:600}
.reset-intro{font-size:13px;line-height:1.7;color:var(--muted);margin:0 0 22px}
.panel form{display:grid;gap:9px}
.panel label{font-size:13px;color:var(--text-primary);margin-top:8px}
.panel input{box-sizing:border-box;width:100%;border:1px solid var(--input-border);border-radius:11px;background:var(--input-bg);color:var(--text-primary);padding:13px 12px;font-size:16px;min-height:46px}
.panel input::placeholder{color:var(--input-placeholder);font-size:13px}
.code-row{display:flex;gap:8px}
.code-row input{flex:1;min-width:0}
.code-row .send{flex:none;width:104px;min-width:104px;border:1px solid var(--input-border);background:var(--panel);color:var(--text-primary);border-radius:11px;padding:0 8px;min-height:46px;font-size:13px;cursor:pointer;white-space:nowrap;font-variant-numeric:tabular-nums}
.code-row .send .countdown{display:inline-block;min-width:3ch;text-align:center}
.code-row .send:disabled{opacity:.6;cursor:not-allowed}
.primary{margin-top:16px;background:var(--action);color:var(--on-action);border:0;padding:14px;border-radius:12px;font-weight:600;display:flex;align-items:center;justify-content:center;gap:10px;cursor:pointer;min-height:46px}
.primary:disabled{opacity:.6;cursor:wait}
.spinner{width:16px;height:16px;border:2px solid currentColor;border-right-color:transparent;border-radius:50%;animation:spin .6s linear infinite}
.error-hint{color:#b53b27;font-size:12px;line-height:1.6}
.back{margin-top:12px;width:100%;background:none;border:0;color:var(--muted);font-size:13px;padding:10px 0;cursor:pointer}
@keyframes spin{to{transform:rotate(360deg)}}
</style>
