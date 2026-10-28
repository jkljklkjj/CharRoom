<template>
  <div class="login-wrap">
    <div class="login-shell">
      <header class="login-header"><router-link to="/" class="login-brand"><BrandMark /></router-link><router-link to="/" class="login-back">← {{ $t('product.backHome') }}</router-link></header>
      <div class="login-layout">
        <section class="login-welcome"><span class="login-eyebrow">{{ $t('product.loginEyebrow') }}</span><h1>{{ $t('product.loginTitle') }}</h1><p>{{ $t('product.loginLead') }}</p><ConversationPreview compact /></section>
        <section class="panel" :aria-labelledby="'login-title'">
          <h2 id="login-title">{{ isRegister ? $t('login.register') : $t('login.login') }}</h2><p class="login-intro">{{ $t('product.loginNote') }}</p>
          <form @submit.prevent="doAction">
            <label for="login-account">{{ $t(isRegister ? 'login.emailLabel' : 'login.emailOrIdLabel') }}</label>
            <input id="login-account" v-model="account" :type="isRegister?'email':'text'" autocomplete="username" autocapitalize="none" spellcheck="false" required :placeholder="isRegister ? $t('login.emailPlaceholder') : $t('login.emailOrIdPlaceholder')" />
            <label for="login-password">{{ $t('login.passwordLabel') }}</label>
            <input id="login-password" type="password" v-model="password" :autocomplete="isRegister?'new-password':'current-password'" required :placeholder="$t('login.passwordPlaceholder')" :aria-invalid="Boolean(isRegister && passwordError)" :aria-describedby="isRegister && passwordError ? 'password-error' : undefined" @input="onPasswordInput" />
            <div v-if="isRegister && passwordError" id="password-error" class="error-hint" role="status">{{ passwordError }}</div>
            <div class="actions"><button class="primary" type="submit" :disabled="submitting"><span v-if="submitting" class="spinner" aria-hidden="true"></span>{{ submitting ? $t('login.loading') : (isRegister ? $t('login.createAccount') : $t('login.login')) }} <span v-if="!submitting" aria-hidden="true">↗</span></button><button type="button" class="toggle-mode" @click="toggleMode">{{ isRegister ? $t('login.hasAccount') : $t('login.noAccount') }}</button><button v-if="!isRegister" type="button" class="toggle-mode forgot" @click="$router.push('/reset')">{{ $t('login.forgotPassword') }}</button></div>
          </form>
          <p class="login-footer">{{ $t('product.loginFooter') }}</p>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import BrandMark from './BrandMark.vue'
import ConversationPreview from './ConversationPreview.vue'
import type { Tokens } from '../types/api'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import api from '../api'
import { useStore } from '../store'
import { toast } from '../utils/toast'
import { validatePassword } from '../utils/validation'

const { t } = useI18n()
const account = ref('')
const password = ref('')
const passwordError = ref('')
const isRegister = ref(false)
const store = useStore()
const router = useRouter()

const emit = defineEmits<{ logged: [tokens: Tokens] }>()

function toggleMode() {
  isRegister.value = !isRegister.value
  passwordError.value = ''
}

function onPasswordInput() {
  if (isRegister.value && password.value) {
    const result = validatePassword(password.value)
    passwordError.value = result.valid ? '' : result.message
  } else {
    passwordError.value = ''
  }
}

const submitting = ref(false)
async function doAction() {
  if (submitting.value) return
  submitting.value = true
  try {
    if (isRegister.value) return await doRegister()
    return await doLogin()
  } finally {
    submitting.value = false
  }
}

async function doLogin() {
  if (!account.value || !password.value) { toast.warning(t('login.pleaseFillAccountPassword')); return }
  const result = await api.login(account.value, password.value)
  if (result.ok) {
    store.setToken(result.accessToken)
    store.setRefreshToken(result.refreshToken || '')
    emit('logged', { accessToken: result.accessToken, refreshToken: result.refreshToken })
  } else {
    toast.error(result.error || t('login.checkAccountPassword'))
  }
}

