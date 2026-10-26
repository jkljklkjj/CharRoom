import { onMounted, onBeforeUnmount, ref, type CSSProperties } from 'vue'

/** A dialog follows the visible screen when mobile browser chrome or the keyboard moves. */
export function useVisualViewport(afterResize?: () => void) {
  const style = ref<CSSProperties>({})
  let frame = 0
  const viewport = window.visualViewport
  function measure() {
    frame = 0
    style.value = {
      top: `${viewport?.offsetTop ?? 0}px`, left: `${viewport?.offsetLeft ?? 0}px`,
      width: `${viewport?.width ?? window.innerWidth}px`, height: `${viewport?.height ?? window.innerHeight}px`,
      right: 'auto', bottom: 'auto'
    }
    afterResize?.()
  }
  function schedule() { if (!frame) frame = requestAnimationFrame(measure) }
  onMounted(() => {
    measure()
    window.addEventListener('resize', schedule)
    viewport?.addEventListener('resize', schedule)
    viewport?.addEventListener('scroll', schedule)
  })
  onBeforeUnmount(() => {
    cancelAnimationFrame(frame)
    window.removeEventListener('resize', schedule)
    viewport?.removeEventListener('resize', schedule)
    viewport?.removeEventListener('scroll', schedule)
  })
  return style
}
