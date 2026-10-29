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
          <div class="social-profile-identity">
            <img v-if="profile.avatarUrl" :src="profile.avatarUrl" class="social-avatar" alt="" width="68" height="68" decoding="async" />
            <div v-else class="social-avatar social-initial">{{ profile.username.slice(0, 1) }}</div>
            <span class="social-profile-relation">{{ t(`social.relation.${profile.relation}`) }}</span>
          </div>
          <h2 tabindex="-1">{{ profile.username }}</h2>
          <div class="social-profile-meta">
            <p class="social-muted social-profile-account">{{ t('social.account') }} {{ profile.id }}</p>
            <p v-if="detailsVisible && profile.city" class="social-muted social-profile-city">{{ profile.city }}</p>
          </div>
          <p v-if="profile.signature" class="social-profile-signature">{{ profile.signature }}</p>
          <template v-if="detailsVisible">
            <section v-if="profile.bio" class="social-profile-bio"><h3>{{ t('social.bio') }}</h3><p>{{ profile.bio }}</p></section>
            <div v-if="profile.interests?.length" class="social-tags"><span v-for="tag in profile.interests" :key="tag">{{ tag }}</span></div>
            <section v-if="profile.intent" class="social-intent"><h3>{{ t('social.intent') }}</h3><p>{{ profile.intent }}</p></section>
          </template>
          <p v-else class="social-muted">{{ t('social.hidden') }}</p>
        </div>
      </article>
      <div class="social-profile-tools">
        <p v-if="error" class="social-error" role="alert">{{ error }} <button v-if="isOwn && !editing" class="cancel-btn" @click="retryKey++">{{ t('polish.retry') }}</button></p>
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
          <div v-if="profile.relation === 'SELF'" class="social-visibility-panel">
            <p class="social-muted" role="status">{{ visibilityState }}</p>
            <button v-if="!isDiscoverable || profile.settings?.visibility !== 'PUBLIC'" type="button" class="cancel-btn" :disabled="busy || refreshing || !verified" @click="publishOwn">{{ t('social.publishCard') }}</button>
            <p v-if="!isDiscoverable || profile.settings?.visibility !== 'PUBLIC'" class="social-muted">{{ t('social.publishHint') }}</p>
          </div>
          <div class="social-actions social-profile-primary">
            <button v-if="profile.relation === 'SELF'" class="save-btn" :disabled="busy || refreshing || !verified || !profile.settings" @click="edit">{{ t(refreshing ? 'sync.refreshing' : 'social.edit') }}</button>
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
import {sessionEpoch} from '../services/sessionEpoch'
const props = defineProps<{ userId: string; initialProfile?: SocialProfile | null; refreshInitial?:boolean; initialBlocked?: Array<{ id: number; username: string }> }>()
const emit = defineEmits<{ chat: [profile: SocialProfile]; saved: [profile: SocialProfile]; blocked: [id: string]; changed: [] }>()
const { t } = useI18n()
const store = useStore()
/** 是否在看自己的名片（只有本人资料走本地缓存） */
const isOwn = computed(() => String(store.state.accountId) === String(props.userId))
const profile = ref<SocialProfile | null>(null), error = ref(''), busy = ref(false), editing = ref(false)
const notice = ref(''),refreshing=ref(false),verified=ref(false)
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
const form = reactive<SocialEdit>({ username: '', signature: '', bio: '', interests: [], city: '', intent: '', cover: 'sunset', visibility: 'FRIENDS', discoverable: false, mutualDiscovery: false })
const detailsVisible = computed(() => {
  if (!profile.value) return false
  if (profile.value.relation !== 'SELF' || audience.value === 'self') return profile.value.detailsVisible
  return profile.value.settings?.visibility === 'PUBLIC' || (audience.value === 'friend' && profile.value.settings?.visibility === 'FRIENDS')
})
watch(() => `${store.state.accountId}:${props.userId}:${retryKey.value}`, async () => {
  const id=props.userId,owner=String(store.state.accountId),session=sessionEpoch.capture(),current=++generation
  controller.abort();controller=new AbortController()
  const signal=controller.signal
  const active=()=>current===generation&&session.isCurrent()&&owner===String(store.state.accountId)&&!signal.aborted
  notice.value='';profile.value=null;error.value='';busy.value=false;blocked.value=[]
  reason.value='';reportReason.value='';editing.value=false;requesting.value=false;reporting.value=false;audience.value='self'
  verified.value=false;refreshing.value=false
  if(retryKey.value===0&&props.initialProfile?.id===id){
    profile.value=props.initialProfile;blocked.value=props.initialBlocked||[]
    if(!props.refreshInitial){verified.value=true;if(isOwn.value)saveOwnProfileCache(owner,props.initialProfile);return}
  }
  if(isOwn.value&&!profile.value)profile.value=loadOwnProfileCache(owner)
  refreshing.value=true
  const [next,users]=await Promise.allSettled([getSocialProfile(id,signal),isOwn.value?getBlockedUsers(signal):Promise.resolve([])])
  if(!active())return
  if(next.status==='fulfilled'){
    profile.value=next.value;verified.value=true
    if(next.value.relation==='SELF')saveOwnProfileCache(owner,next.value)
  }else error.value=String(next.reason instanceof Error?next.reason.message:next.reason)
  if(users.status==='fulfilled')blocked.value=users.value
  else error.value=String(users.reason instanceof Error?users.reason.message:users.reason)
  refreshing.value=false
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
const isDiscoverable = computed(() => Boolean(profile.value?.settings?.discoverable))
const visibilityState = computed(() => {
  if (!profile.value?.settings) return ''
  if (!isDiscoverable.value) return t('social.stateHidden')
  // 候选池要求“允许推荐 + 可见范围公开”，只勾推荐不改可见性依然进不去
  return profile.value.settings.visibility === 'PUBLIC' ? t('social.statePublic') : t('social.stateNeedsPublic')
})
/** 一键公开名片：把可见性设为公开并打开推荐，省掉 5 个下拉框的配置成本 */
function publishOwn() {
  if (!profile.value?.settings) return
  const saved = profile.value.settings
  Object.assign(form, { username: profile.value.username, signature: profile.value.signature || '', bio: saved.bio,
    interests: saved.interests, city: saved.city, intent: saved.intent, cover: saved.cover,
    visibility: 'PUBLIC', discoverable: true, mutualDiscovery: saved.mutualDiscovery })
  interestText.value = form.interests.join('、')
  void save()
}
const save = () => perform(async () => { const current = generation; const next = await saveSocialProfile({ ...form, interests: interestText.value.split(/[、,，]/).map(v => v.trim()).filter(Boolean) }); if (current !== generation) return; profile.value = next; if (next.relation === 'SELF') saveOwnProfileCache(store.state.accountId, next); editing.value = false; notice.value = t('polish.saved'); emit('saved', next) })
const requestFriend = () => perform(async () => { const id = props.userId; await sendSocialFriendRequest(id, reason.value); if (id !== props.userId || !profile.value) return; profile.value.relation = 'PENDING'; requesting.value = false })
const block = () => perform(async () => { const id = props.userId; await blockSocialUser(id); if (id === props.userId) emit('blocked', id) })
const unblock = (id: number) => perform(async () => { const current = generation; await unblockSocialUser(id); if (current === generation) blocked.value = blocked.value.filter(user => user.id !== id) })
const report = () => perform(async () => { const id = props.userId; await reportSocialUser({ targetId: Number(id), reason: reportReason.value }); if (id === props.userId) { reporting.value = false; reportReason.value = ''; notice.value = t('social.reported') } })
</script>
