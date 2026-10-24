<template>
  <section class="social-room">
    <h2>{{ t('social.encounterTitle') }}</h2>
    <p class="social-muted">{{ t('social.encounterHint') }}</p>
    <p role="status" aria-live="polite">{{ t(`social.room.${room.state}`) }}<span v-if="room.state === 'ACTIVE'"> · {{ remaining }}</span></p>
    <p v-if="error" class="social-error" role="alert">{{ t('social.failed') }} {{ error }}</p>
    <div class="social-actions">
      <button v-if="room.state === 'IDLE' || room.state === 'ENDED'" class="save-btn" :disabled="busy" @click="start">{{ t('social.startMatch') }}</button>
      <button v-if="room.state === 'QUEUED'" class="cancel-btn" :disabled="busy" @click="action({ action: 'cancel' })">{{ t('social.cancelMatch') }}</button>
      <button v-if="room.state === 'PREPARING'" class="save-btn" :disabled="busy || room.myReady" @click="action({ action: 'ready' })">{{ t(room.myReady ? 'social.waitOther' : 'social.ready') }}</button>
      <button v-if="['PREPARING', 'ACTIVE'].includes(room.state)" class="cancel-btn" :disabled="busy" @click="action({ action: 'leave' })">{{ t('social.leave') }}</button>
    </div>
    <p v-if="room.state === 'QUEUED' && waited >= 30" class="social-muted">{{ t('social.matchWait') }} <button class="cancel-btn" @click="switchToDaily">{{ t('social.daily') }}</button></p>
    <p v-if="room.reason" class="social-muted">{{ t(`social.end.${room.reason}`) }}</p>
    <template v-if="room.roomId">
      <blockquote class="social-intent">{{ room.question }}</blockquote>
      <div class="social-room-messages" aria-live="polite" role="log" :aria-label="t('social.roomMessages')">
        <div v-for="message in room.messages" :key="message.seq" class="social-room-message" :class="{ mine: message.author === room.mySlot }">
          <small>{{ t(message.author === room.mySlot ? 'social.you' : 'social.other') }}</small><p>{{ message.content }}</p>
        </div>
      </div>
      <form v-if="room.state === 'ACTIVE'" class="social-form" @submit.prevent="send">
        <label>{{ t('social.message') }}<textarea v-model="draft" required maxlength="1000" class="profile-input" rows="2" @input="pending = null"></textarea></label>
        <button class="save-btn" :disabled="busy || !draft.trim()">{{ t('social.send') }}</button>
      </form>
      <div v-if="room.state === 'ACTIVE' || (room.state === 'ENDED' && room.reason === 'EXPIRED')" class="social-actions">
        <button v-if="room.state === 'ACTIVE' && !room.extensions" class="cancel-btn" :disabled="busy || room.extendRequested" @click="action({ action: 'extend' })">{{ t(room.extendRequested ? 'social.waitOther' : 'social.extend') }}</button>
        <button class="cancel-btn" :disabled="busy || room.exchangeRequested" @click="action({ action: 'exchange' })">{{ t(room.exchanged ? 'social.exchanged' : room.exchangeRequested ? 'social.waitOther' : 'social.exchange') }}</button>
      </div>
      <button v-if="room.peer" class="save-btn" @click="emit('profile', room.peer.id)">{{ t('social.view') }} · {{ room.peer.username }}</button>
      <button v-if="!reporting && room.state !== 'IDLE'" class="cancel-btn" @click="reporting = true">{{ t('social.reportAndLeave') }}</button>
      <form v-if="reporting" class="social-form" @submit.prevent="report"><label>{{ t('social.reportReason') }}<textarea v-model="reportReason" required maxlength="500" class="profile-input"></textarea></label><button class="save-btn" :disabled="busy">{{ t('social.submit') }}</button></form>
    </template>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { useEncounter } from '../composables/useEncounter'
import { reportSocialUser } from '../api/social'
const emit = defineEmits<{ profile: [id: string]; daily: [] }>()
const { t } = useI18n(), { room, error, busy, action } = useEncounter()
const draft = ref(''), reporting = ref(false), reportReason = ref(''), now = ref(Date.now()), queuedAt = ref(Date.now())
let timer: ReturnType<typeof setInterval> | undefined
let pending: { messageId: string; content: string } | null = null
const remaining = computed(() => {
  const seconds = Math.max(0, Math.ceil(((room.value.endsAt || 0) - (room.value.serverTime || now.value) - (now.value - receivedAt.value)) / 1000))
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
})
const receivedAt = ref(Date.now())
import { watch } from 'vue'
watch(() => room.value.serverTime, () => { receivedAt.value = Date.now() })
const waited = computed(() => Math.floor((now.value - queuedAt.value) / 1000))
onMounted(() => { timer = setInterval(() => { now.value = Date.now() }, 1000) })
onBeforeUnmount(() => clearInterval(timer))
async function start() { queuedAt.value = Date.now(); pending = null; draft.value = ''; reporting.value = false; await action({ action: 'join' }) }
async function send() {
  const content = draft.value.trim()
  if (!content) return
  pending ??= { messageId: crypto.randomUUID(), content }
  const result = await action({ action: 'send', ...pending })
  if (result) { draft.value = ''; pending = null }
}
async function switchToDaily() { const result = await action({ action: 'cancel' }); if (result) emit('daily') }
async function report() {
  try { await reportSocialUser({ roomId: room.value.roomId, reason: reportReason.value }); reporting.value = false; await action({ action: 'leave' }) }
  catch (failure) { error.value = (failure as Error).message }
}
</script>
