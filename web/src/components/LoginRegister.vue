<template>
  <div class="login-wrap">
    <div class="panel">
        <h3>{{ isRegister ? $t('login.register') : $t('login.login') }}</h3>
        <form @submit.prevent="doAction">
          <label v-if="isRegister">{{ $t('login.emailLabel') }}</label>
          <label v-else>{{ $t('login.emailOrIdLabel') }}</label>
          <input v-model="account" :placeholder="isRegister ? $t('login.emailPlaceholder') : $t('login.emailOrIdPlaceholder')" />
          <label>{{ $t('login.passwordLabel') }}</label>
          <input type="password" v-model="password" :placeholder="$t('login.passwordPlaceholder')" />

          <div class="actions">
            <button class="primary" type="submit">{{ isRegister ? $t('login.createAccount') : $t('login.login') }}</button>
            <button type="button" class="toggle-mode" @click="toggleMode">{{ isRegister ? $t('login.hasAccount') : $t('login.noAccount') }}</button>
          </div>
        </form>
      </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import api from '../api'
import { useStore } from '../store'

const { t } = useI18n()
const account = ref('')
const password = ref('')
const isRegister = ref(false)
const store = useStore()
const router = useRouter()

const emit = defineEmits(['logged'])

function toggleMode() { isRegister.value = !isRegister.value }

async function doAction() {
  if (isRegister.value) return await doRegister()
  return await doLogin()
}

async function doLogin() {
  if (!account.value || !password.value) { window.$toast.warning(t('login.pleaseFillAccountPassword')); return }
  const tokens = await api.login(account.value, password.value)
  if (tokens && tokens.accessToken) {
    store.setToken(tokens.accessToken)
    store.setRefreshToken(tokens.refreshToken || '')
    emit('logged', tokens)
  } else {
    window.$toast.error(t('login.checkAccountPassword'))
  }
}

async function doRegister() {
  if (!account.value || !password.value) { window.$toast.warning(t('login.pleaseFillEmailPassword')); return }
  // 发送验证码到邮箱，跳转到验证码输入页
  const sent = await api.sendVerifyCode(account.value)
  if (sent) {
    // 本地记录冷却到期时间（120s），用于前端倒计时显示
    try { localStorage.setItem('verify:email:cooldown:' + account.value, String(Date.now() + 120000)); } catch (e) {}
    store.setPendingRegister({ email: account.value, password: password.value })
    router.push({ name: 'Verify' })
  } else {
    window.$toast.error(t('login.verificationSentFailed'))
  }
}
</script>

<style scoped>
.login-wrap{display:flex;align-items:center;justify-content:center;height:100%}
.panel{width:420px;background:#fff;padding:22px;border-radius:12px;box-shadow:0 8px 24px rgba(0,0,0,0.06)}
.panel h3{margin:0 0 12px}
.panel label{display:block;font-size:12px;color:var(--muted);margin-top:8px}
.panel input{width:100%;padding:12px 14px;border-radius:10px;border:1px solid #eee;margin-top:10px;box-sizing:border-box}
.actions{margin-top:18px;display:flex;flex-direction:column;gap:8px}
.primary{background:linear-gradient(180deg,var(--accent-1),var(--accent-2));color:#fff;border:0;padding:12px;border-radius:10px;font-weight:700}
.toggle-mode{background:transparent;border:0;color:var(--accent-2);text-align:center;padding:6px 0;font-weight:600;cursor:pointer}
</style>
