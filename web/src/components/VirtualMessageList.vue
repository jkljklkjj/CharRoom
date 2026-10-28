<template>
  <div ref="element" class="virtual-message-list" :data-message-count="items.length" @scroll="scrolled">
    <div class="virtual-message-space" :style="{ height: `${layout.total}px` }">
      <div v-for="row in visible" :key="row.key" :ref="node => observe(node, row.key)"
        class="virtual-message-row" :data-message-key="row.key" :style="{ top: `${layout.offsetAt(row.index)}px` }">
        <slot :item="row.item" :index="row.index" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import type { ComponentPublicInstance } from 'vue'
import type { ChatMessage } from '../types/chat'
import type {ReadingPosition} from '../services/conversationViews'
import { IncrementalVirtualLayout } from '../utils/virtualLayout'

const props = defineProps<{ items: readonly ChatMessage[] }>()
const emit = defineEmits<{ scroll: [event: Event] }>()
const element = ref<HTMLElement | null>(null)
const heights = new Map<string, number>(), nodes = new Map<HTMLElement, string>()
const revision = shallowRef(0), top = ref(0), viewport = ref(600)
const keys = computed(() => {
  const occurrences = new Map<string, number>()
  return props.items.map(item => {
    const base = item.messageId || `${item.time ?? item.timestamp}:${item.user}:${item.text}`
    const occurrence = occurrences.get(base) || 0
    occurrences.set(base, occurrence + 1)
    return `${base}:${occurrence}`
  })
})
const heightIndex=new IncrementalVirtualLayout()
const layout = computed(() => { revision.value; return {
  total:heightIndex.total,offsetAt:(index:number)=>heightIndex.offsetAt(index),
  range:(top:number,height:number)=>heightIndex.range(top,height),indexAt:(top:number)=>heightIndex.indexAt(top)
} })
const visible = computed(() => {
  const { start, end } = layout.value.range(top.value, viewport.value)
  return props.items.slice(start, end).map((item, offset) => ({ item, index: start + offset, key: keys.value[start + offset] }))
})

function anchor(oldKeys = keys.value) {
  const el = element.value, before = layout.value
  if (!el || !oldKeys.length) return null
  const index = before.indexAt(el.scrollTop)
  return { key: oldKeys[index], delta: el.scrollTop - before.offsetAt(index),
    bottom: el.scrollHeight - el.scrollTop - el.clientHeight <= 2 }
}
function restore(saved: ReturnType<typeof anchor>, followBottom = false) {
  const el = element.value
  if (!el || !saved) return
  const index = keys.value.indexOf(saved.key)
  if (followBottom && saved.bottom) el.scrollTop = Math.max(0, el.scrollHeight - el.clientHeight)
  else if (index >= 0) el.scrollTop = layout.value.offsetAt(index) + saved.delta
  top.value = el.scrollTop
}
watch(keys, async (next, previous) => {
  if(previous && next.length===previous.length && next.every((key,index)=>key===previous[index]))return
  const saved = previous ? anchor(previous) : null
  heightIndex.reset(next,heights);revision.value++
  // Drop height entries from a different conversation/deleted messages.
  const retained = new Set(next)
  for (const key of heights.keys()) if (!retained.has(key)) heights.delete(key)
  if (previous?.length && next[0] !== previous[0]) { await nextTick(); restore(saved) }
},{immediate:true})

let observer: ResizeObserver | undefined, frame = 0, disposed = false
const changed = new Map<string, number>()
function measured(entries: ResizeObserverEntry[]) {
  for (const entry of entries) {
    if (entry.target === element.value) { viewport.value = entry.contentRect.height; continue }
    const key = nodes.get(entry.target as HTMLElement)
    const height = (entry.target as HTMLElement).getBoundingClientRect().height
    if (key && height > 0 && Math.abs((heights.get(key) ?? 100) - height) > 0.5) changed.set(key, height)
  }
  if (changed.size && !frame) frame = requestAnimationFrame(async () => {
    frame = 0
    const saved = anchor()
    changed.forEach((height, key) => { heights.set(key, height);heightIndex.update(key,height) }); changed.clear()
    revision.value++
    await nextTick()
    if (!disposed) restore(saved, true)
  })
}
function observe(node: Element | ComponentPublicInstance | null, key: string) {
  if (!(node instanceof HTMLElement)) return
  if (nodes.get(node) === key) return
  nodes.set(node, key)
  observer?.observe(node, { box: 'border-box' })
}
function scrolled(event: Event) {
  if (element.value) { top.value = element.value.scrollTop; viewport.value = element.value.clientHeight }
  // Vue function refs receive null on removal. Release detached nodes here and after updates.
  for (const node of nodes.keys()) if (!node.isConnected) { observer?.unobserve(node); nodes.delete(node) }
  emit('scroll', event)
}
watch(visible, () => nextTick(() => {
  for (const node of nodes.keys()) if (!node.isConnected) { observer?.unobserve(node); nodes.delete(node) }
}))
onMounted(() => {
  observer = new ResizeObserver(measured)
  if (element.value) { observer.observe(element.value); viewport.value = element.value.clientHeight }
  nodes.forEach((_, node) => observer!.observe(node, { box: 'border-box' }))
})
onBeforeUnmount(() => { disposed = true; cancelAnimationFrame(frame); observer?.disconnect(); nodes.clear(); changed.clear() })
function captureReading(): ReadingPosition | null {
  const saved=anchor()
  if(!saved)return null
  const index=keys.value.indexOf(saved.key)
  return {...saved,height:layout.value.offsetAt(index+1)-layout.value.offsetAt(index)}
}
async function restoreReading(position:ReadingPosition) {
  // A tall row's saved offset can exceed the default estimate. Seed its height
  // before scrolling so the first measurement keeps this row as the anchor.
  if(position.height && Number.isFinite(position.height) && position.height>0) {
    heights.set(position.key,position.height);heightIndex.update(position.key,position.height);revision.value++
  }
  await nextTick()
  restore(position,position.bottom)
}
defineExpose({ element, captureReading, restoreReading })
</script>

<style scoped>
.virtual-message-list{overflow:auto;overflow-anchor:none;min-height:0;flex:1}
.virtual-message-space{position:relative;flex:none;width:100%}
.virtual-message-row{position:absolute;left:0;right:0;box-sizing:border-box;padding-bottom:12px;display:flow-root}
</style>
