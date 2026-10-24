/** Preserve the callback's argument and receiver types. */
export function debounce<This, Args extends unknown[]>(fn: (this: This, ...args: Args) => unknown, delay = 300) {
  let timer: ReturnType<typeof setTimeout> | undefined
  return function (this: This, ...args: Args) {
    clearTimeout(timer)
    timer = setTimeout(() => fn.apply(this, args), delay)
  }
}
export function throttle<This, Args extends unknown[]>(fn: (this: This, ...args: Args) => unknown, delay = 300) {
  let last = 0
  return function (this: This, ...args: Args) {
    const now = Date.now()
    if (now - last >= delay) { last = now; fn.apply(this, args) }
  }
}
