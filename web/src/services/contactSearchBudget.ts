/** UI guard only; the server enforces the shared account budget across devices. */
export class ContactSearchBudget {
  private accounts = new Map<string, { attempts: number[]; blockedUntil: number }>()
  constructor(private clock: () => number = Date.now) {}
  reserve(account: string): number {
    const now = this.clock()
    let entry = this.accounts.get(account)
    if (!entry) {
      if (this.accounts.size >= 100) this.accounts.delete(this.accounts.keys().next().value!)
      entry = { attempts: [], blockedUntil: 0 }; this.accounts.set(account, entry)
    }
    entry.attempts = entry.attempts.filter(time => time > now - 60_000)
    const retryAt = Math.max(entry.blockedUntil, entry.attempts.length >= 8 ? entry.attempts[0]! + 60_000 : 0)
    if (retryAt > now) return retryAt - now
    entry.attempts.push(now)
    return 0
  }
  defer(account: string, millis: number) {
    const entry = this.accounts.get(account)
    if (entry) entry.blockedUntil = Math.max(entry.blockedUntil, this.clock() + millis)
  }
}
export const contactSearchBudget = new ContactSearchBudget()
