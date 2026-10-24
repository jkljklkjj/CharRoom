<template>
  <div class="copilot">
    <header class="cp-header">
      <div class="cp-title">
        <span class="cp-logo">🎧</span>
        <span>客服 AI 副驾</span>
        <span v-if="me" class="cp-org">{{ me.role }} · org#{{ me.orgId }}</span>
      </div>
      <div class="cp-metrics" v-if="metrics">
        <span class="metric"><b>{{ metrics.queued }}</b> 待接入</span>
        <span class="metric"><b>{{ metrics.active }}</b> 进行中</span>
        <span class="metric warn"><b>{{ metrics.escalated }}</b> 升级预警</span>
        <span class="metric"><b>{{ (metrics.adoptionRate * 100).toFixed(0) }}%</b> 起草采纳率</span>
      </div>
        <div class="cp-actions">
        <button v-if="!me" class="primary" @click="bootstrap">开通租户</button>
        <template v-else>
          <input v-model="newCustomerId" class="cp-newconv" placeholder="客户ID" @keyup.enter="createConversation" />
          <button @click="createConversation" :disabled="!newCustomerId">新建会话</button>
          <button @click="openChannels">渠道</button>
          <button @click="reload">刷新</button>
        </template>
      </div>
    </header>

    <p v-if="error" class="cp-error">{{ error }}</p>

    <div v-if="me" class="cp-body">
      <aside class="cp-list">
        <div class="cp-list-head">会话队列</div>
        <button
          v-for="c in conversations"
          :key="c.id"
          class="cp-item"
          :class="{ active: c.id === activeId, danger: c.escalationFlag === 1 }"
          @click="open(c.id)"
        >
          <div class="row">
            <span class="cust">{{ c.customerId }}</span>
            <span v-if="c.unread > 0" class="badge">{{ c.unread }}</span>
          </div>
          <div class="preview">{{ c.lastMessagePreview || '（暂无消息）' }}</div>
          <div class="row sub">
            <span :class="['tag', c.status]">{{ statusText(c.status) }}</span>
            <span v-if="c.assigneeId" class="seat">→ {{ c.assigneeId }}</span>
            <span v-if="c.escalationFlag === 1" class="alert">⚠ 情绪 {{ c.emotionScore }}</span>
          </div>
        </button>
        <div v-if="!conversations.length" class="empty">暂无会话</div>
      </aside>

      <main class="cp-detail">
        <div v-if="!detail" class="empty big">选择左侧会话开始接待</div>
        <template v-else>
          <div class="cp-detail-head">
            <span>客户：{{ detail.conversation.customerId }}</span>
            <span :class="['tag', detail.conversation.status]">{{ statusText(detail.conversation.status) }}</span>
            <button v-if="!detail.conversation.assigneeId" class="primary" @click="claim">接入</button>
            <button v-else-if="detail.conversation.status !== 'closed'" @click="closeConv">关闭</button>
          </div>

          <div class="cp-cols">
            <section class="cp-chat">
              <div class="cp-messages">
                <div
                  v-for="m in detail.messages"
                  :key="m.id"
                  :class="['msg', m.senderType]"
                >
                  <div class="bubble">{{ m.content }}</div>
                  <div class="meta">{{ m.senderType === 'customer' ? '客户' : '坐席' }}</div>
                </div>
                <div v-if="!detail.messages.length" class="empty">暂无消息</div>
              </div>

              <div class="cp-compose">
                <textarea v-model="draft" rows="3" placeholder="回复内容（可点击右侧建议填入）" />
                <div class="compose-actions">
                  <input v-model="customerInput" placeholder="模拟客户来消息…" @keyup.enter="simulate" />
                  <button @click="simulate" :disabled="!customerInput">模拟客户</button>
                  <button class="primary" @click="send" :disabled="!draft || !canSend">发送</button>
                </div>
              </div>
            </section>

            <aside class="cp-suggest">
              <div class="cp-list-head">
                副驾建议
                <span class="hint">{{ suggestions.length }}</span>
              </div>
              <div
                v-for="s in suggestions"
                :key="s.id"
                :class="['sug', s.kind]"
              >
                <div class="sug-head">
                  <span class="kind">{{ kindText(s.kind) }}</span>
                  <span v-if="s.source" class="src">{{ s.source }}</span>
                  <span class="score" v-if="s.score">{{ s.score }}</span>
                </div>
                <div class="sug-content">{{ s.content }}</div>
                <div class="sug-actions">
                  <button @click="useSuggestion(s)">采纳</button>
                  <button @click="dismiss(s)">忽略</button>
                </div>
              </div>
              <div v-if="!suggestions.length" class="empty">暂无建议</div>
            </aside>
          </div>

          <section class="cp-kb">
            <div class="cp-list-head">话术库</div>
            <div class="kb-row" v-for="k in knowledge" :key="k.id">
              <span class="kb-title">{{ k.title }}</span>
              <span class="kb-content">{{ k.content }}</span>
              <button @click="removeKnowledge(k.id)">删除</button>
            </div>
            <div class="kb-add">
              <input v-model="kbTitle" placeholder="话术标题" />
              <input v-model="kbContent" placeholder="话术内容" />
              <input v-model="kbTags" placeholder="标签（可选）" />
              <button @click="addKnowledge" :disabled="!kbTitle || !kbContent">新增</button>
            </div>
          </section>
        </template>
      </main>
    </div>

    <div v-else class="empty big">
      尚未开通副驾租户。点击右上角「开通租户」即可开始（首个用户为管理员）。
    </div>

    <!-- 渠道管理 -->
    <div v-if="showChannels" class="ch-overlay" @click.self="showChannels = false">
      <div class="ch-modal">
        <div class="ch-head">
          <span>客服渠道</span>
          <button @click="showChannels = false">✕</button>
        </div>
        <div class="ch-create">
          <input v-model="chForm.name" placeholder="渠道名称（如 官网客服）" />
          <select v-model="chForm.type">
            <option value="h5">H5 网页组件</option>
            <option value="wecom">企业微信</option>
          </select>
          <input v-model="chForm.welcomeMessage" placeholder="开场白（可选）" />
          <label class="ch-check">
            <input type="checkbox" v-model="chForm.autoReply" />
            AI 自动回复
          </label>
          <textarea
            v-if="chForm.type === 'wecom'"
            v-model="chForm.settings"
            rows="3"
            placeholder='企业微信配置 JSON：{"corpId":"","token":"","encodingAesKey":"","secret":"","agentId":""}'
          />
          <button class="primary" @click="createChannel" :disabled="!chForm.name">创建渠道</button>
        </div>
        <div class="ch-list">
          <div v-for="c in channels" :key="c.id" class="ch-item">
            <div class="ch-row">
              <b>{{ c.name }}</b>
              <span class="tag">{{ c.type }}</span>
              <span class="ch-key">{{ c.channelKey }}</span>
              <span :class="['tag', c.enabled === 1 ? 'active' : 'closed']">{{ c.enabled === 1 ? '启用' : '停用' }}</span>
              <span v-if="c.autoReply === 1" class="tag active">自动回复</span>
              <button @click="toggleChannel(c)">{{ c.enabled === 1 ? '停用' : '启用' }}</button>
              <button @click="removeChannel(c.id)">删除</button>
            </div>
            <div v-if="c.type === 'h5'" class="ch-snippet">
              <code>{{ embedSnippet(c) }}</code>
              <button @click="copy(embedSnippet(c))">复制嵌入代码</button>
            </div>
            <div v-else class="ch-snippet">
              <code>{{ wecomCallbackUrl(c) }}</code>
              <button @click="copy(wecomCallbackUrl(c))">复制回调地址</button>
            </div>
          </div>
          <div v-if="!channels.length" class="empty">暂无渠道，先创建一个</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 坐席工作台：数据均为后端动态 JSON，此处不做逐字段建模
