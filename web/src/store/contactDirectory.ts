import {state} from './state'
import {applyDirectorySnapshot,hydrateDirectoryPreviews} from './users'
import {sessionEpoch} from '../services/sessionEpoch'
import {ContactDirectorySync, type DirectoryReply} from '../services/contactDirectorySync'
import {loadDirectory,saveDirectory} from '../utils/contactDirectoryDB'
import {safeFetch,API_BASE} from '../api/http'
import {socialViewCache} from '../services/socialViewCache'

const directory=new ContactDirectorySync({
  capture:()=>{
    const session=sessionEpoch.capture(),accountId=String(state.accountId)
    return {accountId,key:`${accountId}:${session.version}`,isCurrent:()=>session.isCurrent()&&state.loginValid&&String(state.accountId)===accountId}
  },
  load:async account=>{const snapshot=await loadDirectory(account);if(snapshot)await hydrateDirectoryPreviews(account);return snapshot},save:saveDirectory,now:Date.now,
  fetch:async version=>{
    const result=await safeFetch<DirectoryReply>(`${API_BASE}/contacts/snapshot?version=${encodeURIComponent(version)}`)
    if(!result.ok||result.body?.code!==0||!result.body.data)throw Error(result.body?.message||'Contact sync failed')
    return result.body.data
  },
  apply:applyDirectorySnapshot,
  status:value=>{state.directoryPhase=value.phase}
})
export function refreshContacts(force=false) {return directory.refresh(force).catch(error=>{console.debug('Directory refresh failed',error)})}
export function invalidateContacts() {socialViewCache.invalidate();directory.invalidate();void refreshContacts(true)}
