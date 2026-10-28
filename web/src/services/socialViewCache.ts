import type {SocialDirectory,DailyQuestion} from '../types/social'
interface Pages {people:SocialDirectory;daily:DailyQuestion}
interface Token {scope:string;revision:number}
export interface DiscoveryFilters {q:string;tag:string;channel:string}
interface Entry {kind:keyof Pages;data:Pages[keyof Pages];at:number;day:string;scrollTop:number;size:number}
const clone=<T>(value:T):T=>JSON.parse(JSON.stringify(value))
const beijingDay=(now:number)=>new Date(now+8*3600000).toISOString().slice(0,10)

/** Short-lived, session-owned social pages. Other people's profiles never go to disk. */
export class SocialViewCache {
  private scope=''
  private revision=0
  private pages=new Map<string,Entry>()
  private search:DiscoveryFilters={q:'',tag:'',channel:'all'}
  constructor(private readonly now=Date.now,private readonly maxPages=8,private readonly maxCharacters=250000){}
  activate(accountId:string,session:number){
    const next=`${accountId}:${session}`
    if(next!==this.scope){this.clear();this.scope=next}
    return this.capture()
  }
  capture():Token{return {scope:this.scope,revision:this.revision}}
  isCurrent(token:Token){return token.scope===this.scope&&token.revision===this.revision}
  invalidate(){this.pages.clear();this.revision++}
  clear(){this.invalidate();this.scope='';this.search={q:'',tag:'',channel:'all'}}
  filters(token:Token){return this.isCurrent(token)?{...this.search}:{q:'',tag:'',channel:'all'}}
  rememberFilters(token:Token,filters:DiscoveryFilters){if(this.isCurrent(token))this.search={...filters}}
  get<K extends keyof Pages>(token:Token,key:string,kind:K):{data:Pages[K];scrollTop:number}|undefined{
    if(!this.isCurrent(token))return
    const entry=this.pages.get(key),now=this.now()
    if(!entry||entry.kind!==kind)return
    if(now-entry.at>=20000||now<entry.at||(kind==='daily'&&entry.day!==beijingDay(now))){this.pages.delete(key);return}
    this.pages.delete(key);this.pages.set(key,entry)
    return {data:clone(entry.data as Pages[K]),scrollTop:entry.scrollTop}
  }
  put<K extends keyof Pages>(token:Token,key:string,kind:K,data:Pages[K]){
    if(!this.isCurrent(token))return false
    const value=clone(data),size=JSON.stringify(value).length
    this.pages.delete(key)
    if(size>this.maxCharacters)return false
    const now=this.now()
    this.pages.set(key,{kind,data:value,at:now,day:beijingDay(now),scrollTop:0,size})
    let total=[...this.pages.values()].reduce((sum,page)=>sum+page.size,0)
    while(this.pages.size>this.maxPages||total>this.maxCharacters){
      const oldest=this.pages.keys().next().value!
      total-=this.pages.get(oldest)!.size;this.pages.delete(oldest)
    }
    return true
  }
  position(token:Token,key:string,scrollTop:number){
    const entry=this.isCurrent(token)?this.pages.get(key):undefined
    if(entry)entry.scrollTop=Math.max(0,scrollTop)
  }
}
export const socialViewCache=new SocialViewCache()
