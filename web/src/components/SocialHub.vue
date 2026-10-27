<template>
  <div class="settings-overlay social-overlay" :style="viewportStyle" @click.self="emit('close')">
    <div ref="dialog" class="settings-dialog social-hub" :class="{ 'room-mode': tab === 'encounter' && !selected }" role="dialog" aria-modal="true" :aria-label="t('social.discover')" tabindex="-1">
      <header class="settings-header"><h2>{{ t('social.discover') }}</h2><button class="close-settings" :aria-label="t('social.close')" @click="emit('close')">×</button></header>
      <nav class="social-nav" :aria-label="t('social.discover')">
        <button v-for="item in ['people', 'encounter', 'daily', 'me'] as const" :key="item" :aria-pressed="ready && tab === item" :aria-busy="pending === item" :class="{ active: tab === item }" @click="changeTab(item)">{{ t(`social.${item}`) }}</button>
      </nav>
      <div class="social-loading-track"><div v-if="busy || pending" class="social-loading-line" role="progressbar" :aria-label="t('social.loading')"></div></div>
      <div class="social-entry-status" role="status" aria-live="polite"><span v-if="pending">{{ t('social.preparing', { page: t(`social.${pending}`) }) }}</span></div>
      <div v-if="entryError" class="social-entry-error social-error" role="alert">{{ entryError }} <button class="cancel-btn" @click="retryEntry">{{ t('polish.retry') }}</button></div>
      <div v-if="!ready" class="social-entry-placeholder" :aria-busy="Boolean(pending)"><span v-if="pending" class="social-spinner" aria-hidden="true"></span><p>{{ t('social.preparingEntry') }}</p></div>
      <div v-else class="social-layout" :class="{ 'has-profile': selected, 'profile-only': tab === 'me' }">
        <main ref="mainPanel" class="social-main" v-show="!selected || !mobile">
          <p v-if="error" class="social-error" role="alert">{{ t('social.failed') }} {{ error }}</p>
          <template v-if="tab === 'people'">
            <form class="social-form social-discovery-form" @submit.prevent="loadPeople(false)">
              <div class="social-search-row"><label>{{ t('social.search') }}<input v-model="query" maxlength="60" class="profile-input" /></label><button class="save-btn" :disabled="busy">{{ t('polish.searchAction') }}</button></div>
              <details class="social-discovery-filters"><summary>{{ t('social.filters') }}<span v-if="channel !== 'all' || tag.trim()"> · {{ Number(channel !== 'all') + Number(Boolean(tag.trim())) }}</span></summary><div class="social-actions">
                <label>{{ t('social.channel') }}<select v-model="channel" class="profile-input"><option value="all">{{ t('social.all') }}</option><option value="mutual">{{ t('social.mutual') }}</option><option value="city">{{ t('social.city') }}</option></select></label>
                <label>{{ t('social.tags') }}<input v-model="tag" maxlength="24" class="profile-input" /></label>
              </div></details>
            </form>
            <p class="social-muted">{{ t('social.discoveryHint') }}</p>
            <div v-if="!people.length && !error" class="social-empty" :aria-busy="busy">
              <div v-if="busy" class="social-empty-loading" role="status"><span class="social-spinner" aria-hidden="true"></span>{{ t('social.loading') }}</div>
              <template v-else><h3>{{ t('polish.noPeople') }}</h3><p class="social-muted">{{ t('social.empty') }}</p><button class="cancel-btn" @click="changeTab('me')">{{ t('social.me') }}</button></template>
            </div>
            <article v-for="person in people" :key="person.id" class="social-person">
              <div class="social-person-head"><img v-if="person.avatarUrl" :src="person.avatarUrl" class="social-avatar" alt="" /><div v-else class="social-avatar social-initial">{{ person.username.slice(0, 1) }}</div><div><h3>{{ person.username }}</h3><p class="social-muted">{{ person.signature || person.city }}</p></div></div>
              <div class="social-tags"><span v-for="interest in person.interests" :key="interest">{{ interest }}</span></div>
              <div class="social-actions"><small class="social-muted">{{ t(`social.reason.${person.reason || 'EXPLORE'}`) }}</small><button class="cancel-btn" @click="openProfile(person.id)">{{ t('social.view') }}</button></div>
            </article>
            <button v-if="nextCursor !== null" class="cancel-btn" :disabled="busy" @click="loadPeople(true)">{{ t('social.more') }}</button>
          </template>
          <EncounterRoom v-if="tab === 'encounter'" :initial-room="initialRoom" @profile="openProfile" @daily="changeTab('daily')" />
          <template v-if="tab === 'daily'">
            <div v-if="!daily && busy" class="social-empty social-empty-loading-state" role="status"><span class="social-spinner" aria-hidden="true"></span>{{ t('social.loading') }}</div>
            <template v-if="daily">
              <h2>{{ daily.question }}</h2><p class="social-muted">{{ daily.day }} · {{ t('social.dailyHint') }}</p>
              <form class="social-form" @submit.prevent="saveAnswer"><label>{{ t('social.yourAnswer') }}<textarea v-model="answer" maxlength="500" class="profile-input" rows="3"></textarea></label><button class="save-btn" :disabled="busy">{{ t('social.publish') }}</button><p class="social-muted">{{ t('social.answerPrivacy') }}</p></form>
              <article v-for="item in daily.answers" :key="item.id" class="social-person social-answer-card"><p class="social-answer">{{ item.content }}</p><div class="social-actions"><div class="social-author"><img v-if="item.author.avatarUrl" :src="item.author.avatarUrl" class="social-avatar social-avatar-sm" alt="" /><div v-else class="social-avatar social-avatar-sm social-initial">{{ item.author.username.slice(0, 1) }}</div><span class="social-author-name">{{ item.author.username }}</span></div><button class="cancel-btn" @click="openProfile(item.author.id)">{{ t('social.view') }}</button></div></article>
              <p v-if="!daily.answers.length">{{ t('social.noAnswers') }}</p>
              <button v-if="daily.nextCursor" class="cancel-btn" :disabled="busy" @click="loadDaily(true)">{{ t('social.more') }}</button>
            </template>
          </template>
        </main>
        <aside v-if="selected" ref="detailPanel" class="social-detail" tabindex="-1" :aria-label="t('social.view')"><div v-if="tab !== 'me'" class="social-detail-header"><button class="cancel-btn" @click="closeProfile">‹ {{ t('social.back') }}</button></div><SocialProfileCard :key="profileVersion" :user-id="selected" :initial-profile="preparedProfile" :initial-blocked="preparedBlocked" @changed="invalidatePages" @blocked="onBlocked" @chat="startChat" @saved="onSaved" /></aside>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { useStore } from '../store'
