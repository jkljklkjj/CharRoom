// Run against npm run build; exercise the real installed production Service Worker.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const http = require('node:http')
const puppeteer = require('puppeteer-core')
const dist = path.resolve(__dirname, '../dist')
let offline = false
let legacyWorker = true
let version = 1
let appRequests = 0
const server = http.createServer((req, res) => {
  if (offline) { req.socket.destroy(); return }
  const pathname = new URL(req.url, 'http://localhost').pathname
  if (pathname === '/sw.js' && legacyWorker) {
    // Model the previous worker's navigation fallback to its cached home document.
    res.writeHead(200, { 'Content-Type': 'application/javascript', 'Cache-Control': 'no-store' }).end(`
      self.addEventListener('install', event => event.waitUntil(caches.open('legacy-home-fixture').then(cache => cache.add('/'))));
      self.addEventListener('fetch', event => {
        if (event.request.mode === 'navigate') event.respondWith(caches.open('legacy-home-fixture').then(cache => cache.match('/')));
      });
    `)
    return
  }
  if (pathname === '/api/pwa-test') {
    res.writeHead(401, { 'Content-Type': 'application/json' }).end('{"error":"fixture"}')
    return
  }
  if (pathname === '/app') appRequests++
  let file = path.resolve(dist, '.' + pathname)
  if (file !== dist && !file.startsWith(dist + path.sep)) return res.writeHead(404).end()
  if (fs.existsSync(file) && fs.statSync(file).isDirectory()) file = path.join(file, 'index.html')
  if (!fs.existsSync(file)) return res.writeHead(404).end('Missing fixture')
  const ext = path.extname(file)
  res.setHeader('Content-Type', { '.html': 'text/html; charset=utf-8', '.js': 'application/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' }[ext] || 'application/octet-stream')
  const bytes = fs.readFileSync(file)
  res.end(ext === '.html' ? bytes.toString().replace('</head>', `<meta name="fixture-version" content="${version}"></head>`) : bytes)
})
async function main() {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const origin = `http://127.0.0.1:${server.address().port}`
  const browser = await puppeteer.launch({ executablePath: process.argv[2] || process.env.BROWSER_EXECUTABLE, headless: 'new', args: ['--no-proxy-server'] })
  try {
    const context = await browser.createIncognitoBrowserContext()
    let page = await context.newPage()
    await page.setRequestInterception(true)
    page.on('request', request => request.url().startsWith(origin) || request.url().startsWith('data:') ? request.continue() : request.abort())
    await page.goto(origin, { waitUntil: 'networkidle0' })
    await page.evaluate(() => navigator.serviceWorker.ready)
    await page.reload({ waitUntil: 'networkidle0' })
    await page.waitForFunction(() => !!navigator.serviceWorker.controller).catch(error => { throw new Error('Legacy worker did not control navigation', { cause: error }) })
    const registrationPage = page
    // Hold application JS off: assert the document before hydration can replace it.
    page = await context.newPage()
    await page.setJavaScriptEnabled(false)
    const legacyResponse = await page.goto(origin + '/app', { waitUntil: 'domcontentloaded' })
    assert.match(await legacyResponse.text(), /home-hero/, 'legacy fixture must reproduce the wrong home document')
    await registrationPage.evaluate(() => { window.__oldController = navigator.serviceWorker.controller })
    legacyWorker = false
    await registrationPage.evaluate(async () => {
      await (await navigator.serviceWorker.getRegistration()).update()
    })
    await registrationPage.waitForFunction(() => navigator.serviceWorker.controller && navigator.serviceWorker.controller !== window.__oldController, { polling: 100 }).catch(async error => {
      const states = await registrationPage.evaluate(async () => {
        const registration = await navigator.serviceWorker.getRegistration()
        return { installing: registration.installing?.state, waiting: registration.waiting?.state, active: registration.active?.state, caches: await caches.keys() }
      })
      throw new Error('New worker did not take control: ' + JSON.stringify(states), { cause: error })
    })
    const response = await page.goto(origin + '/app', { waitUntil: 'domcontentloaded' })
    assert.equal(response.fromServiceWorker(), true, 'test must use a controlling Service Worker')
    assert.match(await response.text(), /login-welcome/, '/app must start with its own SSR HTML')
    assert.equal(await page.$('.home-hero'), null, 'home must never appear before app JavaScript loads')
    assert.equal(await page.$eval('meta[name="robots"]', el => el.content), 'noindex,follow')
    const requestsBeforeReload = appRequests
    version = 2
    const refreshed = await page.reload({ waitUntil: 'domcontentloaded' })
    assert.match(await refreshed.text(), /name="fixture-version" content="2"/, 'online refresh must use current server HTML')
    assert.ok(appRequests > requestsBeforeReload)
    await page.waitForFunction(async () => {
      const cache = await caches.open('route-documents-v1')
      const response = await cache.match(location.href)
      return response && (await response.text()).includes('name="fixture-version" content="2"')
    }).catch(error => { throw new Error('Updated app document was not cached', { cause: error }) })
    assert.equal((await page.goto(origin + '/features')).status(), 200)
    assert.ok(await page.$('.public-breadcrumbs'))
    assert.equal((await page.goto(origin + '/not-a-route')).status(), 404, 'unknown URLs must not become the homepage')
    await page.goto(origin + '/app')
    assert.equal(await page.evaluate(async () => (await fetch('/api/pwa-test')).status), 401)
    assert.equal(await page.evaluate(async () => {
      for (const name of await caches.keys()) {
        if ((await (await caches.open(name)).keys()).some(request => new URL(request.url).pathname.startsWith('/api/'))) return true
      }
      return false
    }), false, 'API responses must never enter PWA caches')
    await page.goto(origin + '/app?code=fixture-only')
    assert.equal(await page.evaluate(async () => {
      const cache = await caches.open('route-documents-v1')
      return (await cache.keys()).some(request => new URL(request.url).search !== '')
    }), false, 'query parameters such as verification codes must not become cache keys')
    await page.goto(origin + '/app')
    offline = true
    const cached = await page.reload({ waitUntil: 'domcontentloaded' })
    assert.match(await cached.text(), /login-welcome/)
    assert.match(await cached.text(), /name="fixture-version" content="2"/)
    assert.equal(await page.$('.home-hero'), null, 'offline app navigation must retain its own document')
    await page.goto(origin + '/features')
    assert.ok(await page.$('.public-breadcrumbs'), 'offline public pages retain their own cached document')
    assert.equal(await page.$('.home-hero'), null)
    await context.close()
    console.log('PASS: legacy worker upgrade, real PWA /app first document, online freshness, route-specific offline pages, real 404 and uncached API/query URLs')
  } finally { await browser.close() }
}
main().catch(error => { console.error(error); process.exitCode = 1 }).finally(() => server.close())
