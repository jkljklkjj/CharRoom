import type {ChatMessage} from '../types/chat'
import type {MessageCursor} from '../utils/messageDB'
export interface ReadingPosition {key:string;delta:number;bottom:boolean;height?:number}
export interface ConversationView {messages:ChatMessage[];before:MessageCursor|null;hasMore:boolean;hasNewer?:boolean}

/** Bounded recent views. IndexedDB remains authoritative; evictions never delete history. */
export class ConversationViews {
  private views=new Map<string,ConversationView>()
  private positions=new Map<string,ReadingPosition>()
  private costs=new Map<string,{rows:number;characters:number}>()
  private rowCount=0
  private characterCount=0
  constructor(private readonly maxViews=10,private readonly maxRows=10000,private readonly maxCharacters=2000000) {}
  clear(){this.views.clear();this.positions.clear();this.costs.clear();this.rowCount=0;this.characterCount=0}
  get(key:string) {
    const value=this.views.get(key)
    if(value){this.views.delete(key);this.views.set(key,value)}
    return value
  }
  put(key:string,value:ConversationView) {
    this.replace(key,value)
    this.trim()
  }
  private replace(key:string,value:ConversationView) {
    this.remove(key)
    this.views.delete(key);this.views.set(key,value)
    const cost={rows:value.messages.length,characters:value.messages.reduce((size,message)=>size+message.text.length+(message.fileUrl?.length||0),0)}
    this.costs.set(key,cost);this.rowCount+=cost.rows;this.characterCount+=cost.characters
  }
  private remove(key:string) {
    const cost=this.costs.get(key)
    if(cost){this.rowCount-=cost.rows;this.characterCount-=cost.characters;this.costs.delete(key)}
    this.views.delete(key)
  }
  private trim() {
    while(this.views.size>this.maxViews || this.rowCount>this.maxRows || this.characterCount>this.maxCharacters) {
      const oldest=this.views.keys().next().value
      if(oldest===undefined)break
      this.remove(oldest)
    }
  }
  update(key:string,change:(messages:ChatMessage[])=>ChatMessage[]) {
    const value=this.views.get(key)
    if(value){const messages=change(value.messages);if(messages!==value.messages)this.put(key,{...value,messages})}
  }
  updateAll(change:(messages:ChatMessage[])=>ChatMessage[]) {
    for(const [key,value] of [...this.views]){
      const messages=change(value.messages)
      if(messages!==value.messages)this.replace(key,{...value,messages})
    }
    this.trim()
  }
  savePosition(key:string,value:ReadingPosition) {
    this.positions.delete(key);this.positions.set(key,value)
    while(this.positions.size>this.maxViews)this.positions.delete(this.positions.keys().next().value!)
  }
  position(key:string){return this.positions.get(key)}
}
export const conversationViews=new ConversationViews()