import { ref, computed, onMounted, onUnmounted } from 'vue'
import api from '../api'

const me = ref<any>(null)
const metrics = ref<any>(null)
const conversations = ref<any[]>([])
const activeId = ref<any>(null)
const detail = ref<any>(null)
const suggestions = ref<any[]>([])
const knowledge = ref<any[]>([])
const draft = ref('')
const customerInput = ref('')
const error = ref('')
const activeSuggestionId = ref<any>(null)
const kbTitle = ref('')
const kbContent = ref('')
const kbTags = ref('')
const newCustomerId = ref('')
const showChannels = ref(false)
const channels = ref<any[]>([])
const chForm = ref({ name: '', type: 'h5', welcomeMessage: '', autoReply: false, settings: '' })

function openChannels() {
  showChannels.value = true
  loadChannels()
}
async function loadChannels() {
  channels.value = ((await api.listCopilotChannels()) as any[]) || []
}
async function createChannel() {
  const f = chForm.value
  const created = await api.createCopilotChannel(
    f.name,
    f.type,
    f.welcomeMessage,
    f.autoReply,
    f.type === 'wecom' ? f.settings : null
  )
  if (!created) {
    error.value = '创建渠道失败（需要管理员权限）'
    return
  }
  chForm.value = { name: '', type: 'h5', welcomeMessage: '', autoReply: false, settings: '' }
  await loadChannels()
}
async function toggleChannel(c: any) {
  await api.updateCopilotChannel(c.id, { name: c.name, enabled: c.enabled !== 1 })
  await loadChannels()
}
async function removeChannel(id: string | number) {
  await api.deleteCopilotChannel(id)
  await loadChannels()
}
function embedSnippet(c: any) {
  const base = window.location.origin
  return `<script src="${base}/widget.js" data-channel-key="${c.channelKey}" data-api-base="${base}/api"><\/script>`
}
function wecomCallbackUrl(c: any) {
  return `${window.location.origin}/api/public/copilot/wecom/callback/${c.channelKey}`
}
function copy(text: string) {
  if (navigator.clipboard) navigator.clipboard.writeText(text)
}

