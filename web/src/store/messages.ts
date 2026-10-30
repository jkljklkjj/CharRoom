import {historyWindow} from '../utils/historyWindow'
import type { Id, ChatMessage } from '../types/chat'
import {ConversationId, type ConversationKey} from '../utils/conversationId'
import { state, PAGE_SIZE } from './state'
import { AGENT_USER_ID, SELF_USER } from '../constants'
import { getPrivateConversationId, normalizeTimeValue } from './storage'
import { conversationPreviewText } from '../utils/format'
import DOMPurify from 'dompurify'
import { getAppliedCursors, getMessageCursor as dbGetMessageCursor, getMessagesPage as dbGetMessagesPage, appendMessage as dbAppendMessage,
         mergeHistoryMessages as dbMergeHistoryMessages, deleteMessage as dbDeleteMessage } from '../utils/messageDB'

import { updateConversationState, invalidateConversationPreview as invalidatePreview } from './users'
import { sessionEpoch } from '../services/sessionEpoch'
import { conversationViews } from '../services/conversationViews'
import type { MessageCursor } from '../utils/messageDB'

/**
 * 预清洗消息文本，避免每次渲染都调用 DOMPurify。
 * 在消息存入 store 时执行一次，而非每次 v-html 渲染时执行。
 */
function sanitizeText(text: string) {
  if (!text || typeof text !== 'string') return ''
  return DOMPurify.sanitize(text, { ALLOWED_TAGS: [] })
}

// ── seqId 持久化 ──────────────────────────────

let syncVersion=-1,syncTasks=0
export function beginHistorySync() {
  const session=sessionEpoch.capture()
  if(syncVersion!==session.version){syncVersion=session.version;syncTasks=0}
  if(!syncTasks)state.historySyncError=false
  syncTasks++;state.historySyncing=true
  let complete=false
  return (failed=false)=>{
    if(complete||!session.isCurrent())return
    complete=true
    if(failed)state.historySyncError=true
    syncTasks=Math.max(0,syncTasks-1);state.historySyncing=syncTasks>0
  }
}
export async function restoreAppliedCursors() {
  const account=String(state.accountId),session=sessionEpoch.capture()
  const cursors=await getAppliedCursors(account)
  if(!session.isCurrent()||String(state.accountId)!==account)return
  for(const [key,value] of Object.entries(cursors))setConversationSeqId(key,value)
}

export function getConversationSeqId(conversationId: Id) {
  const key = String(conversationId)
  return state.conversationSeqIds[key] || 0
}

export function setConversationSeqId(conversationId: Id, seqId: Id) {
  const key = String(conversationId)
  const current = state.conversationSeqIds[key] || 0
  if (BigInt(seqId) > BigInt(current)) {
    state.conversationSeqIds = {
      ...state.conversationSeqIds,
      [key]: seqId
    }
  }
}

// ── 消息持久化 ────────────────────────────────

async function savePrivateMessage(message: ChatMessage) {
  if (!state.accountId) return
  const chatId = getPrivateConversationId(message)
  if (!chatId) return
  await dbAppendMessage(state.accountId, chatId, message)
}

async function saveGroupMessage(message: ChatMessage) {
  if (!state.accountId || !message.groupId) return
  const conversationId = `group:${message.groupId}`
  await dbAppendMessage(state.accountId, conversationId, message)
}

// ── 会话加载 + 分页 ───────────────────────────

// Cursors belong to a view generation. Switching A→B→A invalidates both old requests.
const pageCursors = new Map<string, MessageCursor | null>()
let viewGeneration = 0
let loadingOlder: { generation: number; promise: Promise<boolean> } | null = null

function currentView() {
  const generation = viewGeneration, accountId = String(state.accountId), selected = state.selectedChatId
  const session = sessionEpoch.capture()
  return { accountId, selected, generation,
    isCurrent: () => session.isCurrent() && viewGeneration === generation && String(state.accountId) === accountId && state.selectedChatId === selected }
}

