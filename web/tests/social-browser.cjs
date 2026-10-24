// UI regression with two isolated fixture accounts. Every external request is intercepted.
// Run after npm run build: node tests/social-browser.cjs <Chromium executable>
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict')
const puppeteer=require('puppeteer-core')
const dist=path.resolve(__dirname,'../dist')
const accounts=[91001,91002], pendingRequests=new Map(), profiles=new Map(), answers=new Map(), sends=[]
let room=null,queued=null,loseFirstAck=true
for(const id of accounts)profiles.set(id,{id:String(id),username:'Fixture '+id,signature:'A public fixture',bio:'Only fixture text',interests:['摄影'],city:'杭州',intent:'Discuss a photo',cover:'sunset',relation:'STRANGER',detailsVisible:true,settings:{userId:id,bio:'Only fixture text',interests:['摄影'],city:'杭州',intent:'Discuss a photo',cover:'sunset',visibility:'PUBLIC',discoverable:true,mutualDiscovery:true}})
const publicProfile=(id,viewer)=>{const profile=JSON.parse(JSON.stringify(profiles.get(id)));if(id!==viewer)delete profile.settings;profile.relation=id===viewer?'SELF':pendingRequests.get(id)===viewer?'PENDING':'STRANGER';return profile}
function roomView(id,after=0) {
  if(!room||!room.users.includes(id))return {state:queued===id?'QUEUED':'IDLE',messages:[]}
  const other=room.users.find(user=>user!==id)
  const exchanged=room.exchange.size===2
  return {state:room.state,roomId:room.id,serverTime:Date.now(),endsAt:room.endsAt,question:'如果今天多出一小时，你会做什么？',mySlot:id===room.users[0]?'A':'B',myReady:room.ready.has(id),extendRequested:room.extend.has(id),extensions:room.extensions,exchangeRequested:room.exchange.has(id),exchanged,messages:room.messages.filter(message=>message.seq>after),reason:room.reason||'',...(exchanged?{peer:publicProfile(other,id)}:{})}
}
function api(url,method,body,id) {
  if(url.pathname==='/api/auth/validate')return {valid:true}
  if(url.pathname==='/api/users/me')return {id,username:'Fixture '+id}
  if(url.pathname==='/api/users/online-status')return {}
  if(url.pathname==='/api/social/discovery')return {users:accounts.filter(other=>other!==id).map(other=>({...publicProfile(other,id),reason:'INTEREST'})),nextCursor:null}
  if(url.pathname.startsWith('/api/social/profiles/'))return publicProfile(Number(url.pathname.split('/').at(-1)),id)
  if(url.pathname==='/api/social/profile'){assert.equal('userId' in body,false,'editor must send only writable fields');const next={...profiles.get(id),...body};next.settings={...body,userId:id};profiles.set(id,next);return publicProfile(id,id)}
  if(url.pathname==='/api/social/friend-requests'){pendingRequests.set(Number(body.targetId),id);return true}
  if(url.pathname==='/api/social/daily'){
    const day='2026-10-24'
    if(method==='PUT'){answers.set(id,body.content);return true}
    return {day,question:'给今天起一个电影名字。',myAnswer:answers.get(id)||'',answers:[...answers].map(([author,content])=>({id:day+':'+author,content,createdAt:new Date().toISOString(),author:publicProfile(author,id)})),nextCursor:null}
  }
  if(url.pathname==='/api/social/encounter'){
    if(method==='POST'){
      if(body.action==='join'){
        if(queued&&queued!==id){room={id:'fixture-room',users:[queued,id],state:'PREPARING',ready:new Set(),extend:new Set(),exchange:new Set(),extensions:0,endsAt:0,messages:[]};queued=null}else queued=id
      }
      if(body.action==='ready'){room.ready.add(id);if(room.ready.size===2){room.state='ACTIVE';room.endsAt=Date.now()+300000}}
      if(body.action==='send'){
        sends.push(body.messageId)
        if(!room.messages.some(message=>message.messageId===body.messageId))room.messages.push({seq:room.messages.length+1,author:id===room.users[0]?'A':'B',content:body.content,createdAt:Date.now(),messageId:body.messageId})
        if(loseFirstAck){loseFirstAck=false;return {__httpError:500}}
      }
      if(body.action==='extend'){room.extend.add(id);if(room.extend.size===2){room.extensions++;room.endsAt+=300000;room.extend.clear()}}
      if(body.action==='exchange')room.exchange.add(id)
      if(body.action==='leave'){room.state='ENDED';room.reason='LEFT'}
      if(body.action==='cancel')queued=null
    }
    return roomView(id,Number(url.searchParams.get('after')||0))
  }
  return []
}
const mime={'.js':'application/javascript','.css':'text/css','.html':'text/html','.proto':'text/plain','.png':'image/png','.svg':'image/svg+xml','.ico':'image/x-icon','.webmanifest':'application/manifest+json'}
const server=http.createServer((request,response)=>{
  const pathname=new URL(request.url,'http://localhost').pathname
  const file=path.resolve(dist,pathname==='/app'||pathname==='/'?'index.html':pathname.slice(1))
  if(!file.startsWith(dist+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){response.writeHead(404).end();return}
  response.writeHead(200,{'Content-Type':mime[path.extname(file)]||'application/octet-stream'});response.end(fs.readFileSync(file))
})
async function click(page,selector,text){await page.waitForFunction((selector,text)=>[...document.querySelectorAll(selector)].some(button=>button.textContent.trim()===text&&!button.disabled),{},selector,text);await page.evaluate((selector,text)=>[...document.querySelectorAll(selector)].find(button=>button.textContent.trim()===text).click(),selector,text)}
async function text(page,selector){return page.$eval(selector,element=>element.textContent)}
async function main(){
  const executablePath=process.argv[2]||process.env.BROWSER_EXECUTABLE
  assert.ok(executablePath,'Supply a Chromium executable')
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve))
  const origin=`http://127.0.0.1:${server.address().port}`
  const browser=await puppeteer.launch({executablePath,headless:'new',args:['--no-proxy-server']})
  const errors=[]
  try {
    const pages=[]
    for(const id of accounts){
      const context=await browser.createIncognitoBrowserContext(),page=await context.newPage()
      await page.setViewport({width:1180,height:900});await page.setBypassServiceWorker(true)
      page.on('pageerror',failure=>errors.push(failure.message))
      await page.evaluateOnNewDocument(id=>{
        sessionStorage.setItem('charroom_token','fixture-'+id);localStorage.setItem('charroom_accountId',String(id));localStorage.setItem('locale','zh-CN')
        globalThis.WebTransport=class{constructor(){this.ready=Promise.resolve();this.closed=new Promise(()=>{});this.incomingBidirectionalStreams=new ReadableStream()}async createBidirectionalStream(){return {readable:new ReadableStream(),writable:new WritableStream({write(){}})}}close(){}}
      },id)
      await page.setRequestInterception(true)
      page.on('request',request=>{
        const url=new URL(request.url())
        if(url.origin===origin){request.continue();return}
        if(!url.pathname.startsWith('/api/')){request.abort();return}
        const data=api(url,request.method(),request.postData()?JSON.parse(request.postData()):{},id)
        request.respond({status:data?.__httpError||200,contentType:'application/json',headers:{'Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'*','Access-Control-Allow-Methods':'*'},body:JSON.stringify(data?.__httpError?{code:1500,message:'fixture lost acknowledgement'}:{code:0,data})})
      })
      await page.goto(origin+'/app');await page.waitForSelector('[aria-label="发现与遇见"]');await page.click('[aria-label="发现与遇见"]');await page.waitForSelector('.social-person');pages.push(page)
    }
    const [a,b]=pages
    await click(a,'.social-person button','看资料');await a.waitForSelector('.social-profile h2')
    await click(a,'.social-profile button','加为好友');await a.type('.social-profile textarea','A fixture introduction');await click(a,'.social-profile button','发送好友申请')
    await a.waitForFunction(()=>[...document.querySelectorAll('.social-profile button')].some(button=>button.textContent.includes('已申请')&&button.disabled))
    for(const page of pages){await click(page,'.social-nav button','轻遇');await click(page,'.social-room button','开始匹配')}
    for(const page of pages){await page.waitForFunction(()=>document.querySelector('.social-room')?.textContent.includes('已遇见'));await click(page,'.social-room button','我准备好了')}
    for(const page of pages)await page.waitForFunction(()=>document.querySelector('.social-room')?.textContent.includes('正在轻聊'))
    await a.type('.social-room textarea','fixture message after a lost acknowledgement');await click(a,'.social-room button','发送')
    await a.waitForFunction(()=>document.querySelector('.social-room [role="alert"]')?.textContent.includes('fixture'))
    await click(a,'.social-room button','发送');await b.waitForFunction(()=>document.querySelector('.social-room-messages')?.textContent.includes('fixture message'))
    assert.equal(sends.length,2);assert.equal(sends[0],sends[1]);assert.equal(room.messages.length,1)
    await click(a,'.social-room button','交换名片');assert.equal(await b.$('.social-room .save-btn + .save-btn'),null)
    await click(b,'.social-room button','交换名片');await a.waitForFunction(()=>document.querySelector('.social-room')?.textContent.includes('看资料'))
    await click(a,'.social-room button','再聊五分钟');await click(b,'.social-room button','再聊五分钟')
    for(let tries=0;room.extensions!==1&&tries<100;tries++)await new Promise(resolve=>setTimeout(resolve,20))
    assert.equal(room.extensions,1)
    await click(b,'.social-room button','结束并离开');await a.waitForFunction(()=>document.querySelector('.social-room')?.textContent.includes('本次相遇已结束'))
    await click(a,'.social-nav button','今日一问');await a.waitForSelector('.social-main textarea');await a.type('.social-main textarea','<img src=x onerror=alert(1)> 只是文字');await click(a,'.social-main button','保存回答（留空可删除）')
    await a.waitForFunction(()=>document.querySelector('.social-answer')?.textContent.includes('<img'))
    assert.equal(await a.$('.social-answer img'),null)
    await click(a,'.social-nav button','我的名片');await click(a,'.social-profile button','编辑资料');await a.waitForSelector('.social-profile form')
    const editInputs=await a.$$('.social-profile form input');await editInputs[0].click({clickCount:3});await editInputs[0].type('Updated fixture')
    await click(a,'.social-profile button','保存');await a.waitForFunction(()=>document.querySelector('.social-profile h2')?.textContent==='Updated fixture')
    for(const theme of ['light','dark'])for(const width of [1000,390,320]){
      await a.setViewport({width,height:900});await a.evaluate(theme=>document.documentElement.setAttribute('data-theme',theme),theme)
      const overflow=await a.$eval('.social-hub',element=>element.scrollWidth>element.clientWidth+1);assert.equal(overflow,false,`layout ${width}`)
      if(process.env.SOCIAL_SCREENSHOT_DIR){fs.mkdirSync(process.env.SOCIAL_SCREENSHOT_DIR,{recursive:true});await a.screenshot({path:path.join(process.env.SOCIAL_SCREENSHOT_DIR,`profile-${theme}-${width}.png`)})}
    }
    assert.deepEqual(errors,[])
    console.log('PASS: two accounts, discovery/profile/request, random room readiness, lost-ACK retry, exchange, extension, leave, daily text escaping, edit, dark/mobile layout.')
  } finally {await browser.close();await new Promise(resolve=>server.close(resolve))}
}
main().catch(failure=>{console.error(failure);server.close();process.exitCode=1})
