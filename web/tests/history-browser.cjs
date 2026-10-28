// Built-app history/virtualization regression. All external requests use fixture responses.
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict')
const puppeteer = require('puppeteer-core')
const dist = path.resolve(__dirname, '../dist')
const mime = { '.js': 'application/javascript', '.css': 'text/css', '.html': 'text/html', '.proto': 'text/plain', '.png': 'image/png', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json' }
const server = http.createServer((request, response) => {
  const url = new URL(request.url, 'http://localhost')
  const file = path.resolve(dist, url.pathname === '/app' ? 'index.html' : url.pathname.slice(1))
  if (!file.startsWith(dist + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { response.writeHead(404).end(); return }
  response.writeHead(200, { 'Content-Type': mime[path.extname(file)] || 'application/octet-stream' }); response.end(fs.readFileSync(file))
})
async function main() {
  const executablePath = process.argv[2] || process.env.BROWSER_EXECUTABLE
  assert.ok(executablePath, 'Supply a Chromium executable')
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const origin = `http://127.0.0.1:${server.address().port}`
  const browser = await puppeteer.launch({ executablePath, headless: 'new', args: ['--no-proxy-server'] })
  let stage = 'boot'
  try {
    const page = await browser.newPage(), errors = []
    page.setDefaultTimeout(15000)
    await page.setViewport({ width: 1180, height: 850 }); await page.setBypassServiceWorker(true)
    page.on('pageerror', error => errors.push(error.message))
    await page.evaluateOnNewDocument(() => {
      sessionStorage.setItem('charroom_token', 'fixture-token')
      localStorage.setItem('charroom_accountId', '91001')
      globalThis.WebTransport = class {
        constructor() { this.ready = Promise.resolve(); this.closed = new Promise(() => {}); this.incomingBidirectionalStreams = new ReadableStream() }
        async createBidirectionalStream() { return { readable: new ReadableStream(), writable: new WritableStream({ write() {} }) } }
        close() {}
      }
    })
    const total=Number(process.env.HISTORY_ROWS||1200)
    const now = Date.now(), rows = Array.from({ length: total }, (_, i) => ({
      id: String(i + 1), senderId: '91002', receiverId: '91001', conversationId: 'user:91001:91002',
      seqId: String(i + 1), message: `Fixture message ${i + 1}` + '\nvariable row height'.repeat(i % 6), timestamp: now - total*1000 + i * 1000
    }))
    await page.setRequestInterception(true)
    page.on('request', request => {
      const url = new URL(request.url())
      if (url.origin === origin) { request.continue(); return }
      if (!url.pathname.startsWith('/api/')) { request.abort(); return }
      let data = []
      if (url.pathname === '/api/cluster/owner') data = { ownerGatewayId: 'fixture', ownerEndpoint: '', gateways: [{ gatewayId: 'fixture' }] }
      if (url.pathname === '/api/auth/validate') data = { valid: true }
      if (url.pathname === '/api/users/me') data = { id: 91001, username: 'Fixture self' }
      if(url.pathname==='/api/contacts/snapshot')data=url.searchParams.get('version')==='fixture'?{version:'fixture',unchanged:true}:{version:'fixture',unchanged:false,users:[{id:91002,username:'Fixture friend'},{id:91003,username:'Another friend'}],groups:[]}
      if (url.pathname === '/api/friends') data = [{ id: 91002, username: 'Fixture friend' }]
      if (url.pathname === '/api/users/online-status') data = {}
      if (url.pathname === '/api/messages/recent') {
        const body = JSON.parse(request.postData() || '{}'), after = Number(body.afterId || 0)
        const messages = rows.slice(after, after + 100)
        data = { messages, windowEnd: now, nextTimestamp: messages.at(-1)?.timestamp, nextId: messages.at(-1)?.id, hasMore: after + messages.length < rows.length }
      }
      void request.respond({ status: 200, contentType: 'application/json', headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*', 'Access-Control-Allow-Methods': '*' }, body: JSON.stringify({ code: 0, data }) })
    })
    await page.goto(origin + '/app')
    await page.waitForSelector('[data-contact-id="user:91002"]')
    stage = 'restore 1200 rows'
    await page.waitForFunction(async expected => {
      const db = await new Promise(resolve => { const r = indexedDB.open('charroom_messages'); r.onsuccess = () => resolve(r.result) })
      try { return await new Promise(resolve => { const r = db.transaction('messages').objectStore('messages').count(); r.onsuccess = () => resolve(r.result === expected) }) }
      finally { db.close() }
    },{},total)
    await page.click('[data-contact-id="user:91002"]')
    stage = 'open history'
    await page.waitForSelector('.messages[data-message-count="50"]')
    if(total>2000){
      stage='bounded 2000-row window'
      for(let expected=100;expected<=2000;expected+=50){
        await page.$eval('.messages',el=>{el.scrollTop=0;el.dispatchEvent(new Event('scroll'))})
        await page.waitForFunction(count=>Number(document.querySelector('.messages')?.dataset.messageCount)>=count,{},expected)
      }
      await page.$eval('.messages',el=>{el.scrollTop=0;el.dispatchEvent(new Event('scroll'))})
      await page.waitForSelector('.jump-latest')
      assert.equal(await page.$eval('.messages',el=>Number(el.dataset.messageCount)),2000)
      assert.ok(await page.$$eval('.virtual-message-row',rows=>rows.length<50))
      await page.click('.jump-latest');await page.waitForSelector('.messages[data-message-count="50"]')
      await page.waitForFunction(last=>document.querySelector('.messages')?.textContent.includes(`Fixture message ${last}`),{},total)
      assert.equal(await page.$('.jump-latest'),null)
      assert.deepEqual(errors,[])
      console.log(`PASS: ${total} durable messages, 2000-row active window, bounded DOM, older pagination and return to latest.`)
      return
    }
    if(process.env.HISTORY_SCREENSHOT_DIR){
      fs.mkdirSync(process.env.HISTORY_SCREENSHOT_DIR,{recursive:true})
      for(const theme of ['light','dark']){
        await page.evaluate(theme=>document.documentElement.setAttribute('data-theme',theme),theme)
        await page.screenshot({path:path.join(process.env.HISTORY_SCREENSHOT_DIR,`chat-${theme}.png`)})
      }
      await page.evaluate(()=>document.documentElement.setAttribute('data-theme','light'))
    }
    for (let expected = 100; expected <= 1200; expected += 50) {
      await page.$eval('.messages', el => { el.scrollTop = 0; el.dispatchEvent(new Event('scroll')) })
      await page.waitForFunction(count => Number(document.querySelector('.messages')?.dataset.messageCount) >= count, {}, expected).catch(error => { throw new Error(`pagination ${expected}: ${error.message}`) })
    }
    assert.ok(await page.$$eval('.virtual-message-row', rows => rows.length < 50), '1200 loaded rows must not mount 1200 DOM nodes')
    stage = 'bottom jump'
    await page.$eval('.messages', el => { el.scrollTop = el.scrollHeight; el.dispatchEvent(new Event('scroll')) })
    await page.waitForFunction(() => document.querySelector('.messages')?.textContent.includes('Fixture message 1200'))
    stage = 'middle viewport'
    await page.$eval('.messages', el => { el.scrollTop = el.scrollHeight / 2; el.dispatchEvent(new Event('scroll')) })
    await page.waitForFunction(() => document.querySelectorAll('.virtual-message-row').length > 2)
    await page.evaluate(async () => {
      let previous = '', stable = 0
      for (let i = 0; i < 120; i++) {
        await new Promise(requestAnimationFrame)
        const el = document.querySelector('.messages'), signature = `${el.scrollTop}:${el.scrollHeight}`
        stable = signature === previous ? stable + 1 : 0; previous = signature
        if (stable >= 4) return
      }
      throw new Error('virtual layout did not settle')
    })
    // Resizing a row above the viewport must preserve the visible message and pixel offset.
    const before = await page.$eval('.messages', el => {
      const top = el.getBoundingClientRect().top
      const row = [...el.querySelectorAll('.virtual-message-row')].find(row => row.getBoundingClientRect().bottom > top + 16)
      return { key: row.dataset.messageKey, offset: row.getBoundingClientRect().top - top }
    })
    const oldHeight = await page.$eval('.virtual-message-space', el => el.getBoundingClientRect().height)
    const resizedKey = await page.$eval('.virtual-message-row', el => el.dataset.messageKey)
    await page.addStyleTag({ content: `.virtual-message-row[data-message-key="${resizedKey}"]{padding-bottom:350px!important}` })
    await page.waitForFunction(height => document.querySelector('.virtual-message-space').getBoundingClientRect().height > height + 100, {}, oldHeight).catch(async error => {
      const state = await page.$eval('.messages', (el, key) => ({ total: el.scrollHeight, virtual: el.firstElementChild.getBoundingClientRect().height, rows: [...el.querySelectorAll('.virtual-message-row')].map(row => ({ key: row.dataset.messageKey, height: row.getBoundingClientRect().height, padding: getComputedStyle(row).paddingBottom })).filter(row => row.key === key), count: el.querySelectorAll('.virtual-message-row').length }), resizedKey)
      throw new Error(`resize measurement ${JSON.stringify({ oldHeight, resizedKey, state })}: ${error.message}`)
    })
    await page.waitForFunction(before => {
      const el = document.querySelector('.messages'), row = [...el.querySelectorAll('.virtual-message-row')].find(row => row.dataset.messageKey === before.key)
      return row && Math.abs(row.getBoundingClientRect().top - el.getBoundingClientRect().top - before.offset) < 3
    }, {}, before).catch(async error => {
      const current = await page.$eval('.messages', (el, key) => ({ scrollTop: el.scrollTop, total: el.scrollHeight, rows: [...el.querySelectorAll('.virtual-message-row')].map(row => ({ key: row.dataset.messageKey, top: row.getBoundingClientRect().top - el.getBoundingClientRect().top })).filter(row => row.key === key) }), before.key)
      throw new Error(`anchor ${JSON.stringify({ before, current })}: ${error.message}`)
    })
    stage='cached conversation return'
    // Enter the enlarged row beyond the 100px estimate. Restoring only its
    // offset would select a different row before ResizeObserver measures it.
    await page.$eval('.messages',(el,key)=>{
      const row=[...el.querySelectorAll('.virtual-message-row')].find(row=>row.dataset.messageKey===key)
      el.scrollTop+=row.getBoundingClientRect().top-el.getBoundingClientRect().top+180
      el.dispatchEvent(new Event('scroll'))
    },resizedKey)
    await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))))
    const reading=await page.$eval('.messages',el=>{
      const top=el.getBoundingClientRect().top,row=[...el.querySelectorAll('.virtual-message-row')].find(row=>row.getBoundingClientRect().bottom>top+16)
      return {key:row.dataset.messageKey,offset:row.getBoundingClientRect().top-top}
    })
    await page.click('[data-contact-id="user:91003"]')
    await page.waitForFunction(()=>document.querySelector('.messages')?.dataset.messageCount==='0')
    await page.click('[data-contact-id="user:91002"]')
    await page.waitForFunction(()=>document.querySelector('.messages')?.dataset.messageCount==='1200')
    await page.waitForFunction(reading=>{
      const el=document.querySelector('.messages'),row=[...el.querySelectorAll('.virtual-message-row')].find(row=>row.dataset.messageKey===reading.key)
      return row && Math.abs(row.getBoundingClientRect().top-el.getBoundingClientRect().top-reading.offset)<3
    },{},reading).catch(async error=>{
      const current=await page.$eval('.messages',(el,key)=>({scrollTop:el.scrollTop,total:el.scrollHeight,rows:[...el.querySelectorAll('.virtual-message-row')].map(row=>({key:row.dataset.messageKey,top:row.getBoundingClientRect().top-el.getBoundingClientRect().top})),key}),reading.key)
      throw new Error(`cached anchor ${JSON.stringify({reading,current})}: ${error.message}`)
    })
    await page.setViewport({ width: 390, height: 844 })
    stage = 'mobile navigation'
    await page.waitForSelector('.mobile-view [data-contact-id="user:91002"]')
    await page.click('.mobile-view [data-contact-id="user:91002"]')
    await page.waitForFunction(() => !!document.querySelector('.messages'))
    assert.deepEqual(errors, [])
    console.log('PASS: 1200 paged history rows, stable local pagination, bounded DOM, bottom jump, variable-height anchor, cached conversation/reading restoration, mobile resize.')
  } catch (error) { throw new Error(`${stage}: ${error.stack || error.message}`) }
  finally { await browser.close(); await new Promise(resolve => server.close(resolve)) }
}
main().catch(error => { console.error(error); server.close(); process.exitCode = 1 })
