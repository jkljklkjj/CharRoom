import { watch, type Ref } from 'vue'

const dialogs: HTMLElement[] = []
let previousOverflow = ''
const focusable = 'button:not(:disabled), summary, [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])'

/** Keep stacked dialogs keyboard accessible and return focus to their trigger. */
export function useDialog(root: Ref<HTMLElement | null>, close: () => void) {
  watch(root, (element, _, cleanup) => {
    if (!element) return
    const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const triggerLabel = trigger?.getAttribute('aria-label')
    if (!dialogs.length) { previousOverflow = document.body.style.overflow; document.body.style.overflow = 'hidden' }
    dialogs.push(element)
    const controls = () => [...element.querySelectorAll<HTMLElement>(focusable)].filter(item => item.getClientRects().length && !item.closest('[inert]'))
    const focus = () => (controls()[0] || element).focus({ preventScroll: true })
    queueMicrotask(() => { if (root.value === element && dialogs.at(-1) === element) focus() })
    const keydown = (event: KeyboardEvent) => {
      if (dialogs.at(-1) !== element || event.isComposing) return
      if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close() }
      if (event.key !== 'Tab') return
      const items = controls(), first = items[0], last = items.at(-1)
      if (!first) { event.preventDefault(); element.focus(); return }
      if (!element.contains(document.activeElement) || (event.shiftKey && document.activeElement === first)) {
        event.preventDefault(); (event.shiftKey ? last! : first).focus()
      } else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }
    document.addEventListener('keydown', keydown, true)
    cleanup(() => {
      document.removeEventListener('keydown', keydown, true)
      const index = dialogs.indexOf(element)
      if (index >= 0) dialogs.splice(index, 1)
      if (!dialogs.length) document.body.style.overflow = previousOverflow
      const top = dialogs.at(-1)
      // Responsive layouts may replace the original trigger while a dialog is open.
      const target = trigger?.isConnected && trigger.getClientRects().length ? trigger : triggerLabel
        ? [...document.querySelectorAll<HTMLElement>('button[aria-label]')].find(item => item.getAttribute('aria-label') === triggerLabel && item.getClientRects().length)
        : null
      if (target && (!top || top.contains(target))) target.focus({ preventScroll: true })
    })
  }, { flush: 'post' })
}
