<template>
  <main class="admin-shell">
    <aside class="admin-nav">
      <RouterLink to="/" class="admin-brand"><BrandMark /></RouterLink>
      <span class="admin-eyebrow">{{ t('admin.workspace') }}</span>
      <div class="admin-nav-current">{{ t('admin.releases') }} <span>01</span></div>
      <p class="admin-nav-note">{{ t('admin.navNote') }}</p>
      <div class="admin-nav-footer"><span v-if="actor">{{ t('admin.account', { id: actor }) }}</span><button v-if="actor" @click="logout">{{ t('admin.logout') }}</button><RouterLink to="/app">{{ t('admin.openChat') }} ↗</RouterLink></div>
    </aside>
    <section class="admin-main">
      <header class="admin-heading"><div><span class="admin-eyebrow">{{ t('admin.operations') }}</span><h1>{{ t('admin.releases') }}</h1><p>{{ t('admin.intro') }}</p></div><select v-model="locale" :aria-label="t('admin.language')"><option value="zh-CN">中文</option><option value="en">English</option><option value="ja">日本語</option></select></header>
      <p v-if="error" class="admin-alert" role="alert">{{ error }}<button @click="error=''" :aria-label="t('admin.close')">×</button></p>
      <p v-if="notice" class="admin-notice" role="status">{{ notice }}</p>
      <form v-if="!actor" class="admin-login admin-card" @submit.prevent="signIn">
        <span class="admin-eyebrow">{{ t('admin.restricted') }}</span><h2>{{ t('admin.signIn') }}</h2><p>{{ t('admin.signInHint') }}</p>
        <label>{{ t('admin.loginAccount') }}<input v-model="account" required autocomplete="username" maxlength="100" /></label>
        <label>{{ t('admin.password') }}<input v-model="password" type="password" required autocomplete="current-password" maxlength="128" /></label>
        <button class="admin-primary" :disabled="busy">{{ busy ? t('admin.working') : t('admin.signIn') }}</button>
      </form>
      <template v-else>
        <div class="admin-toolbar"><div class="admin-platforms" role="group" :aria-label="t('admin.platform')"><button v-for="p in releasePlatforms" :key="p" :class="{ selected: platform===p }" :disabled="busy" @click="switchPlatform(p)">{{ p }}</button></div><button :disabled="busy" @click="manualRefresh">{{ t('admin.refresh') }}</button></div>
        <section class="admin-current admin-card"><div><span class="admin-eyebrow">{{ t('admin.current') }}</span><strong>{{ active ? `v${active.version}` : t('admin.noPublished') }}</strong><p>{{ active ? t('admin.currentHint', { minimum: active.minSupportedVersion }) : t('admin.configHint') }}</p></div><span class="admin-tag" :class="{ live: active }">{{ active ? t('admin.live') : t('admin.notSet') }}</span></section>
        <div class="admin-grid">
          <section class="admin-list admin-card"><div class="admin-section-heading"><h2>{{ t('admin.history') }}</h2><button :disabled="busy" @click="startDraft">＋ {{ t('admin.newDraft') }}</button></div>
            <p v-if="loading" class="admin-empty">{{ t('admin.loading') }}</p>
            <p v-else-if="!rows.length" class="admin-empty">{{ t('admin.empty') }}</p>
            <button v-for="row in rows" :key="row.id" class="admin-release-row" :class="{ selected: selected?.id===row.id }" :disabled="busy" @click="choose(row)"><span><strong>v{{ row.version }}</strong><small>{{ date(row.createdAt) }}</small></span><span class="admin-tag" :class="{ live: active?.id===row.id }">{{ active?.id===row.id ? t('admin.live') : t(`admin.states.${row.state}`) }}</span></button>
            <div class="admin-pagination"><button :disabled="busy || loading || page===0" @click="changePage(-1)">← {{ t('admin.previous') }}</button><span>{{ page+1 }}</span><button :disabled="busy || loading || !hasMore" @click="changePage(1)">{{ t('admin.next') }} →</button></div>
          </section>
          <section class="admin-card admin-editor"><div class="admin-section-heading"><h2>{{ selected ? `v${selected.version}` : t('admin.newDraft') }}</h2><span v-if="selected" class="admin-revision">r{{ selected.revision }}</span></div>
            <form @submit.prevent="save"><fieldset :disabled="busy || readonly">
              <div class="admin-fields"><label>{{ t('admin.version') }}<input v-model="form.version" required maxlength="40" pattern="[0-9]+(\.[0-9]+){0,3}" placeholder="1.3.0" /></label><label>{{ t('admin.minimum') }}<input v-model="form.minSupportedVersion" required maxlength="40" pattern="[0-9]+(\.[0-9]+){0,3}" placeholder="1.0.0" /></label></div>
              <label>{{ t('admin.download') }}<input v-model="form.downloadUrl" type="url" required maxlength="2048" placeholder="https://…/Qingliao.apk" /></label>
              <div class="admin-fields admin-artifact">
                <label>{{ t('admin.packageType') }}<select v-model="form.packageType"><option v-if="platform==='android'" value="apk">APK</option><template v-else><option value="msi">Windows MSI</option><option value="dmg">macOS DMG</option><option value="deb">Linux DEB</option></template></select></label>
                <label>{{ t('admin.architecture') }}<select v-model="form.architecture"><option v-if="platform==='android'" value="universal">Universal</option><option value="x64">x64</option><option value="arm64">ARM64</option></select></label>
                <label>{{ t('admin.fileSize') }}<input v-model.number="form.fileSize" name="fileSize" type="number" min="1" max="2147483648" step="1" required /></label>
                <label v-if="platform==='android'">{{ t('admin.versionCode') }}<input v-model.number="form.versionCode" name="versionCode" type="number" min="1" max="2147483647" step="1" required /></label>
              </div>
              <label>{{ t('admin.sha256') }}<input v-model="form.sha256" name="sha256" required pattern="[0-9a-fA-F]{64}" minlength="64" maxlength="64" spellcheck="false" /></label><p class="admin-help">{{ t('admin.artifactHint') }}</p>
              <label>{{ t('admin.notes') }}<textarea v-model="form.releaseNotes" required maxlength="10000" rows="7" :placeholder="t('admin.notesPlaceholder')" /></label>
              <label class="admin-checkbox"><input v-model="form.forceUpdate" type="checkbox" />{{ t('admin.force') }}</label><p class="admin-help">{{ t('admin.forceHint') }}</p>
            </fieldset><div class="admin-actions"><button v-if="!readonly" type="submit" class="admin-primary" :disabled="busy">{{ t('admin.saveDraft') }}</button><button v-if="selected?.state==='DRAFT'" type="button" :disabled="busy || dirty" @click="review='publish'">{{ t('admin.publish') }}</button><button v-if="selected?.state==='PUBLISHED'" type="button" class="admin-danger" :disabled="busy" @click="review='withdraw'">{{ t('admin.withdraw') }}</button></div><p v-if="selected?.state==='DRAFT' && dirty" class="admin-help">{{ t('admin.saveFirst') }}</p><p v-if="readonly" class="admin-help">{{ t('admin.immutable') }}</p></form>
            <section v-if="selected" class="admin-audit"><h3>{{ t('admin.audit') }}</h3><p v-if="!audits.length" class="admin-help">{{ t('admin.noAudit') }}</p><div v-for="item in audits" :key="item.id"><span>{{ t(`admin.auditActions.${item.action}`) }} · r{{ item.revision }}</span><small>{{ t('admin.account', { id: item.actorId }) }} · {{ date(item.createdAt) }}</small></div></section>
          </section>
        </div>
      </template>
    </section>
    <div v-if="review && selected" class="admin-modal-backdrop" @keydown.esc="review=null"><section role="dialog" aria-modal="true" aria-labelledby="admin-review-title" class="admin-card admin-modal"><h2 id="admin-review-title">{{ t(review==='publish' ? 'admin.reviewPublish' : 'admin.reviewWithdraw') }}</h2><p>{{ t(review==='publish' ? 'admin.publishHint' : 'admin.withdrawHint') }}</p><dl><dt>{{ t('admin.platform') }}</dt><dd>{{ selected.platform }}</dd><dt>{{ t('admin.version') }}</dt><dd>{{ selected.version }}</dd><dt>{{ t('admin.minimum') }}</dt><dd>{{ selected.minSupportedVersion }}</dd><dt>{{ t('admin.force') }}</dt><dd>{{ t(selected.forceUpdate ? 'admin.yes' : 'admin.no') }}</dd><dt>{{ t('admin.download') }}</dt><dd>{{ selected.downloadUrl }}</dd><dt>{{ t('admin.packageType') }}</dt><dd>{{ selected.packageType }} · {{ selected.architecture }}</dd><dt>{{ t('admin.fileSize') }}</dt><dd>{{ selected.fileSize }}</dd><dt>{{ t('admin.sha256') }}</dt><dd>{{ selected.sha256 }}</dd></dl><pre>{{ selected.releaseNotes }}</pre><div class="admin-actions"><button autofocus :disabled="busy" @click="review=null">{{ t('admin.cancel') }}</button><button class="admin-primary" :disabled="busy" @click="commitTransition">{{ t('admin.confirm') }}</button></div></section></div>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BrandMark from '../components/BrandMark.vue'
