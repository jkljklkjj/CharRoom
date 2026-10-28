import type {Id,User} from '../types/chat'

export type ConversationKey = `user:${string}` | `group:${string}`
export class ConversationId {
  private constructor(readonly kind:'private'|'group',readonly id:string){}
  private static create(kind:'private'|'group',value:Id){
    if(typeof value==='number'&&!Number.isSafeInteger(value))throw new Error('Unsafe conversation ID')
    const id=String(value)
    if(!/^(0|[1-9][0-9]*)$/.test(id)||(kind==='group'&&id==='0'))throw new Error('Invalid conversation ID')
    return new ConversationId(kind,id)
  }
  static privateChat(id:Id){return this.create('private',id)}
  static group(id:Id){return this.create('group',id)}
  static parse(key:Id){
    const match=/^(user|group):(0|[1-9][0-9]*)$/.exec(String(key))
    if(!match)throw new Error('Expected a typed conversation key')
    return this.create(match[1]==='group'?'group':'private',match[2])
  }
  static forContact(user:User){return user.conversationKind==='group'?this.group(user.id):this.privateChat(user.id)}
  /** IndexedDB stores private peer IDs and namespaced group IDs; this is a storage boundary. */
  static fromHistoryKey(key:string){return key.startsWith('group:')?this.parse(key):this.privateChat(key)}
  get isGroup(){return this.kind==='group'}
  get key():ConversationKey{return `${this.isGroup?'group':'user'}:${this.id}`}
  get historyKey(){return this.isGroup?this.key:this.id}
}
