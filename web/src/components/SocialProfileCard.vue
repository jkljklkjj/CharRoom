<template>
  <section ref="profileRoot" class="social-profile" :data-cover="profile?.cover || 'sunset'">
    <div v-if="!profile" class="social-profile-placeholder" :aria-busy="!error">
      <p v-if="error" class="social-error" role="alert">{{ error }}</p>
      <button v-if="error" class="cancel-btn" @click="retryKey++">{{ t('polish.retry') }}</button>
      <p v-else class="social-empty-loading" role="status"><span class="social-spinner" aria-hidden="true"></span>{{ t('social.loading') }}</p>
    </div>
    <template v-else>
      <!-- The card is presentation only. All controls live in the separate tools area. -->
      <article v-if="!editing" class="social-profile-card">
        <div class="social-cover" aria-hidden="true"></div>
        <div class="social-profile-content">
          <img v-if="profile.avatarUrl" :src="profile.avatarUrl" class="social-avatar" alt="" />
          <div v-else class="social-avatar social-initial">{{ profile.username.slice(0, 1) }}</div>
          <h2 tabindex="-1">{{ profile.username }}</h2>
          <p class="social-muted social-profile-account">{{ t('social.account') }} {{ profile.id }} · {{ t(`social.relation.${profile.relation}`) }}</p>
          <p v-if="profile.signature" class="social-profile-signature">{{ profile.signature }}</p>
          <template v-if="detailsVisible">
            <section v-if="profile.bio"><h3>{{ t('social.bio') }}</h3><p>{{ profile.bio }}</p></section>
            <div v-if="profile.interests?.length" class="social-tags"><span v-for="tag in profile.interests" :key="tag">{{ tag }}</span></div>
            <p v-if="profile.city" class="social-muted">{{ t('social.city') }} · {{ profile.city }}</p>
            <section v-if="profile.intent" class="social-intent"><h3>{{ t('social.intent') }}</h3><p>{{ profile.intent }}</p></section>
          </template>
          <p v-else class="social-muted">{{ t('social.hidden') }}</p>
        </div>
      </article>
      <div class="social-profile-tools">
        <p v-if="error" class="social-error" role="alert">{{ error }}</p>
        <p v-if="notice" class="social-notice" role="status">{{ notice }}</p>
        <h2 v-if="editing">{{ t('social.edit') }}</h2>
        <form v-if="editing" @submit.prevent="save" class="social-form social-profile-editor">
          <fieldset class="social-fields" :disabled="busy">
          <label>{{ t('social.username') }}<input v-model="form.username" required maxlength="32" class="profile-input" /></label>
          <label>{{ t('social.signature') }}<input v-model="form.signature" maxlength="120" class="profile-input" /></label>
          <label>{{ t('social.bio') }}<textarea v-model="form.bio" maxlength="300" class="profile-input" rows="3"></textarea></label>
          <label>{{ t('social.tags') }}<input v-model="interestText" class="profile-input" :placeholder="t('social.tagsHint')" /></label>
          <label>{{ t('social.city') }}<input v-model="form.city" maxlength="40" class="profile-input" /></label>
          <label>{{ t('social.intent') }}<textarea v-model="form.intent" maxlength="120" class="profile-input" rows="2"></textarea></label>
          <label>{{ t('social.cover') }}<select v-model="form.cover" class="profile-input"><option value="sunset">{{ t('social.sunset') }}</option><option value="ocean">{{ t('social.ocean') }}</option><option value="forest">{{ t('social.forest') }}</option></select></label>
          <label>{{ t('social.visibility') }}<select v-model="form.visibility" class="profile-input"><option value="PUBLIC">{{ t('social.public') }}</option><option value="FRIENDS">{{ t('social.friends') }}</option><option value="SELF">{{ t('social.self') }}</option></select></label>
          <label class="social-check"><input v-model="form.discoverable" type="checkbox" />{{ t('social.discoverable') }}</label>
          <label class="social-check"><input v-model="form.mutualDiscovery" type="checkbox" />{{ t('social.mutualDiscovery') }}</label>
          <p class="social-muted">{{ t('social.privacyHint') }}</p>
          <div class="social-actions social-form-footer"><button class="save-btn" :disabled="busy || !form.username.trim()">{{ t(busy ? 'polish.saving' : 'social.save') }}</button><button type="button" class="cancel-btn" :disabled="busy" @click="editing = false">{{ t('social.cancel') }}</button></div>
          </fieldset>
        </form>
        <template v-else>
          <div class="social-actions social-profile-primary">
            <button v-if="profile.relation === 'SELF'" class="save-btn" :disabled="busy || !profile.settings" @click="edit">{{ t('social.edit') }}</button>
            <button v-else-if="profile.relation === 'FRIEND'" class="save-btn" :disabled="busy" @click="emit('chat', profile)">{{ t('social.chat') }}</button>
            <button v-else class="save-btn" :disabled="busy || profile.relation === 'PENDING'" :aria-expanded="requesting" @click="requesting = !requesting">{{ t(profile.relation === 'PENDING' ? 'social.pending' : 'social.add') }}</button>
          </div>
          <details v-if="profile.relation === 'SELF'" class="social-profile-secondary">
            <summary>{{ t('social.preview') }}</summary>
            <label class="social-form">{{ t('social.preview') }}<select v-model="audience" class="profile-input"><option value="self">{{ t('social.relation.SELF') }}</option><option value="stranger">{{ t('social.stranger') }}</option><option value="friend">{{ t('social.friends') }}</option></select></label>
          </details>
          <section v-if="profile.relation === 'SELF' && blocked.length">
            <h3>{{ t('social.blockedList') }}</h3>
            <div v-for="person in blocked" :key="person.id" class="social-actions"><span>{{ person.username }}</span><button class="cancel-btn" :disabled="busy" @click="unblock(person.id)">{{ t('social.unblock') }}</button></div>
          </section>
          <form v-if="requesting" class="social-form" @submit.prevent="requestFriend"><label>{{ t('social.requestReason') }}<textarea v-model="reason" maxlength="120" class="profile-input" rows="2"></textarea></label><button class="save-btn" :disabled="busy">{{ t('social.sendRequest') }}</button></form>
          <details v-if="profile.relation !== 'SELF'" class="social-profile-secondary"><summary>{{ t('social.moreActions') }}</summary>
            <div class="social-actions"><button class="cancel-btn" :disabled="busy" @click="block">{{ t('social.block') }}</button><button class="cancel-btn" @click="reporting = !reporting">{{ t('social.report') }}</button></div>
            <form v-if="reporting" class="social-form" @submit.prevent="report"><label>{{ t('social.reportReason') }}<textarea v-model="reportReason" required maxlength="500" class="profile-input"></textarea></label><button class="save-btn" :disabled="busy">{{ t('social.submit') }}</button></form>
          </details>
        </template>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onBeforeUnmount, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { getSocialProfile, saveSocialProfile, sendSocialFriendRequest, blockSocialUser, reportSocialUser, getBlockedUsers, unblockSocialUser } from '../api/social'