let memoryScope = ''
export function resetConversationViews() {
  conversationViews.clear();pageCursors.clear();memoryScope='';viewGeneration++
  state.historyLoading=false;state.historyError=false;state.historySyncing=false;state.historySyncError=false;state.historyHasNewer=false;state.historyFollowingLatest=true
}
function ensureMemoryScope() {
  const key=`${state.accountId}:${sessionEpoch.capture().version}`
  if(key!==memoryScope){conversationViews.clear();pageCursors.clear();memoryScope=key;return false}
  return true
}
function conversationKey(id:Id) {return ConversationId.parse(id).historyKey}
function mergeRows(older:readonly ChatMessage[],current:readonly ChatMessage[]) {
  // Rows that arrived with their client ID carry the authoritative mapping, so a placeholder
  // still stored under that ID collapses into the server row without any content matching.
  const canonical = new Map<string, ChatMessage>()
  for (const message of [...older,...current]) {
    if (message.clientMessageId && message.messageId && message.clientMessageId !== message.messageId) canonical.set(message.clientMessageId, message)
  }
  const rows=new Map<string,ChatMessage>()
  for(const message of [...older,...current]) {
    const server = message.messageId ? canonical.get(message.messageId) : undefined
    const row = server ? {...message, messageId: server.messageId, seqId: message.seqId ?? server.seqId, isSent: 'sent' as const} : message
    rows.set(row.messageId || `${row.time}:${row.user}:${row.text}`,row)
  }
  const sorted=[...rows.values()].sort((a,b)=>{
    const time=normalizeTimeValue(a.time ?? a.timestamp)-normalizeTimeValue(b.time ?? b.timestamp)
    if(time)return time
    const left=String(a.messageId||''),right=String(b.messageId||'')
    if(/^\d+$/.test(left)&&/^\d+$/.test(right))return BigInt(left)<BigInt(right)?-1:BigInt(left)>BigInt(right)?1:0
    return left<right?-1:left>right?1:0
  })
  return reconcileOptimistic(sorted)
}

/** Local optimistic rows are only a placeholder until the server copy arrives. */
function isOwnMessage(message: ChatMessage) {
  return message.user === SELF_USER || String(message.user) === String(state.accountId)
}
function peerIdentity(message: ChatMessage) {
  return message.groupId != null ? `group:${message.groupId}`
    : message.targetId != null ? `user:${message.targetId}` : ''
}
function sendText(message: ChatMessage) { return message.text || '' }
function sendTime(message: ChatMessage) { return normalizeTimeValue(message.time ?? message.timestamp) }
/**
 * A local row is only a placeholder until its server copy exists. Every own row
 * without the `local_` prefix is the canonical copy, whatever its send status:
 * an ACK that never renamed its placeholder still leaves exactly one bubble.
 * Matching is nearest-in-time within a generous window because a group history
 * row carries the server clock instead of the client timestamp, and consume-once
 * keeps repeated sends of the same text on their own placeholders.
 */
