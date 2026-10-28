import type {ChatMessage} from '../types/chat'
/** A display window; persisted history and outbox are never deleted by trimming. */
export function historyWindow(rows:readonly ChatMessage[],edge:'oldest'|'latest',maxRows=2000,maxCharacters=2000000){
  let size=0,start=edge==='latest'?rows.length:0,end=start
  if(edge==='oldest'){
    while(end<rows.length&&end<maxRows){const cost=rows[end].text.length+(rows[end].fileUrl?.length||0);if(end>0&&size+cost>maxCharacters)break;size+=cost;end++}
  } else {
    while(start>0&&rows.length-start<maxRows){const cost=rows[start-1].text.length+(rows[start-1].fileUrl?.length||0);if(start<rows.length&&size+cost>maxCharacters)break;size+=cost;start--}
    end=rows.length
  }
  return {messages:rows.slice(start,end),trimmedBefore:start>0,trimmedAfter:end<rows.length}
}
