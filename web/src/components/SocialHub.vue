<template>
  <div class="settings-overlay social-overlay" @click.self="emit('close')">
    <div ref="dialog" class="settings-dialog social-hub" :class="{ 'room-mode': tab === 'encounter' && !selected }" role="dialog" aria-modal="true" :aria-label="t('social.discover')" tabindex="-1">
      <header class="settings-header"><h2>{{ t('social.discover') }}</h2><button class="close-settings" :aria-label="t('social.close')" @click="emit('close')">×</button></header>
      <nav class="theme-options social-nav" :aria-label="t('social.discover')">
        <button v-for="item in ['people', 'encounter', 'daily', 'me'] as const" :key="item" :aria-pressed="tab === item" :class="{ active: tab === item }" @click="changeTab(item)">{{ t(`social.${item}`) }}</button>
      </nav>
      <div class="social-loading-track"><div v-if="busy" class="social-loading-line" role="progressbar" :aria-label="t('social.loading')"></div></div>
      <div class="social-layout" :class="{ 'has-profile': selected, 'profile-only': tab === 'me' }">
        <main class="social-main" v-show="!selected || !mobile">
          <p v-if="error" class="social-error" role="alert">{{ t('social.failed') }} {{ error }}</p>
          <template v-if="tab === 'people'">
            <form class="social-form" @submit.prevent="loadPeople(false)"><label>{{ t('social.search') }}<input v-model="query" maxlength="60" class="profile-input" /></label><div class="social-actions"><select v-model="channel" class="profile-input" :aria-label="t('social.channel')"><option value="all">{{ t('social.all') }}</option><option value="mutual">{{ t('social.mutual') }}</option><option value="city">{{ t('social.city') }}</option></select><input v-model="tag" :placeholder="t('social.tags')" :aria-label="t('social.tags')" maxlength="24" class="profile-input" /><button class="save-btn" :disabled="busy">{{ t('polish.searchAction') }}</button></div></form>
            <p class="social-muted">{{ t('social.discoveryHint') }}</p>
            <div v-if="!people.length && !busy && !error" class="social-empty"><h3>{{ t('polish.noPeople') }}</h3><p class="social-muted">{{ t('social.empty') }}</p><button class="cancel-btn" @click="changeTab('me')">{{ t('social.me') }}</button></div>
            <article v-for="person in people" :key="person.id" class="social-person">
              <div class="social-person-head"><img v-if="person.avatarUrl" :src="person.avatarUrl" class="social-avatar" alt="" /><div v-else class="social-avatar social-initial">{{ person.username.slice(0, 1) }}</div><div><h3>{{ person.username }}</h3><p class="social-muted">{{ person.signature || person.city }}</p></div></div>
              <div class="social-tags"><span v-for="interest in person.interests" :key="interest">{{ interest }}</span></div>
              <div class="social-actions"><small class="social-muted">{{ t(`social.reason.${person.reason || 'EXPLORE'}`) }}</small><button class="cancel-btn" @click="selected = person.id">{{ t('social.view') }}</button></div>
            </article>
            <button v-if="nextCursor !== null" class="cancel-btn" :disabled="busy" @click="loadPeople(true)">{{ t('social.more') }}</button>
          </template>
          <EncounterRoom v-if="tab === 'encounter'" @profile="selected = $event" @daily="changeTab('daily')" />
          <template v-if="tab === 'daily' && daily">
            <h2>{{ daily.question }}</h2><p class="social-muted">{{ daily.day }} · {{ t('social.dailyHint') }}</p>
            <form class="social-form" @submit.prevent="saveAnswer"><label>{{ t('social.yourAnswer') }}<textarea v-model="answer" maxlength="500" class="profile-input" rows="3"></textarea></label><button class="save-btn" :disabled="busy">{{ t('social.publish') }}</button><p class="social-muted">{{ t('social.answerPrivacy') }}</p></form>
            <article v-for="item in daily.answers" :key="item.id" class="social-person"><p class="social-answer">{{ item.content }}</p><div class="social-actions"><span>{{ item.author.username }}</span><button class="cancel-btn" @click="selected = item.author.id">{{ t('social.view') }}</button></div></article>
            <p v-if="!daily.answers.length">{{ t('social.noAnswers') }}</p>
            <button v-if="daily.nextCursor" class="cancel-btn" :disabled="busy" @click="loadDaily(true)">{{ t('social.more') }}</button>
          </template>
        </main>
        <aside v-if="selected" class="social-detail"><div v-if="tab !== 'me' || mobile" class="social-detail-header"><button class="cancel-btn" @click="tab === 'me' ? changeTab('people') : selected = ''">‹ {{ t('social.back') }}</button></div><SocialProfileCard :key="accountId" :user-id="selected" @blocked="onBlocked" @chat="startChat" @saved="onSaved" /></aside>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useStore } from '../store'
