/** Keep document scrolling separate from the app's internal scroll containers. */
export function syncPageViewport(path: string) {
  if (typeof document === 'undefined') return
  const app = path.replace(/\/+$/, '') === '/app'
  const previous = document.documentElement.hasAttribute('data-app-viewport')
  document.documentElement.toggleAttribute('data-app-viewport', app)
  document.body.classList.toggle('app-viewport', app)
  if (app && !previous) window.scrollTo(0, 0)
}