const canSend = computed(() =>
  detail.value && (detail.value.conversation.assigneeId || me.value?.role === 'owner' || me.value?.role === 'admin')
)

function statusText(s: string) {
  return ({ queued: '待接入', active: '进行中', closed: '已关闭' } as Record<string, string>)[s] || s
}
function kindText(k: string) {
  return ({ intent: '意图', playbook: '话术', draft: '起草', emotion: '情绪预警', summary: '摘要' } as Record<string, string>)[k] || k
}

async function bootstrap() {
  error.value = ''
  const org = await api.bootstrapOrg('我的企业')
  if (!org) {
    error.value = '开通失败，请确认已登录'
    return
  }
  await loadMe()
}

async function loadMe() {
  me.value = await api.getCopilotMe()
  if (me.value) {
    await Promise.all([loadConversations(), loadMetrics(), loadKnowledge()])
  }
}

async function reload() {
  await Promise.all([loadConversations(), loadMetrics()])
  if (activeId.value) await open(activeId.value)
}

async function loadConversations() {
  conversations.value = ((await api.listCopilotConversations()) as any[]) || []
}

async function createConversation() {
  const customerId = newCustomerId.value
  if (!customerId) return
  const conv = (await api.openCopilotConversation(Number(customerId))) as any
  newCustomerId.value = ''
  if (!conv) {
    error.value = '新建会话失败'
    return
  }
  await loadConversations()
  await open(conv.id)
}
async function loadMetrics() {
  metrics.value = await api.getCopilotMetrics()
}
async function loadKnowledge() {
  knowledge.value = ((await api.listCopilotKnowledge()) as any[]) || []
}

async function open(id: string | number) {
  activeId.value = id
  draft.value = ''
  activeSuggestionId.value = null
  detail.value = await api.getCopilotConversation(id)
  suggestions.value = ((await api.listCopilotSuggestions(id)) as any[]) || []
  startAutoRefresh()
}

// LLM 草稿是异步升级的：轮询刷新，让模板草稿自动变成模型草稿
let refreshTimer: ReturnType<typeof setInterval> | null = null
function startAutoRefresh() {
  stopAutoRefresh()
  refreshTimer = setInterval(refreshOpen, 4000)
}
function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}
async function refreshOpen() {
  if (!activeId.value || draft.value) return
  const freshDetail = (await api.getCopilotConversation(activeId.value)) as any
  const freshSuggestions = (await api.listCopilotSuggestions(activeId.value)) as any[]
  if (activeId.value && freshDetail) detail.value = freshDetail
  if (activeId.value && freshSuggestions) suggestions.value = freshSuggestions
}

