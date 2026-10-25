/** Enter sends; modifier keys and IME candidate confirmation keep normal editing. */
export function composerEnter(event: KeyboardEvent, send: () => void) {
  if (event.key !== 'Enter' || event.isComposing || event.keyCode === 229 || event.shiftKey || event.ctrlKey || event.altKey || event.metaKey) return
  event.preventDefault()
  send()
}
