import type { User, Group } from '../types/chat'

export interface DirectorySnapshot { version: string; users: readonly User[]; groups: readonly Group[] }
export interface DirectoryReply { version: string; unchanged: boolean; users?: User[]; groups?: Group[] }
export type DirectoryPhase = 'loading' | 'refreshing' | 'ready' | 'stale' | 'error'
interface Scope {key: string; accountId: string; isCurrent(): boolean}
interface Ports {
  capture(): Scope
  load(account: string): Promise<DirectorySnapshot | null>
  save(account: string, value: DirectorySnapshot): Promise<void>
  fetch(version: string): Promise<DirectoryReply>
  apply(value: DirectorySnapshot): void
  status(value: {phase: DirectoryPhase; error?: string}): void
  now(): number
}

/** One local-first directory lifecycle per authenticated session; components never fetch lists themselves. */
export class ContactDirectorySync {
  private scopeKey = ''
  private value: DirectorySnapshot | null = null
  private flight: {key: string; promise: Promise<void>} | null = null
  private hydrated = false
  private mutation = 0
  private checkedAt = -Infinity
  constructor(private readonly ports: Ports) {}

  invalidate() { this.mutation++; this.checkedAt = -Infinity; if (this.value) this.value = {...this.value,version:''} }

  refresh(force = false): Promise<void> {
    const scope = this.ports.capture()
    if (!scope.accountId || !scope.isCurrent()) return Promise.resolve()
    if (scope.key !== this.scopeKey) {
      this.scopeKey=scope.key; this.value=null; this.hydrated=false; this.checkedAt=-Infinity; this.mutation++
    }
    if (this.flight?.key === scope.key) return this.flight.promise
    if (!force && this.ports.now()-this.checkedAt < 15000) return Promise.resolve()
    const flight = {key:scope.key,promise:Promise.resolve()}
    flight.promise = this.run(scope).finally(()=>{if(this.flight===flight)this.flight=null})
    this.flight=flight
    return flight.promise
  }

  private async run(scope: Scope) {
    try {
      if (!this.hydrated) {
        this.ports.status({phase:'loading'})
        const revision=this.mutation
        // Local storage failure must not prevent an authoritative refresh.
        let local: DirectorySnapshot | null = null
        try { local=await this.ports.load(scope.accountId) } catch { /* network can restore the snapshot */ }
        if (!scope.isCurrent()) return
        if (local && revision===this.mutation) {this.value=local;this.ports.apply(local)}
        this.hydrated=true
      }
      this.ports.status({phase:this.value?'refreshing':'loading'})
      for(let attempt=0;attempt<3;attempt++) {
        const expiresSoon=this.value?.users.some(user=>{
          if(!user.avatarUrl)return false
          try {
            const expires=Number(new URL(user.avatarUrl).searchParams.get('Expires'))
            return expires>0 && expires*1000<this.ports.now()+3600000
          } catch {return false}
        })
        const revision=this.mutation, known=expiresSoon?'':this.value?.version || ''
        const reply=await this.ports.fetch(known)
        if(!scope.isCurrent())return
        if(revision!==this.mutation)continue
        if(!reply.version || (reply.unchanged && (!known || reply.version!==known))) throw Error('Invalid directory version')
        if(!reply.unchanged) {
          if(!Array.isArray(reply.users)||!Array.isArray(reply.groups))throw Error('Invalid directory snapshot')
          const next={version:reply.version,users:reply.users,groups:reply.groups}
          this.value=next;this.ports.apply(next)
          try { await this.ports.save(scope.accountId,next) } catch { /* keep the authoritative in-memory snapshot */ }
          if(!scope.isCurrent())return
          if(revision!==this.mutation)continue
        }
        this.checkedAt=this.ports.now();this.ports.status({phase:'ready'});return
      }
      throw Error('Directory changed during refresh')
    } catch(error) {
      if(scope.isCurrent())this.ports.status({phase:this.value?'stale':'error',error:(error as Error).message})
      throw error
    }
  }
}