const RECONCILE_WINDOW_MS = 5 * 60 * 1000
function reconcileOptimistic(rows: ChatMessage[]): ChatMessage[] {
  const server = new Map<string, number[]>()
  for (const row of rows) {
    if (!row.messageId || row.messageId.startsWith('local_') || !isOwnMessage(row)) continue
    const key = `${peerIdentity(row)}|${sendText(row)}`
    const times = server.get(key)
    if (times) times.push(sendTime(row)); else server.set(key, [sendTime(row)])
  }
  if (!server.size) return rows
  return rows.filter(row => {
    if (!row.messageId?.startsWith('local_') || !isOwnMessage(row)) return true
    const times = server.get(`${peerIdentity(row)}|${sendText(row)}`)
    if (!times?.length) return true
    const at = sendTime(row)
    let nearest = -1, distance = RECONCILE_WINDOW_MS
    for (let index = 0; index < times.length; index++) {
      const delta = Math.abs(times[index] - at)
      if (delta <= distance) { distance = delta; nearest = index }
    }
    if (nearest < 0) return true
    times.splice(nearest, 1)
    return false
  })
}
export function setHistoryFollowingLatest(value:boolean){state.historyFollowingLatest=value}
async function installWindow(rows:ChatMessage[],edge:'oldest'|'latest',refreshCursor=true){
  const view=currentView(),group=ConversationId.parse(view.selected!).isGroup
  const previous=group?state.groupMessages:state.messages
  const bounded=historyWindow(rows,edge)
  state.historyHasNewer ||= bounded.trimmedAfter
  if(bounded.trimmedBefore){if(group)state.hasMoreGroupMessages=true;else state.hasMoreMessages=true}
  if(group)state.groupMessages=bounded.messages;else state.messages=bounded.messages
  const first=bounded.messages[0]?.messageId
  if(refreshCursor&&first&&first!==previous[0]?.messageId){
    const cursor=await dbGetMessageCursor(view.accountId,ConversationId.parse(view.selected!).historyKey,first)
    const current=group?state.groupMessages:state.messages
    if(view.isCurrent()&&current[0]?.messageId===first&&cursor)pageCursors.set(ConversationId.parse(view.selected!).historyKey,cursor)
  }
}
export async function loadLatestMessages(){
  if(!state.selectedChatId)return
  viewGeneration++
  const view=currentView(),target=ConversationId.parse(view.selected!)
  state.historyLoading=true
  try{
    const page=await dbGetMessagesPage(view.accountId,target.historyKey,null,PAGE_SIZE)
    if(!view.isCurrent())return
    state.historyHasNewer=false;state.historyFollowingLatest=true
    if(target.isGroup)state.hasMoreGroupMessages=page.hasMore
    else state.hasMoreMessages=page.hasMore
    pageCursors.set(target.historyKey,page.beforeIndex)
    await installWindow(reconcileOptimistic(page.messages),'latest')
  }finally{if(view.isCurrent())state.historyLoading=false}
}
export async function loadConversation(id: ConversationKey | null) {
  const isGroup=id!=null && ConversationId.parse(id).isGroup
  const sameScope=ensureMemoryScope()
  if(sameScope && state.selectedChatId!=null) {
    const previous=conversationKey(state.selectedChatId),group=ConversationId.parse(state.selectedChatId).isGroup
    if(!state.historyLoading)conversationViews.put(previous,{messages:[...(group?state.groupMessages:state.messages)],
      before:pageCursors.get(previous)??null,hasMore:group?state.hasMoreGroupMessages:state.hasMoreMessages,hasNewer:state.historyHasNewer})
  }
  viewGeneration++
  pageCursors.clear()
  state.selectedChatId=id;state.historyError=false
  const conversationId=id==null?'':conversationKey(id)
  const cached=conversationViews.get(conversationId)
  state.messages=!isGroup&&cached?[...cached.messages]:[]
  state.groupMessages=isGroup&&cached?[...cached.messages]:[]
  state.hasMoreMessages=!isGroup&&cached?cached.hasMore:true
  state.hasMoreGroupMessages=isGroup&&cached?cached.hasMore:true
  state.historyLoading=false;state.historyHasNewer=cached?.hasNewer||false;state.historyFollowingLatest=true
  if(id==null||!state.accountId)return
  if(cached){pageCursors.set(conversationId,cached.before);return}
  const view=currentView()
  state.historyLoading=true
  try {
    const page=await dbGetMessagesPage(view.accountId,conversationId,null,PAGE_SIZE)
    if(!view.isCurrent())return
    if(isGroup)state.hasMoreGroupMessages=page.hasMore
    else state.hasMoreMessages=page.hasMore
    pageCursors.set(conversationId,page.beforeIndex)
    await installWindow(mergeRows(page.messages,isGroup?state.groupMessages:state.messages),'latest')
  } catch(error) {
    if(view.isCurrent())state.historyError=true
    throw error
  } finally {if(view.isCurrent())state.historyLoading=false}
}