import { AdminApi, AdminApiError, releasePlatforms } from '../api/admin'
import type { AppRelease, ReleaseAudit, ReleaseInput, ReleasePlatform } from '../api/admin'
const { t, locale } = useI18n()
const api = new AdminApi(), actor = ref(0), account = ref(''), password = ref('')
const busy = ref(false), loading = ref(false), error = ref(''), notice = ref('')
const platform = ref<ReleasePlatform>('android'), page = ref(0), rows = ref<AppRelease[]>([]), active = ref<AppRelease|null>(null), hasMore = ref(false)
const selected = ref<AppRelease|null>(null), audits = ref<ReleaseAudit[]>([]), review = ref<'publish'|'withdraw'|null>(null)
const empty = (): ReleaseInput => ({ platform: platform.value, version:'', minSupportedVersion:'1.0.0', forceUpdate:false, downloadUrl:'', releaseNotes:'', packageType:platform.value==='android'?'apk':'msi', architecture:platform.value==='android'?'universal':'x64', fileSize:0, sha256:'', versionCode:platform.value==='android'?1:0 })
const form = reactive<ReleaseInput>(empty())
let listRevision = 0, auditRevision = 0, uiEpoch = 0, mounted = true
const readonly = computed(() => selected.value !== null && selected.value.state !== 'DRAFT')
let previousFocus: HTMLElement | null = null
watch(review, async value => {
  if (value) {
    previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    await nextTick()
    document.querySelector<HTMLElement>('.admin-modal button')?.focus()
  } else previousFocus?.focus()
})
function trapModalFocus(event: KeyboardEvent) {
  if (event.key !== 'Tab' || !review.value) return
  const controls = [...document.querySelectorAll<HTMLButtonElement>('.admin-modal button:not(:disabled)')]
  const first = controls[0], last = controls.at(-1)
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
const dirty = computed(() => !!selected.value && (Object.keys(form) as (keyof ReleaseInput)[]).some(key => form[key] !== selected.value![key]))
function date(value: string) { return new Date(value).toLocaleString(String(locale.value)) }
function fail(reason: unknown) {
  if (!mounted) return
  if (reason instanceof AdminApiError && (reason.status===401 || reason.status===403)) logout()
  error.value = reason instanceof AdminApiError && reason.status===403 ? t('admin.forbidden') : reason instanceof Error ? reason.message : t('admin.failed')
}
async function run(action: (current: () => boolean) => Promise<void>) {
  if (busy.value) return
  const epoch=uiEpoch
  const current=() => mounted && epoch===uiEpoch
  busy.value=true; error.value=''; notice.value=''
  try { await action(current) } catch (reason) { if(current()) fail(reason) } finally { if(current()) busy.value=false }
}
async function refresh() {
  const revision=++listRevision, target=platform.value
  loading.value=true
  try {
    const result=await api.list(target,page.value)
    if (!mounted || revision!==listRevision || target!==platform.value) return
    rows.value=result.items; active.value=result.active; hasMore.value=result.hasMore
  } catch (reason) { if (revision===listRevision) fail(reason) }
  finally { if (revision===listRevision) loading.value=false }
}
function newDraft() { auditRevision++; selected.value=null; audits.value=[]; review.value=null; Object.assign(form,empty()) }
async function select(row: AppRelease) {
  selected.value=row; Object.assign(form,{platform:row.platform,version:row.version,minSupportedVersion:row.minSupportedVersion,forceUpdate:row.forceUpdate,downloadUrl:row.downloadUrl,releaseNotes:row.releaseNotes,packageType:row.packageType,architecture:row.architecture,fileSize:row.fileSize,sha256:row.sha256,versionCode:row.versionCode}); audits.value=[]
  const revision=++auditRevision
  try { const result=await api.audits(row.id); if (mounted && revision===auditRevision) audits.value=result }
  catch (reason) { if (revision===auditRevision) fail(reason) }
}
function discardAllowed() { return !dirty.value || window.confirm(t('admin.discard')) }
function startDraft() { if(discardAllowed())newDraft() }
function choose(row: AppRelease) { if(discardAllowed())void select(row) }
async function manualRefresh() {
  if(!discardAllowed())return
  const id=selected.value?.id
  await refresh()
  if(id) { const row=rows.value.find(row=>row.id===id); if(row)await select(row); else newDraft() }
}
function switchPlatform(target: ReleasePlatform) { if(!discardAllowed())return; platform.value=target; page.value=0; rows.value=[]; active.value=null; hasMore.value=false; newDraft(); void refresh() }
function changePage(delta: number) { page.value+=delta; void refresh() }
async function signIn() { await run(async current => { try { const me=await api.login(account.value,password.value); if(!current())return; actor.value=me.userId; await refresh() } finally { if(current())password.value='' } }) }
async function save() { await run(async current => { const input={...form}; const row=selected.value ? await api.edit(selected.value,input) : await api.create(input); if(!current())return; await refresh(); if(!current())return; await select(row); if(current())notice.value=t('admin.saved') }) }
async function commitTransition() { await run(async current => { if (!selected.value || !review.value) return; const row=await api.transition(selected.value,review.value); if(!current())return; review.value=null; await refresh(); if(!current())return; await select(row); if(current())notice.value=t('admin.updated') }) }
function logout() { uiEpoch++; listRevision++; auditRevision++; api.logout(); busy.value=false; loading.value=false; actor.value=0; rows.value=[]; active.value=null; error.value=''; notice.value=''; password.value=''; newDraft() }
onMounted(async () => { document.addEventListener('keydown', trapModalFocus); if (api.hasSession) await run(async current => { const me=await api.me(); if(!current())return; actor.value=me.userId; await refresh() }) })
onBeforeUnmount(() => { document.removeEventListener('keydown', trapModalFocus); mounted=false; listRevision++; auditRevision++; api.logout() })
</script>

<style scoped src="../styles/admin.css"></style>