import { discoverPeople, getDailyQuestion, answerDailyQuestion } from '../api/social'
import type { SocialProfile, DailyQuestion } from '../types/social'
import SocialProfileCard from './SocialProfileCard.vue'
import EncounterRoom from './EncounterRoom.vue'
import { useDialog } from '../composables/useDialog'
const emit = defineEmits<{ close: []; chat: [profile: SocialProfile] }>()
const props = withDefaults(defineProps<{ initialTab?: 'people' | 'me' }>(), { initialTab: 'people' })
const dialog = ref<HTMLElement | null>(null)
useDialog(dialog, () => emit('close'))
const store = useStore(), { t } = useI18n(), accountId = computed(() => String(store.state.accountId))
const tab = ref<'people' | 'encounter' | 'daily' | 'me'>('people'), selected = ref(''), error = ref(''), busy = ref(false)
const query = ref(''), tag = ref(''), channel = ref('all'), people = ref<SocialProfile[]>([]), nextCursor = ref<number | null>(null)
const daily = ref<DailyQuestion | null>(null), answer = ref(''), mobile = ref(window.innerWidth < 700)
let controller = new AbortController(), revision = 0
const resize = () => { mobile.value = window.innerWidth < 700 }
function begin() { controller.abort(); controller = new AbortController(); error.value = ''; busy.value = true; return ++revision }
async function loadPeople(append: boolean) {
  const current = begin()
  try {
    const result = await discoverPeople({ q: query.value, tag: tag.value, channel: channel.value, cursor: append ? nextCursor.value || 0 : 0 }, controller.signal)
    if (current !== revision) return
    people.value = append ? [...new Map([...people.value, ...result.users].map(p => [p.id, p])).values()] : result.users
    nextCursor.value = result.nextCursor
  } catch (failure) { if (current === revision) error.value = (failure as Error).message }
  finally { if (current === revision) busy.value = false }
}
async function loadDaily(append = false) {
  const current = begin()
  try { const result = await getDailyQuestion(append ? daily.value?.nextCursor || '' : '', controller.signal); if (current !== revision) return; daily.value = append && daily.value ? { ...result, answers: [...daily.value.answers, ...result.answers] } : result; if (!append) answer.value = result.myAnswer }
  catch (failure) { if (current === revision) error.value = (failure as Error).message }
  finally { if (current === revision) busy.value = false }
}
async function saveAnswer() { const actor = accountId.value; busy.value = true; try { await answerDailyQuestion(daily.value!.day, answer.value); if (actor === accountId.value && tab.value === 'daily') await loadDaily() } catch (failure) { if (actor === accountId.value && tab.value === 'daily') error.value = (failure as Error).message } finally { if (actor === accountId.value) busy.value = false } }
function changeTab(value: typeof tab.value) { revision++; controller.abort(); busy.value = false; error.value = ''; tab.value = value; selected.value = value === 'me' ? accountId.value : ''; if (value === 'daily') void loadDaily(); if (value === 'people') void loadPeople(false) }
function onBlocked(id: string) { people.value = people.value.filter(person => person.id !== id); selected.value = '' }
function onSaved(profile: SocialProfile) { store.mergeUsers([{ id: profile.id, username: profile.username, name: profile.username, signature: profile.signature }]) }
function startChat(profile: SocialProfile) { emit('chat', profile) }
watch(accountId, () => { revision++; controller.abort(); people.value = []; daily.value = null; selected.value = ''; query.value = ''; tag.value = ''; channel.value = 'all'; if (!accountId.value) emit('close'); else changeTab('people') })
onMounted(() => { window.addEventListener('resize', resize); changeTab(props.initialTab) })
onBeforeUnmount(() => { revision++; controller.abort(); window.removeEventListener('resize', resize) })
</script>
