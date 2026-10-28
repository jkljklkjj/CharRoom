import {ConversationId} from './conversationId'
import type {User} from '../types/chat'
export interface ContactSection {key:string;label:string;users:User[]}
export type ContactRow={key:string;kind:'heading';label:string;count:number}|{key:string;kind:'user';user:User}
export function contactRows(sections:readonly ContactSection[]):ContactRow[]{
  const rows:ContactRow[]=[]
  for(const section of sections){
    if(section.label&&section.users.length)rows.push({key:`heading:${section.key}`,kind:'heading',label:section.label,count:section.users.length})
    for(const user of section.users)rows.push({key:ConversationId.forContact(user).key,kind:'user',user})
  }
  return rows
}
export function contactRowHeight(row:ContactRow){return row.kind==='heading'?40:72}
