export function encounterPollDelay(state:string,failures:number){
  if(failures>0)return Math.min(60000,4000*2**Math.min(4,failures-1))
  return ['ACTIVE','PREPARING','MATCHING','WAITING','QUEUED','READY'].includes(state)?1200:15000
}
