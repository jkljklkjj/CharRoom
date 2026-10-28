import type {DirectorySnapshot} from '../services/contactDirectorySync'
let connection: Promise<IDBDatabase> | null = null
function database() {
  connection ??= new Promise<IDBDatabase>((resolve,reject)=>{
    const request=indexedDB.open('charroom_directory',1)
    request.onupgradeneeded=()=>request.result.createObjectStore('snapshots',{keyPath:'accountId'})
    request.onerror=()=>{connection=null;reject(request.error)}
    request.onsuccess=()=>{
      const db=request.result
      db.onversionchange=()=>{db.close();connection=null}
      resolve(db)
    }
  })
  return connection
}
export async function loadDirectory(accountId:string):Promise<DirectorySnapshot|null> {
  const db=await database()
  return new Promise((resolve,reject)=>{
    const tx=db.transaction('snapshots','readonly'),request=tx.objectStore('snapshots').get(accountId)
    tx.oncomplete=()=>{
      const value=request.result as DirectorySnapshot|undefined
      resolve(value && typeof value.version==='string' && Array.isArray(value.users) && Array.isArray(value.groups)?value:null)
    }
    tx.onerror=tx.onabort=()=>reject(tx.error)
  })
}
export async function saveDirectory(accountId:string,value:DirectorySnapshot) {
  const db=await database()
  return new Promise<void>((resolve,reject)=>{
    const tx=db.transaction('snapshots','readwrite')
    tx.objectStore('snapshots').put({accountId,...value})
    tx.oncomplete=()=>resolve();tx.onerror=tx.onabort=()=>reject(tx.error)
  })
}
