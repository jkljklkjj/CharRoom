/** Critical connection starts immediately; independent hydration tasks fail separately. */
export function bootstrapSession(connect: () => void | Promise<unknown>, tasks: Record<string, () => Promise<unknown>>,
  isCurrent: () => boolean, failed: (name: string, error: unknown) => void) {
  if (!isCurrent()) return Promise.resolve()
  try { void Promise.resolve(connect()).catch(error => { if (isCurrent()) failed('connection', error) }) }
  catch (error) { failed('connection', error) }
  return Promise.all(Object.entries(tasks).map(async ([name, task]) => {
    if (!isCurrent()) return
    try { await task() } catch (error) { if (isCurrent()) failed(name, error) }
  })).then(() => {})
}
