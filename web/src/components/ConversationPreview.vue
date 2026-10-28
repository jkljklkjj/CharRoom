<template>
  <section ref="preview" class="conversation-preview" :class="{entered, compact}" :aria-label="$t('product.example')">
    <header class="preview-header"><div class="paper-avatar">{{ $t(`product.demo.${scene}.initial`) }}</div><div><strong>{{ $t(`product.demo.${scene}.name`) }}</strong><small>{{ $t('product.example') }}</small></div></header>
    <div ref="thread" class="preview-thread" :key="scene">
      <p class="preview-time">{{ $t('product.demo.time') }}</p>
      <div class="preview-row"><div class="paper-avatar small">{{ $t(`product.demo.${scene}.initial`) }}</div><div class="paper-bubble">{{ $t(`product.demo.${scene}.first`) }}</div></div>
      <div class="preview-row mine"><div class="paper-bubble mine">{{ $t(`product.demo.${scene}.second`) }}</div></div>
      <div v-if="scene === 'friends'" class="preview-postcard" aria-hidden="true">
        <svg viewBox="0 0 240 125" fill="none"><path fill="#E8D9B9" d="M0 0h240v125H0z"/><path fill="#C8D2BB" d="M0 0h85v125H0z"/><path d="M16 0v125M42 0v125M68 0v125" stroke="#A3B69A"/><ellipse cx="148" cy="103" rx="70" ry="12" fill="#BCA789"/><path d="M89 63c3 57 110 57 113 0" fill="#FFF9EA"/><ellipse cx="145" cy="63" rx="57" ry="23" fill="#E6AC62"/><path d="M105 58c18-15 10 24 28 5s14-18 22-3 17-17 29-2M106 70c18-15 11 16 28 2s26 12 44-1" stroke="#F9DF9E" stroke-width="4"/><ellipse cx="159" cy="55" rx="16" ry="12" fill="#FFF8E8"/><ellipse cx="159" cy="55" rx="8" ry="6" fill="#EDAD4C"/><path d="m180 39 34-28M185 44l34-28" stroke="#654734" stroke-width="4" stroke-linecap="round"/><path d="M122 29c-9-9 10-10 0-20M143 24c-9-9 10-10 0-20" stroke="#FFF9EA" stroke-width="3" stroke-linecap="round"/></svg>
        <span>{{ $t('product.demo.picture') }}</span>
      </div>
      <div class="preview-row"><div class="paper-avatar small">{{ $t(`product.demo.${scene}.initial`) }}</div><div class="paper-bubble">{{ $t(`product.demo.${scene}.third`) }}</div></div>
      <div v-for="message in sent" :key="message.id" class="preview-row mine"><div class="paper-bubble mine">{{ message.text }}</div></div>
    </div>
    <form class="preview-compose" @submit.prevent="send">
      <input
        v-model="draft"
        class="preview-input"
        maxlength="200"
        autocomplete="off"
        :placeholder="$t('product.demo.placeholder')"
        :aria-label="$t('product.demo.placeholder')"
      />
      <button class="preview-send" type="submit" :disabled="!draft.trim()" :aria-label="$t('chat.send')">↗</button>
    </form>
  </section>
</template>
<script setup lang="ts">
import {ref,nextTick,watch,onMounted,onBeforeUnmount} from 'vue'
const props=withDefaults(defineProps<{scene?:'friends'|'assistant';compact?:boolean}>(),{scene:'friends',compact:false})
const preview=ref<HTMLElement|null>(null),entered=ref(false)
const thread=ref<HTMLElement|null>(null)
const draft=ref('')
interface SentMessage{id:number;text:string}
const sent=ref<SentMessage[]>([])
let sentSeq=0
let observer:IntersectionObserver|undefined
onMounted(()=>{
  observer=new IntersectionObserver(entries=>{if(entries.some(entry=>entry.isIntersecting)){entered.value=true;observer?.disconnect()}},{threshold:.15})
  if(preview.value)observer.observe(preview.value)
})
onBeforeUnmount(()=>observer?.disconnect())

/** 切换场景时清空本地追加的消息，避免串到另一个会话 */
watch(()=>props.scene,()=>{sent.value=[];draft.value=''})

function scrollToLatest(){
  // 内容超出高度后滚到底部；滚动条隐藏，靠视觉上“一直是最新”
  nextTick(()=>{const el=thread.value;if(el)el.scrollTop=el.scrollHeight})
}

function send(){
  const text=draft.value.trim()
  if(!text)return
  sent.value.push({id:++sentSeq,text})
  draft.value=''
  scrollToLatest()
}
</script>
<style scoped>
.conversation-preview{background:var(--panel);border:1px solid var(--surface-border-strong);border-radius:18px;overflow:hidden;text-align:left;color:var(--text-primary)}
.preview-header{display:flex;align-items:center;gap:12px;padding:18px 22px;border-bottom:1px solid var(--surface-border)}
.preview-header strong{display:block;font-size:15px}.preview-header small{display:block;color:var(--muted);font-size:11px;margin-top:4px}
/* 可滚动但不显示滚动条：滚动条宽度为 0，同时保留可滚动能力 */
.preview-thread{display:grid;gap:17px;padding:16px 22px 24px;background:var(--bg);max-height:300px;overflow-y:auto;overscroll-behavior:contain;scrollbar-width:none;-ms-overflow-style:none}
.preview-thread::-webkit-scrollbar{display:none;width:0;height:0}
.preview-time{text-align:center;color:var(--muted);font-size:11px;margin:0 0 3px}
.preview-row{display:flex;align-items:flex-start;gap:8px}.preview-row.mine{justify-content:flex-end;padding-left:36px}.paper-bubble{font-size:14px;max-width:82%}
.preview-postcard{position:relative;width:190px;max-width:76%;margin-left:40px;background:var(--panel);border:6px solid var(--panel);border-bottom-width:25px;box-shadow:0 3px 9px #342b2510;transform:rotate(-3deg);border-radius:4px}.preview-postcard svg{display:block;width:100%}.preview-postcard span{position:absolute;bottom:-21px;left:4px;font-size:10px;color:var(--muted)}
.preview-compose{display:flex;align-items:center;gap:8px;margin:14px 20px;color:var(--muted);font-size:12px}
.preview-compose:focus-within .preview-send{box-shadow:0 0 0 2px var(--action-soft,transparent)}
.preview-input{flex:1;min-width:0;padding:6px 0;border:0;background:transparent;font:inherit;color:var(--text-primary);outline:none}
.preview-input::placeholder{color:var(--muted)}
.preview-send{display:grid;place-items:center;flex:none;width:32px;height:32px;border:0;border-radius:11px;background:var(--action);color:var(--on-action);font-size:20px;cursor:pointer;transition:opacity .18s}
.preview-send:disabled{opacity:.45;cursor:default}
.entered .preview-row{animation:preview-reply .4s ease both}.entered .preview-row:nth-of-type(2){animation-delay:.15s}.entered .preview-row:last-child{animation-delay:.3s}
.compact .preview-thread{padding:18px;gap:14px;max-height:220px}.compact .preview-header{padding:16px 18px}.compact .preview-compose{margin:10px 14px}
@keyframes preview-reply{from{opacity:0;transform:translateY(7px)}to{opacity:1;transform:translateY(0)}}
@media(prefers-reduced-motion:reduce){.entered .preview-row{animation:none}}
</style>
