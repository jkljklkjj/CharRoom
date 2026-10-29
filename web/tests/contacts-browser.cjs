// Built-app regression. All API requests and WebTransport traffic use local fixtures.
// Run: node tests/contacts-browser.cjs <Chromium executable>
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict')
const puppeteer = require('puppeteer-core'), protobuf = require('protobufjs')
const dist = path.resolve(__dirname, '../dist')
const root = protobuf.loadSync(path.resolve(__dirname, '../public/proto/message.proto'))
const Wrapper = root.lookupType('com.chatlite.proto.MessageWrapper')
const types = root.lookupEnum('com.chatlite.proto.MessageWrapperType').values
const mime = { '.js': 'application/javascript', '.css': 'text/css', '.html': 'text/html', '.proto': 'text/plain', '.png': 'image/png', '.svg': 'image/svg+xml', '.ico': 'image/x-icon', '.webmanifest': 'application/manifest+json' }
const server = http.createServer((request, response) => {
  const pathname = new URL(request.url, 'http://localhost').pathname
  const file = path.resolve(dist, pathname === '/app' ? 'app/index.html' : pathname === '/' ? 'index.html' : pathname.slice(1))
  if (!file.startsWith(dist + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { response.writeHead(404).end(); return }
  response.writeHead(200, { 'Content-Type': mime[path.extname(file)] || 'application/octet-stream' }); response.end(fs.readFileSync(file))
})

async function main() {
  const executablePath = process.argv[2] || process.env.BROWSER_EXECUTABLE
  assert.ok(executablePath, 'Supply a Chromium executable')
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const origin = `http://127.0.0.1:${server.address().port}`
  const browser = await puppeteer.launch({ executablePath, headless: 'new', args: ['--no-proxy-server'] })
  const errors = []
  function framed(bytes) { const frame = Buffer.alloc(4 + bytes.length); frame.writeUInt32BE(bytes.length); Buffer.from(bytes).copy(frame, 4); return Array.from(frame) }
  async function fixturePage(empty = false, locale = 'zh-CN', large = false, collision = false) {
    const login = framed(Wrapper.encode(Wrapper.fromObject({ type: types.RESPONSE, response: { success: true, clientId: '91001' } })).finish())
    const context = await browser.createIncognitoBrowserContext(), page = await context.newPage()
    let directoryDelay=0,directoryRequests=0,directoryPending=0
    const searches = []
    page.searches = searches
    page.setDirectoryDelay=value=>{directoryDelay=value}
    page.directoryRequests=()=>directoryRequests
    page.directoryPending=()=>directoryPending
    page.setDefaultTimeout(12000)
    await page.setViewport({ width: 1180, height: 850 }); await page.setBypassServiceWorker(true)
    page.on('pageerror', error => errors.push(error.message))
    await page.evaluateOnNewDocument(({ locale, login }) => {
      sessionStorage.setItem('charroom_token', btoa('{}') + '.' + btoa(JSON.stringify({uid:'91001',exp:Math.floor(Date.now()/1000)+3600})) + '.fixture')
      localStorage.setItem('charroom_accountId', '91001'); localStorage.setItem('locale', locale)
      globalThis.fixtureWrites = []; globalThis.fixtureReaders = []
      globalThis.fixtureCollatorComparisons = 0
      Intl.Collator = new Proxy(Intl.Collator, { construct(Target, args) {
        const collator = new Target(...args), compare = collator.compare
        Object.defineProperty(collator, 'compare', { value: (a, b) => {
          globalThis.fixtureCollatorComparisons++; return compare(a, b)
        } })
        return collator
      } })
      globalThis.WebTransport = class {
        constructor() { this.ready = Promise.resolve(); this.closed = new Promise(() => {}); this.incomingBidirectionalStreams = new ReadableStream() }
        async createBidirectionalStream() { return { readable: new ReadableStream({ start(controller) { globalThis.fixtureReaders.push(controller); if (globalThis.fixtureReaders.length === 1) queueMicrotask(() => controller.enqueue(new Uint8Array(login))) } }), writable: new WritableStream({ write(bytes) { globalThis.fixtureWrites.push(Array.from(bytes)) } }) } }
        close() {}
      }
    }, { locale, login })
    await page.setRequestInterception(true)
    page.on('request', request => {
      const url = new URL(request.url())
      if (url.origin === origin) { request.continue(); return }
      if (!url.pathname.startsWith('/api/')) { request.abort(); return }
      let data = []
      if (url.pathname === '/api/users/search' && request.method() === 'GET') {
        const keyword = url.searchParams.get('q')
        searches.push(keyword)
        if (keyword === 'server-cooldown') {
          void request.respond({status:429,contentType:'application/json',headers:{'Access-Control-Allow-Origin':'*'},body:JSON.stringify({code:5001,data:{retryAfterSeconds:2}})})
          return
        }
        if (keyword.startsWith('Remote fixture')) data = [{ id: 91020, username: keyword, relation: 'NONE' }]
      }
      if (url.pathname === '/api/cluster/owner') data = { ownerGatewayId: 'fixture', ownerEndpoint: '', gateways: [{ gatewayId: 'fixture' }] }
      if (url.pathname === '/api/auth/validate') data = { valid: true }
      if (url.pathname === '/api/users/me') data = { id: 91001, username: 'Fixture self' }
      if (url.pathname === '/api/users/online-status') data = {}
      if (url.pathname === '/api/groups') data = empty ? [] : [{ id: 91010, name: '周末散步的人' }]
      if (url.pathname === '/api/friends') data = [{ id: 0, username: 'AI Assistant' }, { id: 91001, username: 'Fixture self' }, ...(empty ? [] : [{ id: 91003, username: 'Zoe', signature: '一起看一场日落。' }, { id: 91002, username: 'Alice', signature: '慢慢走，也好好聊。' }])]
      if(url.pathname==='/api/contacts/snapshot') {
        const version=empty?'empty':'full'
        data=url.searchParams.get('version')===version?{version,unchanged:true}:{version,unchanged:false,
          users:[{id:0,username:'AI Assistant'},{id:91001,username:'Fixture self'},...(empty?[]:[{id:91003,username:'Zoe',signature:'一起看一场日落。'},{id:91002,username:'Alice',signature:'慢慢走，也好好聊。'}]),...(large?Array.from({length:5000},(_,i)=>({id:100000+i,username:`Contact ${String(i).padStart(5,'0')}`})):[]),...(collision?[{id:91010,username:'Same number friend'}]:[])],
          groups:empty?[]:[{id:91010,name:'周末散步的人'}]}
      }
      const response={ status: 200, contentType: 'application/json', headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*', 'Access-Control-Allow-Methods': '*' }, body: JSON.stringify({ code: 0, data }) }
      if(url.pathname==='/api/contacts/snapshot' && request.method()==='GET') {
        directoryRequests++
        if(directoryDelay){directoryPending++;setTimeout(()=>{directoryPending--;void request.respond(response)},directoryDelay);return}
      }
      void request.respond(response)
    })
    await page.goto(origin + '/app'); await page.waitForSelector('#sidebar-panel[aria-busy="false"]')
    if(!empty)await page.waitForSelector('[data-contact-id="user:91002"]')
    return page
  }
  async function screenshot(page, name) {
    if (!process.env.CONTACTS_SCREENSHOT_DIR) return
    fs.mkdirSync(process.env.CONTACTS_SCREENSHOT_DIR, { recursive: true })
    await page.screenshot({ path: path.join(process.env.CONTACTS_SCREENSHOT_DIR, name + '.png') })
  }
  try {
    const page = await fixturePage()
    await page.waitForFunction(() => globalThis.fixtureReaders.length > 0)
    const incoming = Wrapper.encode(Wrapper.fromObject({ type: types.CHAT, chat: { userId: 91003, targetClientId: 91001, content: 'Fixture unread message', timestamp: Date.now(), messageId: 'contacts-unread-1' } })).finish()
    await page.evaluate(bytes => globalThis.fixtureReaders[0].enqueue(new Uint8Array(bytes)), framed(incoming))
    await page.waitForSelector('[data-contact-id="user:91003"] .unread-badge')
    await page.focus('#sidebar-tab-messages'); await page.keyboard.press('ArrowRight')
    await page.waitForSelector('#sidebar-tab-contacts[aria-selected="true"]')
    assert.equal(await page.evaluate(() => document.activeElement.id), 'sidebar-tab-contacts')
    assert.deepEqual(await page.$$eval('.user-row', rows => rows.map(row => row.dataset.contactId)), ['user:91002', 'user:91003', 'group:91010'])
    assert.equal(await page.$('.unread-badge'), null)
    await page.keyboard.press('Home')
    await page.waitForSelector('[data-contact-id="user:91003"] .unread-badge')
    assert.equal(await page.$eval('[data-contact-id="user:91003"] .unread-badge', element => element.textContent), '1', 'tab change must preserve unread count')
    await page.click('[data-contact-id="user:91002"]'); await page.waitForSelector('.composer textarea')
    await page.type('.composer textarea', 'Alice draft')
    await page.click('[data-contact-id="group:91010"]')
    assert.equal(await page.$eval('.composer textarea', el => el.value), '', 'a group cannot inherit a private draft')
    await page.type('.composer textarea', 'Group draft')
    await page.click('[data-contact-id="user:91002"]')
    assert.equal(await page.$eval('.composer textarea', el => el.value), 'Alice draft')
    await page.click('[data-contact-id="group:91010"]')
    assert.equal(await page.$eval('.composer textarea', el => el.value), 'Group draft')
    await page.focus('.composer textarea'); await page.keyboard.down('Control'); await page.keyboard.press('A'); await page.keyboard.up('Control'); await page.keyboard.press('Backspace')
    await page.click('[data-contact-id="user:91002"]')
    await page.click('#sidebar-tab-contacts'); await page.type('.search-bar input', ' ALI ')
    await page.waitForFunction(() => document.querySelectorAll('.user-row').length === 1)
    assert.equal(await page.$eval('.user-row', element => element.dataset.contactId), 'user:91002')
    const mobileViewport = await page.target().createCDPSession()
    await mobileViewport.send('Emulation.setSafeAreaInsetsOverride', {insets:{top:64,bottom:24,left:0,right:0}})
    await page.setViewport({ width: 390, height: 844 })
    await page.waitForSelector('.mobile-view [data-contact-id="user:91002"]')
    await page.waitForSelector('#sidebar-tab-contacts[aria-selected="true"]')
    assert.equal(await page.$eval('.sidebar', element => element.getBoundingClientRect().top), 0, 'sidebar starts at content top without a browser-bar spacer')
    await page.click('[data-contact-id="user:91002"]'); await page.waitForSelector('.mobile-header .back-btn')
    assert.equal(await page.$eval('.mobile-header', element => element.getBoundingClientRect().top), 0, 'chat header shares the same top boundary')
    assert.equal(await page.$eval('.composer textarea', el => el.value), 'Alice draft', 'responsive remount preserves the conversation draft')
    await page.focus('.composer textarea'); await page.keyboard.down('Control'); await page.keyboard.press('A'); await page.keyboard.up('Control'); await page.keyboard.press('Backspace')
    await page.click('.mobile-header .back-btn'); await page.waitForSelector('#sidebar-tab-contacts[aria-selected="true"]')
    assert.equal(await page.$eval('.search-bar input', element => element.value), ' ALI ')
    await page.click('.clear-search')
    await page.focus('.search-bar input')
    await page.$eval('.search-bar input', input => { input.value = 'Remote fixture'; input.dispatchEvent(new Event('input', { bubbles: true })) })
    await page.keyboard.press('Enter')
    assert.equal(await page.$eval('.search-bar input', input => document.activeElement === input), false, 'mobile Search submits and dismisses the keyboard')
    await page.waitForSelector('[data-contact-id="user:91020"]')
    assert.equal(page.searches.filter(query => query === 'Remote fixture').length, 1, 'submit cancels the debounce instead of issuing two searches')
    assert.equal(new URL(page.url()).pathname, '/app', 'search must not navigate away or reload')
    await page.click('.clear-search'); await page.type('.search-bar input', 'not-a-contact')
    await page.waitForSelector('#sidebar-panel [role="status"]')
    assert.equal(await page.$('.user-row'), null)
    await page.click('.clear-search'); await page.waitForSelector('[data-contact-id="group:91010"]'); await page.click('[data-contact-id="group:91010"]')
    await page.waitForSelector('.composer textarea'); await page.type('.composer textarea', 'Fixture group send')
    const beforeSend = await page.evaluate(() => globalThis.fixtureWrites.length)
    await page.keyboard.press('Enter')
    await page.waitForFunction(() => document.querySelector('.messages')?.textContent.includes('Fixture group send'))
    await page.waitForFunction(count => globalThis.fixtureWrites.length > count, {}, beforeSend)
    const written = await page.evaluate(() => globalThis.fixtureWrites)
    const groupSend = written.map(bytes => Wrapper.decode(Uint8Array.from(bytes).subarray(4))).find(message => message.type === types.GROUP_CHAT)
    assert.ok(groupSend, 'group entry must send GROUP_CHAT')
    assert.equal(groupSend.groupChat.targetClientId.toString(), '91010', 'typed conversation key must resolve to the raw wire ID')
    assert.equal(await page.$eval('.mobile-header .chat-title', element => element.textContent), '周末散步的人')
    await page.click('.mobile-header .back-btn'); await page.waitForSelector('#sidebar-tab-contacts[aria-selected="true"]')
    for (const theme of ['light', 'dark']) for (const width of [1180, 320]) {
      await page.setViewport({ width, height: 850 })
      await page.evaluate(theme => document.documentElement.setAttribute('data-theme', theme), theme)
      await page.waitForSelector('#sidebar-tab-contacts[aria-selected="true"]')
      assert.equal(await page.$eval('#sidebar-tab-contacts', element => getComputedStyle(element).backgroundColor), theme === 'dark' ? 'rgb(72, 49, 34)' : 'rgb(255, 234, 216)', 'selected tab uses the native paper palette')
      assert.equal(await page.$eval('.sidebar', element => element.scrollWidth > element.clientWidth + 1), false, `${theme}/${width} must fit`)
      await screenshot(page, `contacts-${theme}-${width}`)
    }
    await page.click('.settings-btn');await page.waitForSelector('.settings-dialog')
    assert.equal(await page.$('.transport-info'),null,'daily settings do not expose transport internals')
    assert.ok(await page.$eval('.theme-options button.active',element=>{
      const style=getComputedStyle(element)
      const luminance=color=>color.match(/[\d.]+/g).slice(0,3).map(Number).map(value=>value/255).map(value=>value<=.04045?value/12.92:((value+.055)/1.055)**2.4).reduce((sum,value,index)=>sum+value*[.2126,.7152,.0722][index],0)
      const a=luminance(style.color),b=luminance(style.backgroundColor)
      return (Math.max(a,b)+.05)/(Math.min(a,b)+.05)>=4.5
    }),'selected settings control retains readable contrast in dark mode')
    await page.keyboard.press('Escape');await page.waitForSelector('.settings-dialog',{hidden:true})
    for (const locale of ['en', 'ja']) {
      const translated = await fixturePage(false, locale)
      await translated.click('#sidebar-tab-contacts')
      assert.equal(await translated.$eval('#sidebar-tab-contacts', element => element.textContent), locale === 'en' ? 'Contacts' : '連絡先')
      await translated.close()
    }
    const empty = await fixturePage(true)
    await empty.click('#sidebar-tab-contacts'); await empty.waitForSelector('#sidebar-panel [role="status"]')
    assert.equal(await empty.$('.user-row'), null, 'assistant/self alone is an empty directory')
    assert.ok((await empty.$eval('#sidebar-panel', element => element.textContent)).includes('还没有联系人'))
    await page.setViewport({width:1180,height:850})
    const requestsBeforeReload=page.directoryRequests()
    page.setDirectoryDelay(2000)
    await page.reload({waitUntil:'domcontentloaded'})
    await page.waitForSelector('[data-contact-id="user:91002"]')
    assert.ok(page.directoryPending()>0,'cached directory must render before the delayed validation finishes')
    assert.equal(await page.$('.skeleton-list'),null,'warm directory must not flash the first-load skeleton')
    assert.equal(page.directoryRequests()-requestsBeforeReload,1,'login and sidebar must share one directory validation')
    page.setDirectoryDelay(0)
    await page.waitForFunction(()=>!document.querySelector('.directory-feedback'))
    // Keep the real Worker/outbox/ACK handling; withhold only the remote ACK.
    const delayed=await fixturePage()
    await delayed.click('[data-contact-id="user:91002"]');await delayed.waitForSelector('.composer textarea')
    await delayed.type('.composer textarea','quiet ACK fixture');await delayed.click('.composer .send')
    await delayed.waitForFunction(()=>document.querySelector('.msg.me .text-content')?.textContent==='quiet ACK fixture')
    await new Promise(resolve=>setTimeout(resolve,3000))
    assert.equal(await delayed.$('.send-indicator'),null,'normal sends remain quiet for the first four seconds')
    await delayed.waitForSelector('.send-spinner',{timeout:4000})
    assert.equal(await delayed.$eval('.send-spinner',element=>Boolean(element.closest('.bubble-col'))),false,'spinner sits beside the bubble')
    await delayed.waitForSelector('.send-failure',{timeout:8000})
    assert.equal(await delayed.$eval('.send-failure',element=>element.textContent),'!')
    assert.equal(await delayed.$('.send-spinner'),null,'failure replaces the spinner')
    await delayed.click('.send-failure');await delayed.waitForSelector('.send-failure',{hidden:true})
    assert.equal(await delayed.$('.send-spinner'),null,'manual retry starts a new quiet period')
    await delayed.waitForSelector('.send-spinner',{timeout:6000})
    await delayed.waitForSelector('.send-failure',{timeout:8000})
    const sends=(await delayed.evaluate(()=>globalThis.fixtureWrites)).map(bytes=>Wrapper.decode(Uint8Array.from(bytes).subarray(4))).filter(message=>message.type===types.CHAT)
    assert.ok(sends.length>=2);assert.equal(sends[0].chat.messageId,sends[1].chat.messageId,'retry keeps the same identity')
    assert.ok(await delayed.evaluate(async()=>{
      const db=await new Promise(resolve=>{const request=indexedDB.open('chatlite_offline');request.onsuccess=()=>resolve(request.result)})
      try{return await new Promise(resolve=>{const request=db.transaction('account_outbox').objectStore('account_outbox').count();request.onsuccess=()=>resolve(request.result>0)})}finally{db.close()}
    }),'a display timeout must retain the durable outbox record')
    const success=framed(Wrapper.encode(Wrapper.fromObject({type:types.ACK,ack:{messageId:sends[0].chat.messageId,serverMessageId:'9007199254740998',success:true,conversationId:'user:91001:91002',seqId:'1'}})).finish())
    await delayed.evaluate(bytes=>globalThis.fixtureReaders[0].enqueue(new Uint8Array(bytes)),success)
    await delayed.waitForSelector('.send-indicator',{hidden:true})
    assert.equal(await delayed.$('.status.sent'),null,'a successful ACK has no persistent success marker')
    await delayed.close()
    const large=await fixturePage(false,'zh-CN',true)
    await large.click('#sidebar-tab-contacts')
    assert.ok(await large.$$eval('.virtual-contacts .user-row',rows=>rows.length<35),'5000 contacts must not create 5000 DOM rows')
    await large.$eval('#sidebar-panel',el=>{el.scrollTop=el.scrollHeight;el.dispatchEvent(new Event('scroll'))})
    await large.waitForSelector('[data-contact-id="group:91010"]')
    await large.focus('[data-contact-id="group:91010"]');await large.keyboard.press('Home')
    await large.waitForFunction(()=>document.activeElement?.getAttribute('data-contact-id')==='user:91002')
    await large.keyboard.press('End')
    await large.waitForFunction(()=>document.activeElement?.getAttribute('data-contact-id')==='group:91010')
    const comparisonsBeforeSearch=await large.evaluate(()=>globalThis.fixtureCollatorComparisons)
    assert.ok(comparisonsBeforeSearch>0)
    await large.type('.search-bar input','104999');await large.waitForSelector('[data-contact-id="user:104999"]')
    assert.equal(await large.evaluate(()=>globalThis.fixtureCollatorComparisons),comparisonsBeforeSearch,'Typing must filter the cached directory without sorting 5000 contacts again')
    assert.equal(await large.$$eval('.virtual-contacts .user-row',rows=>rows.length),1)
    await large.click('[data-contact-id="user:104999"]');await large.waitForSelector('.composer textarea')
    await large.close()
    const collision=await fixturePage(false,'zh-CN',false,true)
    await collision.click('#sidebar-tab-contacts')
    await collision.click('[data-contact-id="user:91010"]');await collision.waitForSelector('.composer textarea')
    await collision.type('.composer textarea','private same ID')
    await collision.click('[data-contact-id="group:91010"]')
    assert.equal(await collision.$eval('.composer textarea',el=>el.value),'')
    await collision.type('.composer textarea','group same ID')
    await collision.click('[data-contact-id="user:91010"]')
    await collision.click('#sidebar-tab-messages')
    assert.equal(await collision.$eval('.composer textarea',el=>el.value),'private same ID')
    const privateWrites=await collision.evaluate(()=>globalThis.fixtureWrites.length)
    await collision.focus('.composer textarea');await collision.keyboard.press('Enter')
    await collision.waitForFunction(()=>document.querySelector('.messages')?.textContent.includes('private same ID'))
    await collision.waitForFunction(count=>globalThis.fixtureWrites.length>count,{},privateWrites)
    await collision.click('[data-contact-id="group:91010"]')
    assert.equal(await collision.$eval('.composer textarea',el=>el.value),'group same ID')
    const groupWrites=await collision.evaluate(()=>globalThis.fixtureWrites.length)
    await collision.focus('.composer textarea');await collision.keyboard.press('Enter')
    await collision.waitForFunction(()=>document.querySelector('.messages')?.textContent.includes('group same ID'))
    await collision.waitForFunction(count=>globalThis.fixtureWrites.length>count,{},groupWrites)
    const sameIdWrites=(await collision.evaluate(()=>globalThis.fixtureWrites)).map(bytes=>Wrapper.decode(Uint8Array.from(bytes).subarray(4)))
    assert.equal(sameIdWrites.find(message=>message.type===types.CHAT)?.chat.targetClientId.toString(),'91010')
    assert.equal(sameIdWrites.find(message=>message.type===types.GROUP_CHAT)?.groupChat.targetClientId.toString(),'91010')
    await collision.click('[data-contact-id="user:91010"]')
    const groupIncoming=framed(Wrapper.encode(Wrapper.fromObject({type:types.GROUP_CHAT,groupChat:{targetClientId:91010,userId:91003,content:'isolated group delivery',timestamp:Date.now(),messageId:'same-id-group-incoming'}})).finish())
    await collision.evaluate(bytes=>globalThis.fixtureReaders[0].enqueue(new Uint8Array(bytes)),groupIncoming)
    await collision.waitForSelector('[data-contact-id="group:91010"] .unread-badge')
    assert.ok(!await collision.$eval('.messages',el=>el.textContent.includes('isolated group delivery')))
    await collision.click('[data-contact-id="group:91010"]')
    await collision.waitForFunction(()=>document.querySelector('.messages')?.textContent.includes('isolated group delivery'))
    await collision.click('[data-contact-id="user:91010"]')
    await collision.waitForFunction(()=>document.querySelector('.messages')?.textContent.includes('private same ID'))
    assert.ok(!await collision.$eval('.messages',el=>el.textContent.includes('isolated group delivery')))
    await collision.close()
    const searchPage = await fixturePage(true)
    await searchPage.setViewport({width:390,height:844})
    await searchPage.waitForSelector('.mobile-view #sidebar-tab-contacts')
    await searchPage.click('#sidebar-tab-contacts')
    async function submitKeyword(target, keyword) {
      await target.focus('.search-bar input')
      await target.$eval('.search-bar input', (input, value) => { input.value=value; input.dispatchEvent(new Event('input',{bubbles:true})) }, keyword)
      await target.keyboard.press('Enter')
    }
    for (let i=0;i<8;i++) {
      await submitKeyword(searchPage, `Remote fixture ${i}`)
      await searchPage.waitForFunction(name => document.querySelector('[data-contact-id="user:91020"] .name')?.textContent===name, {}, `Remote fixture ${i}`)
    }
    await submitKeyword(searchPage,'Remote fixture ninth')
    await searchPage.waitForFunction(() => document.querySelector('.user-search-feedback')?.textContent.includes('8 次'))
    assert.equal(searchPage.searches.length,8,'the ninth submission is blocked locally')
    await searchPage.close()
    const automatic = await fixturePage(true)
    await automatic.click('#sidebar-tab-contacts')
    const started = Date.now()
    await automatic.type('.search-bar input','Remote fixture automatic')
    await new Promise(resolve=>setTimeout(resolve,1800))
    assert.equal(automatic.searches.length,0,'typing does not search before the three-second pause')
    await automatic.waitForSelector('[data-contact-id="user:91020"]')
    assert.ok(Date.now()-started>=2800)
    await automatic.$eval('.search-bar input', input => {
      input.dispatchEvent(new CompositionEvent('compositionstart',{bubbles:true}))
      input.value='Remote fixture composed';input.dispatchEvent(new Event('input',{bubbles:true}))
      input.form.requestSubmit()
    })
    assert.equal(automatic.searches.length,1,'IME confirmation must not submit a second search')
    await automatic.$eval('.search-bar input', input => input.dispatchEvent(new CompositionEvent('compositionend',{bubbles:true})))
    await automatic.click('.submit-search')
    await automatic.waitForFunction(() => document.querySelector('[data-contact-id="user:91020"] .name')?.textContent==='Remote fixture composed')
    assert.equal(automatic.searches.length,2,'the visible search button shares the immediate submit path')
    await submitKeyword(automatic,'server-cooldown')
    await automatic.waitForFunction(() => document.querySelector('.user-search-feedback')?.textContent.includes('秒后重试'))
    assert.ok(await automatic.$('.users-wrap'),'server throttling does not log out the account')
    await submitKeyword(automatic,'Remote fixture after cooldown')
    assert.equal(automatic.searches.length,3,'server cooldown stops premature client retries')
    await automatic.waitForFunction(() => !document.querySelector('.user-search-feedback')?.textContent.includes('秒后重试'))
    await submitKeyword(automatic,'Remote fixture after cooldown')
    await automatic.waitForFunction(() => document.querySelector('[data-contact-id="user:91020"] .name')?.textContent==='Remote fixture after cooldown')
    assert.equal(automatic.searches.length,4,'server cooldown expires without an app reload')
    await automatic.close()
    assert.deepEqual(errors, [])
    console.log('PASS: mobile Enter/button, 3-second debounce, eight/minute quota, server cooldown, IME; same-ID private/group isolation, 5000-contact bounded DOM, cache-first reload and delayed ACK lifecycle.')
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)) }
}
main().catch(error => { console.error(error); server.close(); process.exitCode = 1 })