async function claim() {
  await api.claimCopilotConversation(activeId.value)
  await reload()
}
async function closeConv() {
  await api.closeCopilotConversation(activeId.value)
  await reload()
}

async function simulate() {
  const content = customerInput.value
  if (!content) return
  customerInput.value = ''
  await api.sendCopilotCustomerMessage(activeId.value, content)
  await open(activeId.value)
  await loadConversations()
}

function useSuggestion(s: any) {
  draft.value = s.content
  activeSuggestionId.value = s.id
  api.copilotFeedback(s.id, true)
  s.status = 'adopted'
}

function dismiss(s: any) {
  api.copilotFeedback(s.id, false)
  s.status = 'dismissed'
}

async function send() {
  const content = draft.value
  if (!content) return
  const msg = await api.sendCopilotMessage(activeId.value, content, activeSuggestionId.value)
  if (!msg) {
    error.value = '发送失败（可能是会话未分配给你或内容被审核拦截）'
    return
  }
  error.value = ''
  draft.value = ''
  activeSuggestionId.value = null
  await open(activeId.value)
  await loadConversations()
}

async function addKnowledge() {
  await api.createCopilotKnowledge(kbTitle.value, kbContent.value, kbTags.value as unknown as string[])
  kbTitle.value = ''
  kbContent.value = ''
  kbTags.value = ''
  await loadKnowledge()
}
async function removeKnowledge(id: string | number) {
  await api.deleteCopilotKnowledge(id)
  await loadKnowledge()
}

onMounted(loadMe)
onUnmounted(stopAutoRefresh)
</script>

