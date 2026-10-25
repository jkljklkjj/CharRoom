<template>
  <section class="social-room">
    <h2>{{ t('social.encounterTitle') }}</h2>
    <p class="social-muted">{{ t('social.encounterHint') }}</p>
    <div class="social-room-status"><span role="status" aria-live="polite">{{ t(`social.room.${room.state}`) }}</span><time v-if="room.state === 'ACTIVE'" :aria-label="t('polish.timeRemaining')">{{ remaining }}</time></div>
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
      <div ref="messagesElement" class="social-room-messages" aria-live="polite" role="log" :aria-label="t('social.roomMessages')" tabindex="0" @scroll="onScroll">
        <p v-if="!room.messages.length" class="social-muted">{{ t(room.state === 'PREPARING' ? 'polish.readyHint' : 'polish.firstMessage') }}</p>
        <div v-for="message in room.messages" :key="message.seq" class="social-room-message" :class="{ mine: message.author === room.mySlot }">
          <small>{{ t(message.author === room.mySlot ? 'social.you' : 'social.other') }}</small><p>{{ message.content }}</p>
        </div>
      </div>
      <button v-if="unseen" class="cancel-btn" @click="jumpToLatest">{{ t('polish.newMessages', { count: unseen }) }} ↓</button>
      <form v-if="room.state === 'ACTIVE'" class="social-form" @submit.prevent="send">
        <label>{{ t('social.message') }}<textarea v-model="draft" required maxlength="1000" class="profile-input" rows="2" @keydown="composerEnter($event, send)"></textarea></label>
        <div class="social-compose-footer"><small class="social-muted">{{ draft.length }}/1000 · {{ t('polish.sendHint') }}</small><button class="save-btn" :disabled="busy || !draft.trim()">{{ t(busy ? 'polish.sending' : 'social.send') }}</button></div>
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
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { useEncounter } from '../composables/useEncounter'
import { reportSocialUser } from '../api/social'
import { composerEnter } from '../utils/composer'
const emit = defineEmits<{ profile: [id: string]; daily: [] }>()
const { t } = useI18n(), { room, error, busy, action } = useEncounter()
const draft = ref(''), reporting = ref(false), reportReason = ref(''), now = ref(Date.now()), queuedAt = ref(Date.now())
const messagesElement = ref<HTMLElement | null>(null), unseen = ref(0)
let following = true
function onScroll() {
  const element = messagesElement.value
  following = !element || element.scrollHeight - element.scrollTop - element.clientHeight < 64
  if (following) unseen.value = 0
}
async function jumpToLatest() {
  following = true; unseen.value = 0
  await nextTick()
  const element = messagesElement.value
  if (element) element.scrollTop = element.scrollHeight
}
watch(() => room.value.roomId, () => { pending = null; draft.value = ''; reporting.value = false; following = true; unseen.value = 0 })
watch(() => room.value.messages.at(-1)?.seq, async (last, previous) => {
  if (following) await jumpToLatest()
  else if (last && last !== previous) unseen.value += room.value.messages.filter(message => message.seq > (previous || 0)).length
})
let timer: ReturnType<typeof setInterval> | undefined
let pending: { messageId: string; content: string } | null = null
const remaining = computed(() => {
  const seconds = Math.max(0, Math.ceil(((room.value.endsAt || 0) - (room.value.serverTime || now.value) - (now.value - receivedAt.value)) / 1000))
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
})
const receivedAt = ref(Date.now())
watch(() => room.value.serverTime, () => { receivedAt.value = Date.now() })
const waited = computed(() => Math.floor((now.value - queuedAt.value) / 1000))
onMounted(() => { timer = setInterval(() => { now.value = Date.now() }, 1000) })
onBeforeUnmount(() => clearInterval(timer))
async function start() { queuedAt.value = Date.now(); pending = null; draft.value = ''; reporting.value = false; await action({ action: 'join' }) }
async function send() {
  const content = draft.value.trim()
  if (!content || busy.value) return
  if (!pending || pending.content !== content) pending = { messageId: crypto.randomUUID(), content }
  const submitted = pending
  const result = await action({ action: 'send', ...submitted })
  if (result) {
    if (draft.value.trim() === submitted.content) draft.value = ''
    if (pending === submitted) pending = null
    await jumpToLatest()
  }
}
async function switchToDaily() { const result = await action({ action: 'cancel' }); if (result) emit('daily') }
async function report() {
  try { await reportSocialUser({ roomId: room.value.roomId, reason: reportReason.value }); reporting.value = false; await action({ action: 'leave' }) }
  catch (failure) { error.value = (failure as Error).message }
}
</script>
