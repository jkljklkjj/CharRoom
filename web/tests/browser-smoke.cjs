// Run after npm run build. All accounts, REST responses and realtime traffic are fixtures.
const fs = require('node:fs')
const path = require('node:path')
const http = require('node:http')
const assert = require('node:assert/strict')
const puppeteer = require('puppeteer-core')
const protobuf = require('protobufjs')
const dist = path.resolve(__dirname, '../dist')
const root = protobuf.loadSync(path.resolve(__dirname, '../src/proto/message.proto'))
const Wrapper = root.lookupType('com.chatlite.proto.MessageWrapper')
const peers = new Map()
const mime = { '.js': 'application/javascript', '.css': 'text/css', '.html': 'text/html', '.proto': 'text/plain', '.png': 'image/png', '.svg': 'image/svg+xml', '.ico': 'image/x-icon', '.webmanifest': 'application/manifest+json' }
const server = http.createServer((request, response) => {
  const pathname = new URL(request.url, 'http://localhost').pathname
  const file = path.resolve(dist, pathname === '/app' || pathname === '/' ? 'index.html' : pathname.slice(1))
  if (!file.startsWith(dist + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { response.writeHead(404).end(); return }
  response.writeHead(200, { 'Content-Type': mime[path.extname(file)] || 'application/octet-stream' })
  response.end(fs.readFileSync(file))
})
const encode = object => Array.from(Wrapper.encode(Wrapper.fromObject(object)).finish())
const deliver = (page, object) => page.evaluate(bytes => window.__fixtureIncoming.enqueue(new Uint8Array(bytes)), encode(object))

async function main() {
  const executablePath = process.argv[2] || process.env.BROWSER_EXECUTABLE
  if (!executablePath) throw new Error('Pass a Chromium/Edge executable path or set BROWSER_EXECUTABLE')
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const origin = `http://127.0.0.1:${server.address().port}`
  const browser = await puppeteer.launch({ executablePath, headless: 'new', args: ['--no-proxy-server'] })
  const errors = []
  try {
    const pages = []
    for (const id of [91001, 91002]) {
      const other = id === 91001 ? 91002 : 91001
      const context = await browser.createIncognitoBrowserContext()
      const page = await context.newPage()
      await page.setBypassServiceWorker(true)
      page.on('pageerror', error => errors.push(error.message))
      await page.evaluateOnNewDocument(id => {
        sessionStorage.setItem('charroom_token', 'fixture-access-' + id)
        localStorage.setItem('charroom_accountId', String(id))
        localStorage.setItem('charroom_refreshToken', 'fixture-refresh-' + id)
        // Keep a real Worker and real IndexedDB; only the remote transport is simulated.
        const BrowserWorker = globalThis.Worker
        globalThis.__workerReplies = []
        globalThis.Worker = class extends BrowserWorker {
          constructor(...args) {
            super(...args)
            this.addEventListener('message', event => globalThis.__workerReplies.push(event.data.type))
          }
        }
        globalThis.WebTransport = class {
          constructor() {
            this.ready = Promise.resolve()
            this.closed = new Promise(() => {})
            this.incomingBidirectionalStreams = new ReadableStream()
          }
          async createBidirectionalStream() {
            const readable = new ReadableStream({ start(controller) { globalThis.__fixtureIncoming ??= controller } })
            const writable = new WritableStream({ write(data) { return globalThis.__fixtureSend(Array.from(new Uint8Array(data))) } })
            return { readable, writable }
          }
          close() {}
        }
      }, id)
      await page.exposeFunction('__fixtureSend', async bytes => {
        const object = Wrapper.toObject(Wrapper.decode(new Uint8Array(bytes)), { longs: String, enums: Number })
        if (object.type === 1) {
          peers.set(id, page)
          await deliver(page, { type: 10, response: { success: true, clientId: String(id) } })
        } else if (object.type === 3) {
          const receiver = peers.get(Number(object.chat.targetClientId))
          assert.ok(receiver, 'recipient session must be ready')
          await deliver(receiver, { type: 3, chat: { ...object.chat, userId: String(id), timestamp: String(Date.now()), seqId: '1' } })
          await deliver(page, { type: 9, ack: { messageId: object.chat.messageId, success: true } })
        }
      })
      await page.setRequestInterception(true)
      page.on('request', request => {
        const url = new URL(request.url())
        if (url.origin === origin) { request.continue(); return }
        if (!url.pathname.startsWith('/api/')) { request.abort(); return }
        let data = []
        if (url.pathname === '/api/auth/validate') data = { valid: true }
        if (url.pathname === '/api/users/me') data = { id, username: 'Fixture ' + id }
        if (url.pathname === '/api/friends') data = [{ id: other, username: 'Fixture ' + other, online: true }]
        if (url.pathname === '/api/users/online-status') data = { [other]: true }
        request.respond({ status: 200, contentType: 'application/json', headers: {
          'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*', 'Access-Control-Allow-Methods': '*'
        }, body: JSON.stringify({ code: 0, data }) })
      })
      await page.goto(origin + '/app')
      await page.waitForSelector('.users-list li', { timeout: 15000 })
      await page.click('.users-list li')
      await page.waitForSelector('textarea')
      pages.push(page)
    }
    for (const page of pages) await page.waitForFunction(() => globalThis.__workerReplies.includes('init'))
    assert.equal(peers.size, 2)
    for (let index = 0; index < 2; index++) {
      const text = 'typescript-browser-' + index
      await pages[index].type('textarea', text)
      await pages[index].click('button.send')
      await pages[1 - index].waitForFunction(text => [...document.querySelectorAll('.text-content')].some(element => element.textContent === text), {}, text)
      await pages[index].waitForFunction(() => !document.querySelector('.status.sending') && !document.querySelector('.status.failed'))
    }
    for (const page of pages) {
      const replies = await page.evaluate(() => globalThis.__workerReplies)
      assert.ok(replies.includes('encode') && replies.includes('decode'), 'real bundled Worker must encode and decode')
      assert.ok(!replies.includes('error'))
    }
    assert.deepEqual(errors, [])
    console.log('PASS: built app, two isolated accounts, bidirectional messages, ACKs, real Worker; mocked remote APIs/transport')
  } finally { await browser.close() }
}
main().catch(error => { console.error(error); process.exitCode = 1 }).finally(() => server.close())
