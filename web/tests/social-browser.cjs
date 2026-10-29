// UI regression with two isolated fixture accounts. Every external request is intercepted.
// Run after npm run build: node tests/social-browser.cjs <Chromium executable>
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict')
const puppeteer=require('puppeteer-core')
const dist=path.resolve(__dirname,'../dist')
const accounts=[91001,91002], pendingRequests=new Map(), profiles=new Map(), answers=new Map(), sends=[]
const blocks=new Map(), reports=[], friendships=new Set(), delays=new Map(), failures=new Set(), requests=[]
let room=null,queued=null,loseFirstAck=true,delayNextSend=false
for(const id of accounts)profiles.set(id,{id:String(id),username:'Fixture '+id,signature:'A public fixture',bio:'Only fixture text',interests:['摄影'],city:'杭州',intent:'Discuss a photo',cover:'sunset',relation:'STRANGER',detailsVisible:true,settings:{userId:id,bio:'Only fixture text',interests:['摄影'],city:'杭州',intent:'Discuss a photo',cover:'sunset',visibility:'PUBLIC',discoverable:true,mutualDiscovery:true}})
const publicProfile=(id,viewer)=>{const profile=JSON.parse(JSON.stringify(profiles.get(id)));if(id!==viewer)delete profile.settings;profile.relation=id===viewer?'SELF':friendships.has([id,viewer].sort().join(':'))?'FRIEND':pendingRequests.get(id)===viewer?'PENDING':'STRANGER';return profile}
function roomView(id,after=0) {
  if(!room||!room.users.includes(id))return {state:queued===id?'QUEUED':'IDLE',messages:[]}
  const other=room.users.find(user=>user!==id)
  const exchanged=room.exchange.size===2
  return {state:room.state,roomId:room.id,serverTime:Date.now(),endsAt:room.endsAt,question:'如果今天多出一小时，你会做什么？',mySlot:id===room.users[0]?'A':'B',myReady:room.ready.has(id),extendRequested:room.extend.has(id),extensions:room.extensions,exchangeRequested:room.exchange.has(id),exchanged,messages:room.messages.filter(message=>message.seq>after),reason:room.reason||'',...(exchanged?{peer:publicProfile(other,id)}:{})}
}
function api(url,method,body,id) {
  if(url.pathname==='/api/cluster/owner')return {ownerGatewayId:'fixture',ownerEndpoint:'',gateways:[{gatewayId:'fixture'}]}
  if(url.pathname==='/api/auth/validate')return {valid:true}
  if(url.pathname==='/api/contacts/snapshot') {
    const friends=accounts.filter(other=>friendships.has([id,other].sort().join(':'))),version=friends.join(',')||'empty'
    return url.searchParams.get('version')===version?{version,unchanged:true}:{version,unchanged:false,users:friends.map(other=>({id:other,username:profiles.get(other).username})),groups:[]}
  }
  if(url.pathname==='/api/users/me')return {id,username:'Fixture '+id}
  if(url.pathname==='/api/users/online-status')return {}
  if(url.pathname==='/api/social/discovery')return {users:accounts.filter(other=>other!==id).map(other=>({...publicProfile(other,id),reason:'INTEREST'})),nextCursor:null}
  if(url.pathname.startsWith('/api/social/profiles/'))return publicProfile(Number(url.pathname.split('/').at(-1)),id)
  if(url.pathname==='/api/social/profile'){assert.equal('userId' in body,false,'editor must send only writable fields');const next={...profiles.get(id),...body};next.settings={...body,userId:id};profiles.set(id,next);return publicProfile(id,id)}
  if(url.pathname==='/api/social/friend-requests'){pendingRequests.set(Number(body.targetId),id);return true}
  if(url.pathname==='/api/social/blocks')return [...(blocks.get(id)||[])].map(other=>({id:other,username:profiles.get(other).username}))
  if(url.pathname.startsWith('/api/social/blocks/')){const other=Number(url.pathname.split('/').at(-1));const set=blocks.get(id)||new Set();method==='DELETE'?set.delete(other):set.add(other);blocks.set(id,set);return true}
  if(url.pathname==='/api/social/reports'){reports.push({id,...body});return true}
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
  const file=path.resolve(dist,pathname==='/app'?'app/index.html':pathname==='/'?'index.html':pathname.slice(1))
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
        sessionStorage.setItem('charroom_token',btoa('{}')+'.'+btoa(JSON.stringify({uid:String(id),exp:Math.floor(Date.now()/1000)+3600}))+'.fixture');localStorage.setItem('charroom_accountId',String(id));localStorage.setItem('locale','zh-CN')
        globalThis.WebTransport=class{constructor(){this.ready=Promise.resolve();this.closed=new Promise(()=>{});this.incomingBidirectionalStreams=new ReadableStream()}async createBidirectionalStream(){return {readable:new ReadableStream(),writable:new WritableStream({write(){}})}}close(){}}
      },id)
      await page.setRequestInterception(true)
      page.on('request',request=>{
        const url=new URL(request.url())
        if(url.origin===origin){request.continue();return}
        if(!url.pathname.startsWith('/api/')){request.abort();return}
        const body=request.postData()?JSON.parse(request.postData()):{}
        requests.push(url.pathname)
        const data=failures.has(url.pathname)?{__httpError:503}:api(url,request.method(),body,id)
        const respond=()=>request.respond({status:data?.__httpError||200,contentType:'application/json',headers:{'Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'*','Access-Control-Allow-Methods':'*'},body:JSON.stringify(data?.__httpError?{code:1500,message:'fixture lost acknowledgement'}:{code:0,data})})
        if(delays.has(url.pathname)){setTimeout(respond,delays.get(url.pathname))}else if(body.action==='send'&&delayNextSend){delayNextSend=false;setTimeout(respond,600)}else respond()
      })
      await page.goto(origin+'/app');await page.waitForSelector('[aria-label="发现与遇见"]');await page.click('[aria-label="发现与遇见"]');await page.waitForSelector('.social-person');pages.push(page)
    }
    const [a,b]=pages
    // Close/reopen keeps the applied filter, loaded page and reading position.
    await a.type('.social-search-row input','Fixture')
    await click(a,'.social-search-row button','搜索')
    await a.waitForFunction(()=>!document.querySelector('.social-main button.save-btn')?.disabled)
    const compact=await a.addStyleTag({content:'.social-hub{height:440px!important}'})
    await a.$eval('.social-main',element=>{element.scrollTop=60})
    const reading=await a.$eval('.social-main',element=>element.scrollTop)
    assert.ok(reading>0)
    const directoryReads=requests.filter(url=>url==='/api/social/discovery').length
    await a.click('.social-hub .close-settings');await a.waitForFunction(()=>!document.querySelector('.social-hub'))
    delays.set('/api/social/discovery',1500)
    await a.click('button.discovery-entry');await a.waitForSelector('.social-person')
    assert.equal(requests.filter(url=>url==='/api/social/discovery').length,directoryReads,'fresh discovery snapshot survives modal unmount')
    assert.equal(await a.$eval('.social-search-row input',element=>element.value),'Fixture')
    await a.waitForFunction(reading=>Math.abs(document.querySelector('.social-main').scrollTop-reading)<2,{},reading)
    await compact.evaluate(element=>element.remove());delays.delete('/api/social/discovery')
    if(process.env.SOCIAL_SCREENSHOT_DIR){
      fs.mkdirSync(process.env.SOCIAL_SCREENSHOT_DIR,{recursive:true})
      for(const theme of ['light','dark']){
        await a.evaluate(theme=>document.documentElement.setAttribute('data-theme',theme),theme)
        await a.screenshot({path:path.join(process.env.SOCIAL_SCREENSHOT_DIR,`discovery-${theme}.png`)})
      }
      await a.evaluate(()=>document.documentElement.setAttribute('data-theme','light'))
    }
    // A delayed target must not replace the current page with a different loading layout.
    delays.set('/api/social/daily',650)
    await click(a,'.social-nav button','今日一问')
    assert.equal(await a.$eval('.social-nav [aria-pressed="true"]',el=>el.textContent),'找人')
    assert.ok(await a.$('.social-person'))
    await a.waitForFunction(()=>document.querySelector('.social-nav [aria-pressed="true"]')?.textContent==='今日一问')
    await click(a,'.social-nav button','找人');await a.waitForSelector('.social-person')
    const dailyReads=requests.filter(url=>url==='/api/social/daily').length
    await click(a,'.social-nav button','今日一问')
    await a.waitForFunction(()=>document.querySelector('.social-nav [aria-pressed="true"]')?.textContent==='今日一问')
    assert.equal(requests.filter(url=>url==='/api/social/daily').length,dailyReads,'fresh page snapshot avoids a duplicate read')
    await a.type('.social-main textarea','尚未发布的本地草稿')
    await click(a,'.social-nav button','找人');await click(a,'.social-nav button','今日一问')
    assert.equal(await a.$eval('.social-main textarea',element=>element.value),'尚未发布的本地草稿')
    await a.click('.social-hub .close-settings');await a.waitForFunction(()=>!document.querySelector('.social-hub'))
    await a.reload();await a.waitForSelector('button.discovery-entry');await a.click('button.discovery-entry')
    await click(a,'.social-nav button','今日一问');await a.waitForSelector('.social-main textarea')
    assert.equal(await a.$eval('.social-main textarea',element=>element.value),'尚未发布的本地草稿','a browser reload restores only this account/day draft')
    await a.$eval('.social-main textarea',element=>{element.value='刷新前最后一笔';element.dispatchEvent(new Event('input',{bubbles:true}))})
    await a.reload();await a.waitForSelector('button.discovery-entry');await a.click('button.discovery-entry')
    await click(a,'.social-nav button','今日一问');await a.waitForSelector('.social-main textarea')
    assert.equal(await a.$eval('.social-main textarea',element=>element.value),'刷新前最后一笔','pagehide flushes edits made before the debounce deadline')
    await click(b,'.social-nav button','今日一问');await b.waitForSelector('.social-main textarea')
    assert.equal(await b.$eval('.social-main textarea',element=>element.value),'','the other account cannot see the draft')
    await click(b,'.social-nav button','找人')
    await a.$eval('.social-main textarea',element=>{element.value='';element.dispatchEvent(new Event('input',{bubbles:true}))})
    await click(a,'.social-nav button','找人');await a.waitForSelector('.social-person')
    delays.set('/api/social/profiles/91001',650)
    await click(a,'.social-nav button','我的名片');await click(a,'.social-nav button','找人')
    await a.waitForFunction(()=>!document.querySelector('.social-nav [aria-busy="true"]'))
    assert.ok(await a.$('.social-person'))
    delays.clear()
    failures.add('/api/social/encounter')
    await click(a,'.social-nav button','轻遇');await a.waitForSelector('.social-entry-error')
    assert.equal(await a.$eval('.social-nav [aria-pressed="true"]',el=>el.textContent),'找人')
    failures.clear()
    queued=91001
    await a.click('.social-entry-error button');await a.waitForSelector('.social-room')
    assert.equal(await a.$$eval('.social-room button',buttons=>buttons.some(button=>button.textContent.trim()==='开始匹配')),false,'prepared room enters with server state, without an idle screen')
    await click(a,'.social-nav button','找人');await a.waitForSelector('.social-person')
    queued=null
    assert.equal(await a.evaluate(()=>document.querySelector('.social-hub').contains(document.activeElement)),true,'dialog takes focus')
    await a.focus('.social-hub .close-settings');await a.keyboard.down('Shift');await a.keyboard.press('Tab');await a.keyboard.up('Shift')
    assert.equal(await a.evaluate(()=>document.querySelector('.social-hub').contains(document.activeElement)),true,'Tab remains inside dialog')
    assert.equal(await a.evaluate(()=>document.body.style.overflow),'hidden')
    // Android-like narrow viewport, then a visual viewport reduced by a keyboard.
    await a.setViewport({width:390,height:720})
    await click(a,'.social-nav button','我的名片');await a.waitForSelector('.social-profile h2')
    await a.evaluate(()=>{
      Object.defineProperty(window.visualViewport,'height',{configurable:true,value:410})
      Object.defineProperty(window.visualViewport,'offsetTop',{configurable:true,value:25})
      window.visualViewport.dispatchEvent(new Event('resize'))
    })
    await a.waitForFunction(()=>{const r=document.querySelector('.social-hub').getBoundingClientRect();return r.top>=24&&r.bottom<=436},{timeout:3000})
    assert.equal(await a.$('.social-profile-card button, .social-profile-card input, .social-profile-card select, .social-profile-card textarea'),null,'the name card is presentation only')
    assert.equal(await a.$('.social-detail-header'),null,'My card has no redundant back button above it')
    await a.evaluate(()=>{delete window.visualViewport.height;delete window.visualViewport.offsetTop;window.visualViewport.dispatchEvent(new Event('resize'))})
    for (const theme of ['light','dark']) {
      await a.setViewport({width:320,height:720})
      await a.evaluate(theme=>{document.documentElement.dataset.theme=theme},theme)
      assert.equal(await a.$eval('.social-profile-card',card=>card.scrollWidth<=card.clientWidth),true,'long card content fits a narrow screen')
      assert.equal(await a.$eval('.social-profile-content',content=>{
        const avatar=content.querySelector('.social-avatar').getBoundingClientRect(),title=content.querySelector('h2').getBoundingClientRect()
        return avatar.bottom<=title.top
      }),true,'avatar and name never overlap')
      if(process.env.SOCIAL_SCREENSHOT_DIR){fs.mkdirSync(process.env.SOCIAL_SCREENSHOT_DIR,{recursive:true});await a.screenshot({path:path.join(process.env.SOCIAL_SCREENSHOT_DIR,`profile-320-${theme}.png`)})}
    }
    await a.setViewport({width:390,height:720});await a.evaluate(()=>{document.documentElement.dataset.theme='light'})
    // Editing is a separate surface; cancel preserves saved data, privacy preview remains accurate.
    await click(a,'.social-profile-primary button','编辑资料');await a.waitForSelector('.social-profile-editor')
    assert.equal(await a.$('.social-profile-card'),null)
    await a.$eval('.social-profile-editor input',input=>{input.value='Unsaved fixture';input.dispatchEvent(new Event('input',{bubbles:true}))})
    await click(a,'.social-profile-editor button','取消');await a.waitForSelector('.social-profile-card')
    assert.equal(await text(a,'.social-profile-card h2'),'Fixture 91001')
    await click(a,'.social-nav button','找人')
    delays.set('/api/social/profiles/91001',1500)
    await click(a,'.social-nav button','我的名片');await a.waitForSelector('.social-profile-card',{timeout:1000})
    assert.equal(await text(a,'.social-profile-card h2'),'Fixture 91001','own cached profile is visible while the network is delayed')
    assert.equal(await a.$eval('.social-profile-primary button',element=>element.disabled),true,'cached settings wait for authoritative refresh before editing')
    await a.waitForFunction(()=>document.querySelector('.social-profile-primary button')?.textContent==='编辑资料'&&!document.querySelector('.social-profile-primary button')?.disabled)
    delays.delete('/api/social/profiles/91001')
    await click(a,'.social-profile-primary button','编辑资料');await a.waitForSelector('.social-profile-editor')
    await a.$$eval('.social-profile-editor select',selects=>{selects[1].value='FRIENDS';selects[1].dispatchEvent(new Event('change',{bubbles:true}))})
    // Simulate a short visible viewport with a focused field near the bottom of a long editor.
    await a.$$eval('.social-profile-editor textarea',fields=>fields.at(-1).focus())
    await a.evaluate(()=>{Object.defineProperty(window.visualViewport,'height',{configurable:true,value:370});window.visualViewport.dispatchEvent(new Event('resize'))})
    await a.waitForFunction(()=>{const r=document.querySelector('.social-hub').getBoundingClientRect();return r.bottom<=371})
    await click(a,'.social-profile-editor button','保存');await a.waitForSelector('.social-profile-card')
    assert.equal(profiles.get(91001).settings.visibility,'FRIENDS')
    await a.evaluate(()=>{delete window.visualViewport.height;window.visualViewport.dispatchEvent(new Event('resize'))})
    await a.focus('.social-profile-primary button');await a.keyboard.press('Tab')
    assert.equal(await a.evaluate(()=>document.activeElement.tagName),'SUMMARY','preview disclosure remains in keyboard focus loop')
    await a.keyboard.press('Enter');await a.select('.social-profile-secondary select','stranger')
    assert.equal(await a.$('.social-profile-card .social-intent'),null,'stranger preview respects FRIENDS visibility')
    assert.equal(await a.$('.social-profile-city'),null,'city metadata respects the same privacy preview')
    await a.select('.social-profile-secondary select','friend')
    assert.ok(await a.$('.social-profile-card .social-intent'))
    await a.select('.social-profile-secondary select','self')
    await a.setViewport({width:1180,height:900});await a.evaluate(()=>document.querySelector('.social-nav button').click());await a.waitForSelector('.social-person')
    await click(a,'.social-person button','看资料');await a.waitForSelector('.social-profile h2')
    await click(a,'.social-profile button','加为好友');await a.type('.social-profile textarea','A fixture introduction');await click(a,'.social-profile button','发送好友申请')
    await a.waitForFunction(()=>[...document.querySelectorAll('.social-profile button')].some(button=>button.textContent.includes('已申请')&&button.disabled))
    for(const page of pages){await click(page,'.social-nav button','轻遇');await click(page,'.social-room button','开始匹配')}
    for(const page of pages){await page.waitForFunction(()=>document.querySelector('.social-room')?.textContent.includes('已遇见'));await click(page,'.social-room button','我准备好了')}
    for(const page of pages)await page.waitForFunction(()=>document.querySelector('.social-room')?.textContent.includes('正在轻聊'))
    await a.type('.social-room textarea','候选确认')
    await a.$eval('.social-room textarea',element=>element.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',isComposing:true,bubbles:true,cancelable:true})))
    assert.equal(sends.length,0)
    await a.$eval('.social-room textarea',element=>{element.value='';element.dispatchEvent(new Event('input',{bubbles:true}))})
    await a.type('.social-room textarea','fixture message after a lost acknowledgement');await click(a,'.social-room button','发送')
    await a.waitForFunction(()=>document.querySelector('.social-room [role="alert"]')?.textContent.includes('fixture'))
    await click(a,'.social-room button','发送');await b.waitForFunction(()=>document.querySelector('.social-room-messages')?.textContent.includes('fixture message'))
    assert.equal(sends.length,2);assert.equal(sends[0],sends[1]);assert.equal(room.messages.length,1)
    delayNextSend=true
    await a.type('.social-room textarea','submitted fixture');await click(a,'.social-room button','发送')
    await a.$eval('.social-room textarea',element=>{element.value='下一条消息的草稿';element.dispatchEvent(new Event('input',{bubbles:true}))})
    await a.waitForFunction(()=>[...document.querySelectorAll('.social-room button')].some(button=>button.textContent.trim()==='发送'&&!button.disabled))
    assert.equal(await a.$eval('.social-room textarea',element=>element.value),'下一条消息的草稿','late send response must preserve new input')
    for(let index=0;index<30;index++)room.messages.push({seq:room.messages.length+1,author:'B',content:'Scrollable room fixture '+index,createdAt:Date.now(),messageId:'room-scroll-'+index})
    await a.waitForFunction(()=>document.querySelector('.social-room-messages')?.textContent.includes('Scrollable room fixture 29'))
    assert.equal(await a.$eval('.social-room-messages',element=>element.scrollHeight>element.clientHeight),true,'room history has its own bounded scroll area')
    if(process.env.SOCIAL_SCREENSHOT_DIR){fs.mkdirSync(process.env.SOCIAL_SCREENSHOT_DIR,{recursive:true});await a.screenshot({path:path.join(process.env.SOCIAL_SCREENSHOT_DIR,'encounter-desktop.png')})}
    await click(a,'.social-room button','交换名片');assert.equal(await b.$('.social-room .save-btn + .save-btn'),null)
    await click(b,'.social-room button','交换名片');await a.waitForFunction(()=>document.querySelector('.social-room')?.textContent.includes('看资料'))
    await click(a,'.social-room button','再聊五分钟');await click(b,'.social-room button','再聊五分钟')
    for(let tries=0;room.extensions!==1&&tries<100;tries++)await new Promise(resolve=>setTimeout(resolve,20))
    assert.equal(room.extensions,1)
    await click(b,'.social-room button','结束并离开');await a.waitForFunction(()=>document.querySelector('.social-room')?.textContent.includes('本次相遇已结束'))
    await click(a,'.social-nav button','今日一问');await a.waitForSelector('.social-main textarea');await a.type('.social-main textarea','<img src=x onerror=alert(1)> 只是文字')
    delays.set('/api/social/daily',650)
    await click(a,'.social-main button','保存回答（留空可删除）')
    await a.$eval('.social-main textarea',element=>{element.value='发布期间继续写的新草稿';element.dispatchEvent(new Event('input',{bubbles:true}))})
    await a.waitForFunction(()=>document.querySelector('.social-answer')?.textContent.includes('<img'))
    assert.equal(await a.$eval('.social-main textarea',element=>element.value),'发布期间继续写的新草稿','publishing an older revision does not erase new edits')
    delays.delete('/api/social/daily')
    assert.equal(await a.$('.social-answer img'),null)
    await click(a,'.social-nav button','我的名片');await click(a,'.social-profile button','编辑资料');await a.waitForSelector('.social-profile form')
    const editInputs=await a.$$('.social-profile form input');await editInputs[0].click({clickCount:3});await editInputs[0].type('Updated fixture')
    await click(a,'.social-profile button','保存');await a.waitForFunction(()=>document.querySelector('.social-profile h2')?.textContent==='Updated fixture')
    assert.ok((await text(a,'.social-profile [role="status"]')).includes('保存'))
    // Secondary actions are outside the card but keep their API payloads and relationship states.
    await a.evaluate(()=>document.querySelector('.social-nav button').click());await a.waitForSelector('.social-person')
    await click(a,'.social-person button','看资料');await a.waitForSelector('.social-profile-card')
    await a.click('.social-profile-secondary summary');await click(a,'.social-profile button','举报资料')
    await a.type('.social-profile-secondary textarea','Fixture report reason');await click(a,'.social-profile-secondary button','提交')
    await a.waitForFunction(()=>document.querySelector('.social-profile [role="status"]')?.textContent.includes('举报已提交'))
    assert.equal(reports.at(-1).targetId,91002);assert.equal(reports.at(-1).reason,'Fixture report reason')
    await click(a,'.social-profile button','屏蔽此人');await a.waitForFunction(()=>!document.querySelector('.social-detail'))
    assert.ok(blocks.get(91001).has(91002))
    await click(a,'.social-nav button','我的名片');await a.waitForSelector('.social-profile-card')
    await click(a,'.social-profile button','解除屏蔽');await a.waitForFunction(()=>![...document.querySelectorAll('.social-profile button')].some(button=>button.textContent.trim()==='解除屏蔽'));assert.equal(blocks.get(91001).size,0)
    friendships.add([91001,91002].sort().join(':'))
    await a.setViewport({width:390,height:720});await a.evaluate(()=>document.querySelector('.social-nav button').click());await a.waitForSelector('.social-person')
    await click(a,'.social-person button','看资料');await a.waitForSelector('.social-profile-card')
    await click(a,'.social-profile-primary button','发消息');await a.waitForFunction(()=>!document.querySelector('.social-hub'))
    await a.waitForSelector('.mobile-header .back-btn');assert.equal(await text(a,'.mobile-header .chat-title'),'Fixture 91002')
    await a.click('.mobile-header .back-btn');await a.waitForSelector('button.discovery-entry');await a.click('button.discovery-entry')
    await click(a,'.social-nav button','我的名片');await a.waitForSelector('.social-profile-card')
    for(const theme of ['light','dark'])for(const width of [1000,390,320]){
      await a.setViewport({width,height:900});await a.evaluate(theme=>document.documentElement.setAttribute('data-theme',theme),theme)
      await a.waitForFunction(()=>{const r=document.querySelector('.social-hub').getBoundingClientRect();return r.top>=-1&&r.bottom<=window.visualViewport.height+1})
      assert.equal(await a.$('.social-profile-card button, .social-profile-card input, .social-profile-card select'),null)
      const overflow=await a.$eval('.social-hub',element=>element.scrollWidth>element.clientWidth+1);assert.equal(overflow,false,`layout ${width}`)
      if(process.env.SOCIAL_SCREENSHOT_DIR){fs.mkdirSync(process.env.SOCIAL_SCREENSHOT_DIR,{recursive:true});await a.screenshot({path:path.join(process.env.SOCIAL_SCREENSHOT_DIR,`profile-${theme}-${width}.png`)})}
    }
    // Late long content must scroll within the panel, never grow the outer dialog.
    profiles.get(91001).bio='Long fixture biography.\n'.repeat(45)
    await a.evaluate(()=>document.querySelector('.social-nav button').click());await a.waitForSelector('.social-person')
    await click(a,'.social-nav button','我的名片');await a.waitForFunction(()=>document.querySelector('.social-profile-card')?.textContent.includes('Long fixture biography.'))
    assert.equal(await a.$eval('.social-detail',panel=>panel.scrollHeight>panel.clientHeight),true)
    assert.equal(await a.$eval('.social-hub',panel=>panel.getBoundingClientRect().bottom<=window.visualViewport.height+1),true)
    await a.$eval('.social-detail',panel=>{panel.scrollTop=panel.scrollHeight})
    const edit=await a.$('.social-profile-primary button');await edit.click();await a.waitForSelector('.social-profile-editor')
    assert.equal(await a.$eval('.social-detail',panel=>panel.scrollTop),0,'entering edit starts at the first field')
    await click(a,'.social-profile-editor button','取消');await a.waitForSelector('.social-profile-card')
    await a.keyboard.press('Escape');await a.waitForFunction(()=>!document.querySelector('.social-hub'))
    assert.equal(await a.evaluate(()=>document.body.style.overflow),'')
    assert.equal(await a.evaluate(()=>document.activeElement?.getAttribute('aria-label')),'发现与遇见','dialog restores trigger focus')
    assert.deepEqual(errors,[])
    console.log('PASS: two accounts, social permissions/encounter lifecycle, cache across modal remounts, filters/reading position, account-owned drafts after reload, publication/edit race, cached own profile with verified editing, keyboard/dark/mobile layout.')
  } finally {await browser.close();await new Promise(resolve=>server.close(resolve))}
}
main().catch(failure=>{console.error(failure);server.close();process.exitCode=1})