export function loadOlderMessages(): Promise<boolean> {
  if (!state.selectedChatId || !state.accountId) return Promise.resolve(false)
  if (loadingOlder?.generation === viewGeneration) return loadingOlder.promise
  const view = currentView(), isGroup = ConversationId.parse(view.selected!).isGroup
  const conversationId = ConversationId.parse(view.selected!).historyKey
  const before = pageCursors.get(conversationId)
  if (!(isGroup ? state.hasMoreGroupMessages : state.hasMoreMessages) || !before) return Promise.resolve(false)
  const flight = { generation: view.generation, promise: Promise.resolve(false) }
  flight.promise = dbGetMessagesPage(view.accountId, conversationId, before, PAGE_SIZE).then(page => {
    if (!view.isCurrent()) return false
    const current = isGroup ? state.groupMessages : state.messages
    const ids = new Set(current.map(m => m.messageId).filter(Boolean))
    const merged = reconcileOptimistic([...page.messages.filter(m => !m.messageId || !ids.has(m.messageId)), ...current])
    if (isGroup) state.hasMoreGroupMessages = page.hasMore
    else state.hasMoreMessages = page.hasMore
    const bounded=historyWindow(merged,'oldest');state.historyHasNewer ||= bounded.trimmedAfter
    if(isGroup)state.groupMessages=bounded.messages;else state.messages=bounded.messages
    pageCursors.set(conversationId, page.beforeIndex)
    return page.messages.length > 0
  }).finally(() => { if (loadingOlder === flight) loadingOlder = null })
  loadingOlder = flight
  return flight.promise
}

// ── 消息操作 ──────────────────────────────────

// 最近处理过的 messageId（防重复入列：后端未收到 ACK 会 30s×3 重发，
// 重连补拉/增量同步也可能与实时推送重复）
const seenMessageIds = new Set<string>()
let seenVersion = -1
function seenScope() {
  const version = sessionEpoch.capture().version
  if (version !== seenVersion) { seenVersion = version; seenMessageIds.clear() }
}
function rememberSeen(messageId?: string | null) {
  if (!messageId) return
  seenScope()
  seenMessageIds.add(messageId)
  if (seenMessageIds.size > 500) {
    const oldest = seenMessageIds.values().next().value
    if (oldest !== undefined) seenMessageIds.delete(oldest)
  }
}
function alreadySeen(messageId?: string | null): boolean {
  if (!messageId) return false
  seenScope()
  if (seenMessageIds.has(messageId)) return true
  rememberSeen(messageId)
  return false
}

export async function addMessage(m: ChatMessage) {
  const session = sessionEpoch.capture()
  const chatId = getPrivateConversationId(m)
  if (!chatId) return
  // 预清洗文本，避免每次渲染调用 DOMPurify
  const sanitized = m.text != null ? { ...m, text: sanitizeText(m.text) } : m
  await savePrivateMessage(sanitized)
  if (!session.isCurrent() || alreadySeen(m.messageId)) return
  if (state.selectedChatId === ConversationId.privateChat(chatId).key) {
    if (!m.messageId || !state.messages.some(row => row.messageId === m.messageId)) state.messages.push(sanitized)
    state.messages = reconcileOptimistic(state.messages)
    await installWindow(state.messages,state.historyFollowingLatest?'latest':'oldest')
  }
  if(!session.isCurrent())return
  if(state.selectedChatId!==ConversationId.privateChat(chatId).key)conversationViews.update(String(chatId),rows=>mergeRows([sanitized],rows))
  if (String(m.user) !== 'you') {
    updateConversationState(ConversationId.privateChat(chatId).key, {
      lastIncomingMessageTime: normalizeTimeValue(m.time || m.timestamp),
      lastMessagePreview: conversationPreviewText(sanitized),
      unreadDelta: state.selectedChatId === ConversationId.privateChat(chatId).key ? 0 : 1
    })
    // 清除预览缓存，下次 rebuild 时重新读取
    invalidatePreview(ConversationId.privateChat(chatId).key)
  } else {
    // 自己发出的消息同样更新联系人列表第二行的预览（不改变 incoming 排序时间）
    updateConversationState(ConversationId.privateChat(chatId).key, { lastMessagePreview: conversationPreviewText(sanitized) })
  }
}

