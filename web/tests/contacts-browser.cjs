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
  const file = path.resolve(dist, pathname === '/app' || pathname === '/' ? 'index.html' : pathname.slice(1))
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
  async function fixturePage(empty = false, locale = 'zh-CN') {
    const context = await browser.createIncognitoBrowserContext(), page = await context.newPage()
    page.setDefaultTimeout(12000)
    await page.setViewport({ width: 1180, height: 850 }); await page.setBypassServiceWorker(true)
    page.on('pageerror', error => errors.push(error.message))
    await page.evaluateOnNewDocument(locale => {
      sessionStorage.setItem('charroom_token', 'fixture-token')
      localStorage.setItem('charroom_accountId', '91001'); localStorage.setItem('locale', locale)
      globalThis.fixtureWrites = []; globalThis.fixtureReaders = []
      globalThis.WebTransport = class {
        constructor() { this.ready = Promise.resolve(); this.closed = new Promise(() => {}); this.incomingBidirectionalStreams = new ReadableStream() }
        async createBidirectionalStream() { return { readable: new ReadableStream({ start(controller) { globalThis.fixtureReaders.push(controller) } }), writable: new WritableStream({ write(bytes) { globalThis.fixtureWrites.push(Array.from(bytes)) } }) } }
        close() {}
      }
    }, locale)
    await page.setRequestInterception(true)
    page.on('request', request => {
      const url = new URL(request.url())
      if (url.origin === origin) { request.continue(); return }
      if (!url.pathname.startsWith('/api/')) { request.abort(); return }
      let data = []
      if (url.pathname === '/api/auth/validate') data = { valid: true }
      if (url.pathname === '/api/users/me') data = { id: 91001, username: 'Fixture self' }
      if (url.pathname === '/api/users/online-status') data = {}
      if (url.pathname === '/api/groups') data = empty ? [] : [{ id: 91010, name: '周末散步的人' }]
      if (url.pathname === '/api/friends') data = [{ id: 0, username: 'AI Assistant' }, { id: 91001, username: 'Fixture self' }, ...(empty ? [] : [{ id: 91003, username: 'Zoe', signature: '一起看一场日落。' }, { id: 91002, username: 'Alice', signature: '慢慢走，也好好聊。' }])]
      request.respond({ status: 200, contentType: 'application/json', headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*', 'Access-Control-Allow-Methods': '*' }, body: JSON.stringify({ code: 0, data }) })
    })
    await page.goto(origin + '/app'); await page.waitForSelector('.user-row')
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
    await page.evaluate(bytes => globalThis.fixtureReaders[0].enqueue(new Uint8Array(bytes)), Array.from(incoming))
    await page.waitForSelector('[data-contact-id="91003"] .unread-badge')
    await page.focus('#sidebar-tab-messages'); await page.keyboard.press('ArrowRight')
    await page.waitForSelector('#sidebar-tab-contacts[aria-selected="true"]')
    assert.equal(await page.evaluate(() => document.activeElement.id), 'sidebar-tab-contacts')
    assert.deepEqual(await page.$$eval('.user-row', rows => rows.map(row => row.dataset.contactId)), ['91002', '91003', '-91010'])
    assert.equal(await page.$('.unread-badge'), null)
    await page.keyboard.press('Home')
    await page.waitForSelector('[data-contact-id="91003"] .unread-badge')
    assert.equal(await page.$eval('[data-contact-id="91003"] .unread-badge', element => element.textContent), '1', 'tab change must preserve unread count')
    await page.click('#sidebar-tab-contacts'); await page.type('.search-bar input', ' ALI ')
    await page.waitForFunction(() => document.querySelectorAll('.user-row').length === 1)
    assert.equal(await page.$eval('.user-row', element => element.dataset.contactId), '91002')
    await page.setViewport({ width: 390, height: 844 })
    await page.waitForSelector('.mobile-view [data-contact-id="91002"]')
    await page.waitForSelector('#sidebar-tab-contacts[aria-selected="true"]')
    await page.click('[data-contact-id="91002"]'); await page.waitForSelector('.mobile-header .back-btn')
    await page.click('.mobile-header .back-btn'); await page.waitForSelector('#sidebar-tab-contacts[aria-selected="true"]')
    assert.equal(await page.$eval('.search-bar input', element => element.value), ' ALI ')
    await page.click('.clear-search'); await page.type('.search-bar input', 'not-a-contact')
    await page.waitForSelector('#sidebar-panel [role="status"]')
    assert.equal(await page.$('.user-row'), null)
    await page.click('.clear-search'); await page.waitForSelector('[data-contact-id="-91010"]'); await page.click('[data-contact-id="-91010"]')
    await page.waitForSelector('.composer textarea'); await page.type('.composer textarea', 'Fixture group send')
    const beforeSend = await page.evaluate(() => globalThis.fixtureWrites.length)
    await page.keyboard.press('Enter')
    await page.waitForFunction(() => document.querySelector('.messages')?.textContent.includes('Fixture group send'))
    await page.waitForFunction(count => globalThis.fixtureWrites.length > count, {}, beforeSend)
    const written = await page.evaluate(() => globalThis.fixtureWrites)
    const groupSend = written.map(bytes => Wrapper.decode(Uint8Array.from(bytes))).find(message => message.type === types.GROUP_CHAT)
    assert.ok(groupSend, 'group entry must send GROUP_CHAT')
    assert.equal(groupSend.groupChat.targetClientId.toString(), '91010', 'sidebar negative ID must not reach wire')
    assert.equal(await page.$eval('.mobile-header .chat-title', element => element.textContent), '周末散步的人')
    await page.click('.mobile-header .back-btn'); await page.waitForSelector('#sidebar-tab-contacts[aria-selected="true"]')
    for (const theme of ['light', 'dark']) for (const width of [1180, 320]) {
      await page.setViewport({ width, height: 850 })
      await page.evaluate(theme => document.documentElement.setAttribute('data-theme', theme), theme)
      await page.waitForSelector('#sidebar-tab-contacts[aria-selected="true"]')
      assert.equal(await page.$eval('#sidebar-tab-contacts', element => getComputedStyle(element).backgroundColor), theme === 'dark' ? 'rgb(73, 48, 36)' : 'rgb(255, 234, 216)', 'selected tab uses the theme palette')
      assert.equal(await page.$eval('.sidebar', element => element.scrollWidth > element.clientWidth + 1), false, `${theme}/${width} must fit`)
      await screenshot(page, `contacts-${theme}-${width}`)
    }
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
    assert.deepEqual(errors, [])
    console.log('PASS: tabs/keyboard, unread preserved, directory/search/empty, mobile back, private/group navigation, positive group wire ID, three languages, light/dark 320px layout.')
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)) }
}
main().catch(error => { console.error(error); server.close(); process.exitCode = 1 })