<style scoped>
.copilot { display: flex; flex-direction: column; height: 100vh; font-size: 14px; background: #f5f6f8; }
.cp-header { display: flex; align-items: center; gap: 16px; padding: 10px 16px; background: #1f2937; color: #fff; }
.cp-title { display: flex; align-items: center; gap: 8px; font-weight: 600; }
.cp-logo { font-size: 18px; }
.cp-org { font-weight: 400; opacity: .7; font-size: 12px; }
.cp-newconv { border: 1px solid #4b5563; background: #111827; color: #fff; border-radius: 6px; padding: 4px 8px; width: 120px; }
.ch-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.45); z-index: 1000; display: flex; align-items: center; justify-content: center; }
.ch-modal { width: 680px; max-height: 80vh; overflow: auto; background: #fff; border-radius: 12px; padding: 16px; }
.ch-head { display: flex; justify-content: space-between; align-items: center; font-weight: 600; margin-bottom: 12px; }
.ch-create { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; padding-bottom: 12px; border-bottom: 1px solid #f0f0f0; }
.ch-create input, .ch-create select, .ch-create textarea { border: 1px solid #d1d5db; border-radius: 6px; padding: 6px 8px; font-size: 13px; }
.ch-create textarea { width: 100%; }
.ch-check { display: flex; align-items: center; gap: 4px; font-size: 13px; color: #374151; }
.ch-list { margin-top: 12px; }
.ch-item { border-bottom: 1px solid #f3f4f6; padding: 8px 0; }
.ch-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.ch-key { font-family: monospace; color: #6b7280; font-size: 12px; }
.ch-snippet { display: flex; align-items: center; gap: 8px; margin-top: 6px; }
.ch-snippet code { flex: 1; background: #111827; color: #e5e7eb; padding: 6px 8px; border-radius: 6px; font-size: 12px; overflow-x: auto; white-space: nowrap; }
.cp-metrics { display: flex; gap: 14px; margin-left: auto; font-size: 12px; opacity: .9; }
.cp-metrics .warn b { color: #fca5a5; }
.cp-actions button, .cp-detail button, .cp-compose button, .kb-add button, .cp-kb button {
  border: 1px solid #d1d5db; background: #fff; border-radius: 6px; padding: 4px 10px; cursor: pointer;
}
.cp-actions button.primary, .cp-compose button.primary, .cp-detail-head button.primary { background: #2563eb; border-color: #2563eb; color: #fff; }
button:disabled { opacity: .5; cursor: not-allowed; }
.cp-error { margin: 0; padding: 8px 16px; background: #fee2e2; color: #b91c1c; }
.cp-body { flex: 1; display: flex; min-height: 0; }
.cp-list { width: 260px; border-right: 1px solid #e5e7eb; background: #fff; overflow-y: auto; }
.cp-list-head { padding: 10px 12px; font-weight: 600; color: #374151; border-bottom: 1px solid #f0f0f0; display: flex; justify-content: space-between; }
.cp-item { display: block; width: 100%; text-align: left; padding: 10px 12px; border: 0; border-bottom: 1px solid #f3f4f6; background: transparent; cursor: pointer; }
.cp-item.active { background: #eff6ff; }
.cp-item.danger { border-left: 3px solid #ef4444; }
.cp-item .row { display: flex; justify-content: space-between; align-items: center; }
.cust { font-weight: 600; }
.preview { color: #6b7280; font-size: 12px; margin: 4px 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sub { font-size: 11px; color: #9ca3af; gap: 6px; }
.tag { padding: 1px 6px; border-radius: 4px; background: #e5e7eb; }
.tag.active { background: #dbeafe; color: #1d4ed8; }
.tag.closed { background: #f3f4f6; color: #9ca3af; }
.badge { background: #ef4444; color: #fff; border-radius: 10px; padding: 0 6px; font-size: 11px; }
.alert { color: #dc2626; }
.cp-detail { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.cp-detail-head { display: flex; align-items: center; gap: 10px; padding: 10px 14px; background: #fff; border-bottom: 1px solid #e5e7eb; }
.cp-cols { flex: 1; display: flex; min-height: 0; }
.cp-chat { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.cp-messages { flex: 1; overflow-y: auto; padding: 14px; }
.msg { display: flex; flex-direction: column; margin-bottom: 10px; }
.msg.customer { align-items: flex-start; }
.msg.seat { align-items: flex-end; }
.bubble { max-width: 70%; padding: 8px 12px; border-radius: 10px; background: #fff; }
.msg.customer .bubble { background: #fff; }
.msg.seat .bubble { background: #dbeafe; }
.msg .meta { font-size: 11px; color: #9ca3af; margin-top: 2px; }
.cp-compose { border-top: 1px solid #e5e7eb; padding: 10px; background: #fff; }
.cp-compose textarea { width: 100%; border: 1px solid #d1d5db; border-radius: 6px; padding: 8px; resize: vertical; box-sizing: border-box; }
.compose-actions { display: flex; gap: 8px; margin-top: 8px; }
.compose-actions input { flex: 1; border: 1px solid #d1d5db; border-radius: 6px; padding: 6px 8px; }
.cp-suggest { width: 320px; border-left: 1px solid #e5e7eb; background: #fff; overflow-y: auto; }
.sug { border-bottom: 1px solid #f3f4f6; padding: 10px 12px; }
.sug.emotion { background: #fff1f2; }
.sug-head { display: flex; align-items: center; gap: 6px; font-size: 12px; color: #6b7280; margin-bottom: 4px; }
.sug-head .kind { font-weight: 600; color: #374151; }
.sug-head .src { background: #f3f4f6; border-radius: 4px; padding: 0 6px; }
.sug-head .score { margin-left: auto; color: #2563eb; }
.sug-content { color: #111827; line-height: 1.5; }
.sug-actions { display: flex; gap: 8px; margin-top: 6px; }
.sug-actions button { border: 1px solid #d1d5db; background: #fff; border-radius: 6px; padding: 2px 10px; cursor: pointer; }
.sug.draft .sug-content { background: #f9fafb; padding: 6px; border-radius: 6px; }
.cp-kb { border-top: 1px solid #e5e7eb; background: #fff; max-height: 200px; overflow-y: auto; }
.kb-row { display: flex; gap: 10px; align-items: center; padding: 6px 12px; border-bottom: 1px solid #f3f4f6; }
.kb-title { font-weight: 600; width: 120px; }
.kb-content { flex: 1; color: #6b7280; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kb-add { display: flex; gap: 8px; padding: 8px 12px; }
.kb-add input { flex: 1; border: 1px solid #d1d5db; border-radius: 6px; padding: 6px 8px; }
.empty { padding: 20px; text-align: center; color: #9ca3af; }
.empty.big { margin: auto; }
.hint { color: #9ca3af; font-weight: 400; }
</style>
