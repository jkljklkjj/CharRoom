export interface DailyAnswerDraft {accountId:string;day:string;text:string;revision:string}
const key=(accountId:string)=>`charroom_daily_answer_${accountId}`

/** One latest day per account; no answer-feed or other-user information is persisted. */
export function loadDailyAnswerDraft(accountId:string,day:string):DailyAnswerDraft|null{
  if(!accountId||!/^\d{4}-\d{2}-\d{2}$/.test(day))return null
  try{
    const value=JSON.parse(localStorage.getItem(key(accountId))||'null')
    if(value?.version!==1||value.accountId!==accountId||value.day!==day||typeof value.text!=='string'||value.text.length>500||typeof value.revision!=='string')return null
    return {accountId,day,text:value.text,revision:value.revision}
  }catch{return null}
}
export function saveDailyAnswerDraft(accountId:string,day:string,text:string):DailyAnswerDraft|null{
  if(!accountId||!/^\d{4}-\d{2}-\d{2}$/.test(day)||text.length>500)return null
  const draft={accountId,day,text,revision:globalThis.crypto.randomUUID()}
  try{localStorage.setItem(key(accountId),JSON.stringify({version:1,...draft}));return draft}catch{return null}
}
export function clearSubmittedDailyAnswer(draft:DailyAnswerDraft|null){
  if(!draft)return
  const current=loadDailyAnswerDraft(draft.accountId,draft.day)
  if(current?.revision===draft.revision)clearDailyAnswerDraft(draft.accountId)
}
export function clearDailyAnswerDraft(accountId:string){try{localStorage.removeItem(key(accountId))}catch{}}