/** Bootstrap pages do not increase unread counts or advance incremental seq cursors. */
export async function restoreHistoryPage(accountId: string, messages: ChatMessage[], applied?:import('../utils/messageDB').AppliedCursor) {
  const session = sessionEpoch.capture()
  const isCurrent = () => session.isCurrent() && String(state.accountId) === accountId
  if (!isCurrent()) return
  if (messages.some(message => !message.messageId)) throw new Error('History message is missing its stable ID')
  const conversations = new Map<string, ChatMessage[]>()
  for (const message of messages) {
    const key = message.groupId ? `group:${message.groupId}` : getPrivateConversationId(message)
    if (!key) continue
    const batch = conversations.get(String(key)) || []
    batch.push({ ...message, text: sanitizeText(message.text || '') })
    conversations.set(String(key), batch)
  }
  if(applied && conversations.size!==1)throw Error('An applied cursor must belong to one conversation')
  for (const [conversationId, batch] of conversations) {
    if (!isCurrent()) return
    await dbMergeHistoryMessages(accountId, conversationId, batch,applied)
    if (!isCurrent()) return
    batch.forEach(message => alreadySeen(message.messageId))
    const isGroup = conversationId.startsWith('group:')
    const uiId = ConversationId.fromHistoryKey(conversationId).key
    invalidatePreview(uiId)
    if (String(state.selectedChatId) !== uiId) {conversationViews.update(conversationId,rows=>mergeRows(batch,rows));continue}
    const view = currentView()
    const current=isGroup?state.groupMessages:state.messages
    // The batch is already committed with its applied cursor. Merge only those rows into the window.
    if(view.isCurrent())await installWindow(mergeRows(batch,current),state.historyFollowingLatest?'latest':'oldest')
  }
}

export async function upsertAgentStreamMessage(messageId: string, fullContent: string, done = false) {
  if (!messageId) return
  if(state.selectedChatId!==ConversationId.privateChat(AGENT_USER_ID).key) {
    const cached=conversationViews.get(String(AGENT_USER_ID)),existing=cached?.messages.find(row=>row.messageId===messageId)
    const message={...existing,user:String(AGENT_USER_ID),targetId:String(state.accountId),text:sanitizeText(fullContent),messageId,done,time:existing?.time||new Date().toISOString()}
    if(cached)conversationViews.update(String(AGENT_USER_ID),rows=>mergeRows(rows,[message]))
    if(done)await savePrivateMessage(message)
    return
  }
  const existingIndex = state.messages.findIndex(m => m.messageId === messageId)
  if (existingIndex >= 0) {
    state.messages[existingIndex] = {
      ...state.messages[existingIndex],
      text: sanitizeText(fullContent),
      done
    }
    if (done) await savePrivateMessage(state.messages[existingIndex])
  } else {
    const agentMessage = {
      user: String(AGENT_USER_ID),
      text: fullContent,
      time: new Date().toISOString(),
      targetId: String(state.accountId),
      messageId,
      done
    }
    state.messages.push(agentMessage)
    await savePrivateMessage(agentMessage)
  }
  if(done)await installWindow(state.messages,state.historyFollowingLatest?'latest':'oldest')
}

