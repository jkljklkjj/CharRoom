/** Compose cancellation and a bounded deadline without retaining event listeners. */
export function requestScope(signals: (AbortSignal | null | undefined)[], timeout = 20_000) {
  const controller = new AbortController()
  const abort = () => controller.abort()
  signals.forEach(signal => { if (signal?.aborted) abort(); else signal?.addEventListener('abort', abort, { once: true }) })
  const timer = setTimeout(abort, timeout)
  return { signal: controller.signal, close: () => {
    clearTimeout(timer)
    signals.forEach(signal => signal?.removeEventListener('abort', abort))
  } }
}

export function abortable<T>(promise: Promise<T>, signal: AbortSignal): Promise<T> {
  return new Promise((resolve, reject) => {
    const aborted = () => reject(new Error('Request cancelled'))
    if (signal.aborted) { aborted(); return }
    signal.addEventListener('abort', aborted, { once: true })
    promise.then(resolve, reject).finally(() => signal.removeEventListener('abort', aborted))
  })
}
