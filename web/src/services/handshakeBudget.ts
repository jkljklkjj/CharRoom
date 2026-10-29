/** Client-side pacing only; the authoritative account/device window lives on the server. */
export class HandshakeBudget {
  private attempts: number[] = []
  private blockedUntil = 0
  reserve(now = Date.now()): number {
    this.attempts = this.attempts.filter(time => time > now - 60000)
    const until = Math.max(this.blockedUntil, this.attempts.length >= 6 ? this.attempts[0]! + 60000 : 0)
    if (until > now) return until - now
    this.attempts.push(now)
    return 0
  }
  block(retryAfter: number, now = Date.now()) {
    this.blockedUntil = Math.max(this.blockedUntil, now + Math.max(1, Math.min(60000, retryAfter)))
  }
}
export function handshakeRetryAfter(message: string): number | null {
  const match = /^HANDSHAKE_RATE_LIMITED:(\d+)$/.exec(message)
  return match ? Math.max(1, Math.min(60000, Number(match[1]))) : null
}
