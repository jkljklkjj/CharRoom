<template>
  <span v-if="phase" class="send-indicator">
    <span v-if="phase==='sending'" class="send-spinner" role="status" :aria-label="$t('polish.sending')" :title="$t('polish.sending')"></span>
    <button v-else type="button" class="send-failure" :aria-label="$t('product.sendFailed')" :title="$t('product.sendFailed')" @click.stop="emit('retry')">!</button>
  </span>
</template>
<script setup lang="ts">
import {ref,watch,onBeforeUnmount} from 'vue'
import type {MessageStatus} from '../types/chat'
import {messageSendState,type SendIndicatorPhase} from '../utils/messageSendState'

const props=defineProps<{status?:MessageStatus;startedAt:number}>()
const emit=defineEmits<{'retry':[]}>()
const phase=ref<SendIndicatorPhase>(null)
let timer:ReturnType<typeof setTimeout>|undefined
function update() {
  clearTimeout(timer);timer=undefined
  const now=Date.now(),state=messageSendState(props.status,props.startedAt,now)
  phase.value=state.phase
  if(state.nextAt!==null)timer=setTimeout(update,Math.max(0,state.nextAt-now))
}
watch(()=>[props.status,props.startedAt],update,{immediate:true})
onBeforeUnmount(()=>clearTimeout(timer))
</script>
<style scoped>
.send-indicator{align-self:flex-end;display:inline-grid;place-items:center;flex:none;width:16px;min-height:20px;margin-bottom:8px}
.send-spinner{box-sizing:border-box;width:12px;height:12px;border:1.5px solid var(--muted);border-right-color:transparent;border-radius:50%;animation:send-spin .8s linear infinite}
.send-failure{position:relative;display:grid;place-items:center;width:16px;height:20px;padding:0;border:0;background:none;color:#C1352B;font-size:19px;line-height:1;font-weight:700;cursor:pointer}
.send-failure::before{content:'';position:absolute;inset:-8px}
[data-theme="dark"] .send-failure{color:#FF8C82}
@keyframes send-spin{to{transform:rotate(360deg)}}
@media(prefers-reduced-motion:reduce){.send-spinner{animation:none}}
</style>