import { discoverPeople, getDailyQuestion, answerDailyQuestion, getSocialProfile, getBlockedUsers, getEncounter } from '../api/social'
import type { SocialProfile, DailyQuestion, SocialDirectory, EncounterState } from '../types/social'
import SocialProfileCard from './SocialProfileCard.vue'
import EncounterRoom from './EncounterRoom.vue'
import { useDialog } from '../composables/useDialog'
import { useVisualViewport } from '../composables/useVisualViewport'
const emit = defineEmits<{ close: []; chat: [profile: SocialProfile] }>()
const props = withDefaults(defineProps<{ initialTab?: 'people' | 'me' }>(), { initialTab: 'people' })
const dialog = ref<HTMLElement | null>(null)
const mainPanel = ref<HTMLElement | null>(null), detailPanel = ref<HTMLElement | null>(null)
const viewportStyle = useVisualViewport(async () => {
  await nextTick()
  const input = document.activeElement
  if (!(input instanceof HTMLElement) || !input.matches('input,textarea,select') || !dialog.value?.contains(input)) return
  const panel = input.closest<HTMLElement>('.social-main, .social-detail')
  if (!panel) return
  const field = input.getBoundingClientRect(), bounds = panel.getBoundingClientRect()
  if (field.bottom > bounds.bottom - 16) panel.scrollTop += field.bottom - bounds.bottom + 16
  else if (field.top < bounds.top + 16) panel.scrollTop -= bounds.top + 16 - field.top
})
let profileTrigger: HTMLElement | null = null
async function openProfile(id: string) {
  profileTrigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
  await enter(tab.value, async signal => {
    const profile = await getSocialProfile(id, signal)
    return () => { preparedProfile.value = profile; preparedBlocked.value = []; profileVersion.value++; selected.value = id }
  })
  await nextTick()
  if (selected.value === id && detailPanel.value) { detailPanel.value.scrollTop = 0; detailPanel.value.focus({ preventScroll: true }) }
}
async function closeProfile() {
  selected.value = ''
  await nextTick()
  if (profileTrigger?.isConnected) profileTrigger.focus({ preventScroll: true })
}
useDialog(dialog, () => emit('close'))
const store = useStore(), { t } = useI18n(), accountId = computed(() => String(store.state.accountId))
const tab = ref<'people' | 'encounter' | 'daily' | 'me'>(props.initialTab), selected = ref(''), error = ref(''), busy = ref(false)
const query = ref(''), tag = ref(''), channel = ref('all'), people = ref<SocialProfile[]>([]), nextCursor = ref<number | null>(null)
const daily = ref<DailyQuestion | null>(null), answer = ref(''), mobile = ref(window.innerWidth < 700)
type Tab = typeof tab.value
const ready = ref(false), pending = ref<Tab | null>(null), entryError = ref('')
const preparedProfile = ref<SocialProfile | null>(null), preparedBlocked = ref<Array<{ id: number; username: string }>>([])
const profileVersion = ref(0), initialRoom = ref<EncounterState>()
const pages = new Map<string, { at: number; data: SocialDirectory | DailyQuestion }>()
let navigation = 0, navigationController = new AbortController(), retryEntry: () => void = () => {}
const peopleKey = () => JSON.stringify(['people', query.value, tag.value, channel.value])
function invalidatePages() { pages.clear() }
function rememberPage(key: string, data: SocialDirectory | DailyQuestion) {
  pages.delete(key); pages.set(key, { at: Date.now(), data })
  if (pages.size > 8) pages.delete(pages.keys().next().value!)
}
function cachedPage<T>(key: string): T | undefined {
  const page = pages.get(key)
  return page && Date.now() - page.at < 20_000 ? page.data as T : undefined
}
function cancelEntry() { navigation++; navigationController.abort(); pending.value = null; entryError.value = '' }
async function enter(target: Tab, prepare: (signal: AbortSignal) => Promise<() => void>) {
  cancelEntry()
  const current = navigation, actor = accountId.value
  navigationController = new AbortController()
  const requestController = navigationController
  const signal = requestController.signal
  pending.value = target
  let timedOut = false
  const timeout = setTimeout(() => { timedOut = true; requestController.abort() }, 12_000)
  retryEntry = () => { void enter(target, prepare) }
  try {
    const commit = await prepare(signal)
    if (current !== navigation || actor !== accountId.value || signal.aborted) return
    // Invalidate in-page requests before atomically replacing the visible page.
    revision++; controller.abort(); busy.value = false; error.value = ''
    commit(); ready.value = true
    await nextTick()
    if (dialog.value && !dialog.value.contains(document.activeElement)) {
      dialog.value.querySelector<HTMLButtonElement>('.social-nav [aria-pressed="true"]')?.focus({ preventScroll: true })
    }
  } catch (failure) {
    if (current === navigation && actor === accountId.value) entryError.value = timedOut ? t('social.loadTimeout') : t('social.failed') + ' ' + (failure as Error).message
  } finally { clearTimeout(timeout); if (current === navigation) pending.value = null }
}
let controller = new AbortController(), revision = 0
const resize = () => { mobile.value = window.innerWidth < 700 }
function begin() { controller.abort(); controller = new AbortController(); error.value = ''; busy.value = true; return ++revision }
async function loadPeople(append: boolean) {
  const current = begin()
  const key = peopleKey()
  try {
    const result = await discoverPeople({ q: query.value, tag: tag.value, channel: channel.value, cursor: append ? nextCursor.value || 0 : 0 }, controller.signal)
    if (current !== revision) return
    people.value = append ? [...new Map([...people.value, ...result.users].map(p => [p.id, p])).values()] : result.users
    nextCursor.value = result.nextCursor
    rememberPage(key, { users: people.value, nextCursor: nextCursor.value })
  } catch (failure) { if (current === revision) error.value = (failure as Error).message }
  finally { if (current === revision) busy.value = false }
}
async function loadDaily(append = false) {
  const current = begin()
  try { const result = await getDailyQuestion(append ? daily.value?.nextCursor || '' : '', controller.signal); if (current !== revision) return; daily.value = append && daily.value ? { ...result, answers: [...daily.value.answers, ...result.answers] } : result; if (!append) answer.value = result.myAnswer; rememberPage('daily', daily.value) }
  catch (failure) { if (current === revision) error.value = (failure as Error).message }
  finally { if (current === revision) busy.value = false }
}
async function saveAnswer() { const actor = accountId.value; busy.value = true; try { await answerDailyQuestion(daily.value!.day, answer.value); if (actor === accountId.value) invalidatePages(); if (actor === accountId.value && tab.value === 'daily') await loadDaily() } catch (failure) { if (actor === accountId.value && tab.value === 'daily') error.value = (failure as Error).message } finally { if (actor === accountId.value) busy.value = false } }
async function changeTab(value: Tab) {
  if (ready.value && value === tab.value) {
    cancelEntry()
    if (value !== 'me') selected.value = ''
    return
  }
  await enter(value, async signal => {
    let directory: SocialDirectory | undefined, nextDaily: DailyQuestion | undefined
    let profile: SocialProfile | undefined, blocked: Array<{ id: number; username: string }> = [], room: EncounterState | undefined
    const key = peopleKey()
    if (value === 'people') directory = cachedPage<SocialDirectory>(key) || await discoverPeople({ q: query.value, tag: tag.value, channel: channel.value, cursor: 0 }, signal)
    if (value === 'daily') nextDaily = cachedPage<DailyQuestion>('daily') || await getDailyQuestion('', signal)
    if (value === 'me') [profile, blocked] = await Promise.all([getSocialProfile(accountId.value, signal), getBlockedUsers(signal)])
    if (value === 'encounter') room = await getEncounter(0, signal)
    return () => {
      if (directory) { people.value = directory.users; nextCursor.value = directory.nextCursor; if (pages.get(key)?.data !== directory) rememberPage(key, directory) }
      if (nextDaily) { if (!daily.value || daily.value.day !== nextDaily.day) answer.value = nextDaily.myAnswer; daily.value = nextDaily; if (pages.get('daily')?.data !== nextDaily) rememberPage('daily', nextDaily) }
      if (profile) { preparedProfile.value = profile; preparedBlocked.value = blocked; profileVersion.value++ }
      initialRoom.value = room
      tab.value = value; selected.value = value === 'me' ? accountId.value : ''
      void nextTick(() => {
        if (mainPanel.value) mainPanel.value.scrollTop = 0
        if (detailPanel.value) detailPanel.value.scrollTop = 0
      })
    }
  })
}
function onBlocked(id: string) { invalidatePages(); people.value = people.value.filter(person => person.id !== id); selected.value = '' }
function onSaved(profile: SocialProfile) { invalidatePages(); store.mergeUsers([{ id: profile.id, username: profile.username, name: profile.username, signature: profile.signature }]) }
function startChat(profile: SocialProfile) { emit('chat', profile) }
watch(accountId, () => { cancelEntry(); invalidatePages(); ready.value = false; preparedProfile.value = null; preparedBlocked.value = []; revision++; controller.abort(); people.value = []; daily.value = null; selected.value = ''; query.value = ''; tag.value = ''; channel.value = 'all'; if (!accountId.value) emit('close'); else changeTab('people') })
onMounted(() => { window.addEventListener('resize', resize); changeTab(props.initialTab) })
onBeforeUnmount(() => { cancelEntry(); invalidatePages(); revision++; controller.abort(); window.removeEventListener('resize', resize) })
</script>