export async function addGroupMessage(m: ChatMessage) {
  const session = sessionEpoch.capture()
  const conversationId = ConversationId.group(m.groupId!).key
  // 预清洗文本，避免每次渲染调用 DOMPurify
  const sanitized = m.text != null ? { ...m, text: sanitizeText(m.text) } : m
  await saveGroupMessage(sanitized)
  if (!session.isCurrent() || alreadySeen(m.messageId)) return
  if (state.selectedChatId === conversationId) {
    if (!m.messageId || !state.groupMessages.some(row => row.messageId === m.messageId)) state.groupMessages.push(sanitized)
    state.groupMessages = reconcileOptimistic(state.groupMessages)
    await installWindow(state.groupMessages,state.historyFollowingLatest?'latest':'oldest')
  }
  if(!session.isCurrent())return
  if(state.selectedChatId!==conversationId)conversationViews.update(`group:${m.groupId}`,rows=>mergeRows([sanitized],rows))
  if (String(m.user) !== String(state.accountId)) {
    updateConversationState(conversationId, {
      lastIncomingMessageTime: normalizeTimeValue(m.time || m.timestamp),
      lastMessagePreview: conversationPreviewText(sanitized),
      unreadDelta: state.selectedChatId === conversationId ? 0 : 1
    })
    // 清除预览缓存，下次 rebuild 时重新读取
    invalidatePreview(conversationId)
  } else {
    updateConversationState(conversationId, { lastMessagePreview: conversationPreviewText(sanitized) })
  }
}

export function confirmMessageId(clientId: string, serverId: string, seqId: Id) {
  // A late echo/history copy with the server ID must not create a second row after the local ID is renamed.
  rememberSeen(serverId)
  conversationViews.updateAll(rows=>rows.some(row=>row.messageId===clientId)?mergeRows([],rows.map(row=>row.messageId===clientId?{...row,messageId:serverId,seqId,isSent:'sent'}:row)):rows)
  for (const rows of [state.messages, state.groupMessages]) {
    const position = rows.findIndex(row => row.messageId === clientId)
    if (position < 0) continue
    const updated = { ...rows[position], messageId: serverId, seqId, isSent: 'sent' as const }
    rows.splice(position, 1)
    const duplicate = rows.findIndex(row => row.messageId === serverId)
    if (duplicate >= 0) rows[duplicate] = { ...rows[duplicate], ...updated }
    else rows.splice(position, 0, updated)
  }
}

export function updateMessageStatus(messageId: string, status: import('../types/chat').MessageStatus, sendStartedAt?:number) {
  const change=(message:ChatMessage):ChatMessage=>({...message,isSent:status,...(sendStartedAt===undefined?{}:{sendStartedAt})})
  conversationViews.updateAll(rows=>rows.map(row=>row.messageId===messageId?change(row):row))
  for (let i = 0; i < state.messages.length; i++) {
    if (state.messages[i].messageId === messageId) {
      state.messages[i] = change(state.messages[i])
      return
    }
  }
  for (let i = 0; i < state.groupMessages.length; i++) {
    if (state.groupMessages[i].messageId === messageId) {
      state.groupMessages[i] = change(state.groupMessages[i])
      return
    }
  }
}

export function setSelectedChat(id: ConversationKey | null) {
  void loadConversation(id).catch(error=>console.debug('Local history load failed',error))
}

export function trimMessages(max: number) {
  if (state.messages.length > max) {
    state.messages = state.messages.slice(state.messages.length - max)
  }
}

export function trimGroupMessages(max: number) {
  if (state.groupMessages.length > max) {
    state.groupMessages = state.groupMessages.slice(state.groupMessages.length - max)
  }
}

export function deleteMessage(message: ChatMessage, isGroup = false) {
  if(message.messageId)conversationViews.updateAll(rows=>rows.filter(row=>row.messageId!==message.messageId))
  const byId = message && message.messageId
    ? (m: ChatMessage) => m.messageId !== message.messageId
    : (m: ChatMessage) => m !== message
  if (isGroup) {
    state.groupMessages = state.groupMessages.filter(byId)
  } else {
    state.messages = state.messages.filter(byId)
  }
  // 同步删除 IndexedDB 中的持久化消息，避免刷新后复活
  if (state.accountId && message && message.messageId) {
    const chatId = isGroup
      ? `group:${message.groupId}`
      : getPrivateConversationId(message)
    if (chatId) {
      dbDeleteMessage(state.accountId, chatId, message.messageId).catch(e =>
        console.warn('[MessageDB] 删除消息失败:', e)
      )
    }
  }
}