async function doRegister() {
  if (!account.value || !password.value) { toast.warning(t('login.pleaseFillEmailPassword')); return }

  // 密码强度校验
  const passwordResult = validatePassword(password.value)
  if (!passwordResult.valid) {
    toast.error(passwordResult.message)
    return
  }

  // 发送验证码到邮箱，跳转到验证码输入页
  const result = await api.sendVerifyCode(account.value)
  if (result.ok) {
    // 本地记录冷却到期时间（120s），用于前端倒计时显示
    try { localStorage.setItem('verify:email:cooldown:' + account.value, String(Date.now() + 120000)); } catch (e) {}
    store.setPendingRegister({ email: account.value, password: password.value })
    router.push({ name: 'Verify' })
  } else {
    toast.error(result.error || t('login.verificationSentFailed'))
  }
}
</script>

<style scoped>
.login-wrap{height:100%;overflow:auto;background:var(--bg);box-sizing:border-box;padding:0 32px 40px}.login-shell{max-width:1040px;margin:0 auto}.login-header{display:flex;align-items:center;justify-content:space-between;padding:28px 0;gap:12px}.login-brand{text-decoration:none}.login-back{font-size:12px;color:var(--muted);text-decoration:none}.login-layout{display:grid;grid-template-columns:1fr 400px;align-items:center;gap:100px;min-height:calc(100dvh - 150px)}.login-eyebrow{color:var(--on-apricot);font-size:12px;letter-spacing:.08em}.login-welcome h1{font-size:34px;letter-spacing:-.045em;font-weight:600;line-height:1.4;margin:17px 0}.login-welcome>p{color:var(--muted);font-size:14px;line-height:1.8;max-width:350px;margin-bottom:26px}.login-welcome :deep(.conversation-preview){max-width:360px;box-shadow:none}.panel{background:var(--panel);border:1px solid var(--surface-border);border-radius:18px;padding:34px;box-sizing:border-box;width:100%}.panel h2{margin:0 0 8px;font-size:24px;font-weight:600}.login-intro{font-size:13px;line-height:1.7;color:var(--muted);margin:0 0 26px}.panel form{display:grid;gap:9px}.panel label{font-size:13px;color:var(--text-primary);margin-top:8px}.panel input{box-sizing:border-box;width:100%;border:1px solid var(--input-border);border-radius:11px;background:var(--input-bg);color:var(--text-primary);padding:13px 12px;font-size:16px;min-height:46px}.panel input::placeholder{color:var(--input-placeholder);font-size:13px}.actions{margin-top:16px;display:flex;flex-direction:column;gap:12px}.primary{background:var(--action);color:var(--on-action);border:0;padding:14px;border-radius:12px;font-weight:600;display:flex;align-items:center;justify-content:center;gap:12px;cursor:pointer;min-height:46px}.primary:disabled{opacity:.6;cursor:wait}.spinner{width:16px;height:16px;border:2px solid currentColor;border-right-color:transparent;border-radius:50%;animation:spin .6s linear infinite}.toggle-mode{background:none;border:0;color:var(--action);text-align:center;padding:8px 0;min-height:40px;font-size:13px;cursor:pointer}.error-hint{color:#b53b27;font-size:12px;line-height:1.6}.login-footer{font-size:11px;color:var(--muted);line-height:1.7;border-top:1px solid var(--surface-border);padding-top:20px;margin:20px 0 0}
@keyframes spin{to{transform:rotate(360deg)}}
@media(max-width:900px){.login-layout{gap:35px;grid-template-columns:1fr 360px}.login-welcome h1{font-size:28px}}
@media(max-width:700px){.login-wrap{padding:0 20px 30px}.login-layout{display:block;min-height:0;max-width:420px;margin:24px auto}.login-welcome{margin-bottom:26px}.login-welcome h1{font-size:28px}.login-welcome>p{margin-bottom:0}.login-welcome :deep(.conversation-preview){display:none}.panel{padding:26px}.login-header{padding-block:20px}.login-back{font-size:11px}}
</style>
