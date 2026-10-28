import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import store from '../store'
import { actEncounter, getEncounter } from '../api/social'
import type { EncounterState, EncounterAction } from '../types/social'
import {encounterPollDelay} from '../utils/businessPolling'

export function useEncounter(initial?: EncounterState) {
  const room = ref<EncounterState>(initial || { state: 'IDLE', messages: [] })
  const error = ref(''), busy = ref(false)
  let timer: ReturnType<typeof setTimeout> | undefined
  let controller = new AbortController(), generation = 0, revision = 0, disposed = false
  let pollController:AbortController|undefined,polling=false,failures=0,resume=false
  const visible=()=>document.visibilityState!=='hidden'&&navigator.onLine!==false
  function schedule(delay=encounterPollDelay(room.value.state,failures)){
    clearTimeout(timer)
    if(!disposed&&visible())timer=setTimeout(poll,delay)
  }
  function visibility(){
    clearTimeout(timer)
    if(!visible()){pollController?.abort();return}
    failures=0
    if(polling){resume=true;return}
    void poll()
  }
  function merge(next: EncounterState) {
    const previous = next.roomId && next.roomId === room.value.roomId && next.reason !== 'BLOCKED' ? room.value.messages : []
    const messages = new Map(previous.map(message => [message.seq, message]))
    for (const message of next.messages || []) messages.set(message.seq, message)
    room.value = { ...next, messages: [...messages.values()].sort((a, b) => a.seq - b.seq) }
  }
  async function poll() {
    if(disposed||!visible()||!store.state.token)return
    if(polling)return
    if (busy.value) { schedule(500); return }
    polling=true;pollController=new AbortController()
    const current = generation
    const requestRevision = revision
    try {
      const last = room.value.messages.at(-1)?.seq || 0
      const next = await getEncounter(last, pollController.signal)
      if (!disposed && visible() && current === generation && requestRevision === revision) { merge(next); error.value = '';failures=0 }
    } catch (failure) { if (!disposed && !pollController.signal.aborted && current === generation && requestRevision === revision) {failures++;error.value = String((failure as Error).message)} }
    finally { polling=false;if (!disposed && (current === generation||resume)) {const delay=resume?0:undefined;resume=false;schedule(delay)} }
  }
  async function action(input: EncounterAction) {
    if (busy.value) return
    const current = generation
    revision++
    busy.value = true; error.value = ''
    try {
      const next = await actEncounter({ ...input, roomId: input.roomId ?? room.value.roomId }, controller.signal)
      if (!disposed && current === generation) merge(next)
      return current === generation ? next : undefined
    } catch (failure) {
      if (!disposed && current === generation) error.value = String((failure as Error).message)
      return undefined
    } finally { if (current === generation) {busy.value = false;if(!polling)schedule()} }
  }
  function reset() {
    generation++; controller.abort();pollController?.abort(); controller = new AbortController(); clearTimeout(timer);failures=0
    room.value = { state: 'IDLE', messages: [] }; error.value = ''; busy.value = false
    if (!disposed && store.state.token) {if(polling)resume=true;else void poll()}
  }
  watch(() => String(store.state.accountId), reset)
  onMounted(() => {document.addEventListener('visibilitychange',visibility);window.addEventListener('online',visibility);window.addEventListener('offline',visibility);if (initial) schedule(); else void poll() })
  onBeforeUnmount(() => { disposed = true; generation++; clearTimeout(timer); controller.abort();pollController?.abort();document.removeEventListener('visibilitychange',visibility);window.removeEventListener('online',visibility);window.removeEventListener('offline',visibility) })
  return { room, error, busy, action }
}