import { useStore } from '../store'
import { loadOwnProfileCache, saveOwnProfileCache } from '../utils/socialProfileCache'
import type { SocialProfile, SocialEdit } from '../types/social'
const props = defineProps<{ userId: string; initialProfile?: SocialProfile | null; initialBlocked?: Array<{ id: number; username: string }> }>()
const emit = defineEmits<{ chat: [profile: SocialProfile]; saved: [profile: SocialProfile]; blocked: [id: string]; changed: [] }>()
const { t } = useI18n()
const store = useStore()
/** 是否在看自己的名片（只有本人资料走本地缓存） */
const isOwn = computed(() => String(store.state.accountId) === String(props.userId))
const profile = ref<SocialProfile | null>(null), error = ref(''), busy = ref(false), editing = ref(false)
const notice = ref('')
const profileRoot = ref<HTMLElement | null>(null)
const retryKey = ref(0)
const blocked = ref<Array<{ id: number; username: string }>>([])
const requesting = ref(false), reporting = ref(false), reason = ref(''), reportReason = ref(''), interestText = ref(''), audience = ref('self')
watch(editing, async () => {
  await nextTick()
  const root = profileRoot.value
  const target = root?.querySelector<HTMLElement>(editing.value ? '.social-profile-editor input' : '.social-profile-card h2')
  target?.focus({ preventScroll: true })
  const panel = root?.closest<HTMLElement>('.social-detail')
  if (panel) panel.scrollTop = 0
})
let controller = new AbortController(), generation = 0
const form = reactive<SocialEdit>({ username: '', signature: '', bio: '', interests: [], city: '', intent: '', cover: 'sunset', visibility: 'SELF', discoverable: false, mutualDiscovery: false })
const detailsVisible = computed(() => {
  if (!profile.value) return false
  if (profile.value.relation !== 'SELF' || audience.value === 'self') return profile.value.detailsVisible
  return profile.value.settings?.visibility === 'PUBLIC' || (audience.value === 'friend' && profile.value.settings?.visibility === 'FRIENDS')
})
watch(() => `${props.userId}:${retryKey.value}`, async () => {
  const id = props.userId
  const current = ++generation
  notice.value = ''
  controller.abort(); controller = new AbortController(); profile.value = null; error.value = ''; busy.value = false; blocked.value = []; reason.value = ''; reportReason.value = ''; editing.value = false; requesting.value = false; reporting.value = false; audience.value = 'self'
  if (retryKey.value === 0 && props.initialProfile?.id === id) { profile.value = props.initialProfile; blocked.value = props.initialBlocked || []; return }
  // 本人名片：先渲染本地缓存，接口返回后再覆盖（失败时缓存仍可见）
  if (isOwn.value) {
    const cached = loadOwnProfileCache(store.state.accountId)
    if (cached) profile.value = cached
  }
  try { const next = await getSocialProfile(id, controller.signal); if (current === generation) { profile.value = next; blocked.value = []; if (next.relation === 'SELF') { saveOwnProfileCache(store.state.accountId, next); const users = await getBlockedUsers(); if (current === generation) blocked.value = users } } }
  catch (failure) { if (current === generation && !controller.signal.aborted) error.value = (failure as Error).message }
}, { immediate: true })
onBeforeUnmount(() => { generation++; controller.abort() })
function edit() {
  if (!profile.value?.settings) return
  const saved = profile.value.settings
  Object.assign(form, { username: profile.value.username, signature: profile.value.signature || '', bio: saved.bio, interests: saved.interests,
    city: saved.city, intent: saved.intent, cover: saved.cover, visibility: saved.visibility, discoverable: saved.discoverable, mutualDiscovery: saved.mutualDiscovery })
  interestText.value = form.interests.join('、'); editing.value = true
}
async function perform(work: () => Promise<void>) {
  if (busy.value) return
  const current = generation
  busy.value = true; error.value = ''; notice.value = ''
  try { await work(); if (current === generation) emit('changed') } catch (failure) { if (current === generation) error.value = (failure as Error).message }
  finally { if (current === generation) busy.value = false }
}
const save = () => perform(async () => { const current = generation; const next = await saveSocialProfile({ ...form, interests: interestText.value.split(/[、,，]/).map(v => v.trim()).filter(Boolean) }); if (current !== generation) return; profile.value = next; if (next.relation === 'SELF') saveOwnProfileCache(store.state.accountId, next); editing.value = false; notice.value = t('polish.saved'); emit('saved', next) })
const requestFriend = () => perform(async () => { const id = props.userId; await sendSocialFriendRequest(id, reason.value); if (id !== props.userId || !profile.value) return; profile.value.relation = 'PENDING'; requesting.value = false })
const block = () => perform(async () => { const id = props.userId; await blockSocialUser(id); if (id === props.userId) emit('blocked', id) })
const unblock = (id: number) => perform(async () => { const current = generation; await unblockSocialUser(id); if (current === generation) blocked.value = blocked.value.filter(user => user.id !== id) })
const report = () => perform(async () => { const id = props.userId; await reportSocialUser({ targetId: Number(id), reason: reportReason.value }); if (id === props.userId) { reporting.value = false; reportReason.value = ''; notice.value = t('social.reported') } })
</script>
