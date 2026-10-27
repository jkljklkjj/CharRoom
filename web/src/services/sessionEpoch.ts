/** Every login/logout owns a generation, including logging back into the same account. */
export class SessionEpoch {
  private version = 0
  private controller = new AbortController()
  renew() {
    this.controller.abort()
    this.controller = new AbortController()
    this.version++
  }
  capture() {
    const version = this.version, signal = this.controller.signal
    return { version, signal, isCurrent: () => version === this.version && !signal.aborted }
  }
}
export const sessionEpoch = new SessionEpoch()
