// Run after npm run build; no real account or external services are used.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const http = require('node:http')
const puppeteer = require('puppeteer-core')
const dist = path.resolve(__dirname, '../dist')
const server = http.createServer((req, res) => {
  const pathname = new URL(req.url, 'http://localhost').pathname
  let file = path.resolve(dist, '.' + pathname)
  if (file !== dist && !file.startsWith(dist + path.sep)) return res.writeHead(404).end()
  if (fs.existsSync(file) && fs.statSync(file).isDirectory()) file = path.join(file, 'index.html')
  if (!fs.existsSync(file)) return res.writeHead(404).end()
  res.setHeader('Content-Type', { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' }[path.extname(file)] || 'application/octet-stream')
  res.end(fs.readFileSync(file))
})
async function main() {
  const html = fs.readFileSync(path.join(dist, 'app/index.html'), 'utf8')
  assert.match(html, /id="app" data-ssr/)
  assert.match(html, /login-welcome/)
  assert.match(html, /noindex/)
  assert.doesNotMatch(html, /class="boot-shell"/)
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const origin = `http://127.0.0.1:${server.address().port}`
  const browser = await puppeteer.launch({ executablePath: process.argv[2] || process.env.BROWSER_EXECUTABLE, headless: 'new', args: ['--no-proxy-server'] })
  if (process.env.CLIENT_SCREENSHOT_DIR) fs.mkdirSync(process.env.CLIENT_SCREENSHOT_DIR, { recursive: true })
  try {
    for (const route of ['/', '/app']) for (const width of [1180, 390]) {
      const context = await browser.createIncognitoBrowserContext()
      const page = await context.newPage()
      const errors = []
      const external = []
      const scripts = []
      page.on('pageerror', e => errors.push(e.message))
      page.on('console', msg => { if (/hydration|mismatch/i.test(msg.text())) errors.push(msg.text()) })
      await page.setViewport({ width, height: 900 })
      await page.setBypassServiceWorker(true)
      await page.evaluateOnNewDocument(() => localStorage.setItem('locale', 'zh-CN'))
      let release
      const ready = new Promise(resolve => { release = resolve })
      await page.setRequestInterception(true)
      page.on('request', async request => {
        if (request.url().startsWith('data:')) return request.continue()
        if (new URL(request.url()).origin !== origin) { external.push(request.url()); return request.abort() }
        if (request.resourceType() === 'script') { scripts.push(request.url()); await ready }
        return request.continue()
      })
      const navigation = page.goto(origin + route)
      if (route === '/') {
        await page.waitForSelector('.home-hero h1', { visible: true })
        const before = await page.$eval('.home-hero', el => ({ display: getComputedStyle(el).display, height: el.getBoundingClientRect().height }))
        assert.equal(before.display, 'grid', 'home must be styled before the JS request finishes')
        if (process.env.CLIENT_SCREENSHOT_DIR) await page.screenshot({ path: path.join(process.env.CLIENT_SCREENSHOT_DIR, `home-${width}-before.png`) })
        await page.evaluate(() => { window.__ssrHero = document.querySelector('.home-hero h1') })
        release()
        await navigation
        await page.waitForFunction(() => !!document.querySelector('#app').__vue_app__)
        assert.equal(await page.evaluate(() => window.__ssrHero === document.querySelector('.home-hero h1')), true)
        assert.ok(Math.abs(before.height - await page.$eval('.home-hero', el => el.getBoundingClientRect().height)) < 1)
        assert.equal(await page.$('.hero-conversation .entered'), null, 'first-fold content must not replay its entrance animation')
        assert.ok(!scripts.some(url => /WebApp-|proto-|LoginRegister-/.test(url)), 'home must not load the chat runtime')
        assert.deepEqual(external, [], 'public home must not request authentication or remote APIs')
        await page.click('.home-open')
        await page.waitForSelector('#login-password', { visible: true })
        assert.equal(new URL(page.url()).pathname, '/app')
        assert.deepEqual(errors, [])
        await context.close()
        continue
      }
      await page.waitForSelector('.login-welcome', { visible: true })
      await page.waitForSelector('.login-verifying', { visible: true })
      await page.waitForSelector('#login-password', { hidden: true })
      await page.evaluate(() => {
        window.__ssrPanel = document.querySelector('.panel')
        window.__ssrWelcome = document.querySelector('.login-welcome')
      })
      const before = await page.$eval('.panel', el => ({ x: el.getBoundingClientRect().x, y: el.getBoundingClientRect().y, width: el.getBoundingClientRect().width, height: el.getBoundingClientRect().height, border: getComputedStyle(el).borderRadius }))
      assert.notEqual(before.border, '0px', 'SSR must include the route CSS before JS loads')
      if (process.env.CLIENT_SCREENSHOT_DIR) await page.screenshot({ path: path.join(process.env.CLIENT_SCREENSHOT_DIR, `app-${width}-before.png`) })
      release()
      await navigation
      await page.waitForSelector('#login-password', { visible: true })
      assert.equal(await page.evaluate(() => window.__ssrPanel === document.querySelector('.panel') && window.__ssrWelcome === document.querySelector('.login-welcome')), true, 'hydrate the real components without replacing them')
      const after = await page.$eval('.panel', el => ({ x: el.getBoundingClientRect().x, y: el.getBoundingClientRect().y, width: el.getBoundingClientRect().width, height: el.getBoundingClientRect().height }))
      for (const key of ['x', 'y', 'width', 'height']) assert.ok(Math.abs(before[key] - after[key]) < 1, `auth frame ${key} must stay stable`)
      assert.equal(await page.$('.app-main'), null, 'anonymous users cannot see the private app')
      if (process.env.CLIENT_SCREENSHOT_DIR) await page.screenshot({ path: path.join(process.env.CLIENT_SCREENSHOT_DIR, `app-${width}-after.png`) })
      assert.deepEqual(errors, [])
      await context.close()
    }
    console.log('Home + App SSR: styled first frame, mobile/desktop hydration, home without auth/chat runtime, app navigation and stable auth frame passed')
  } finally { await browser.close() }
}
main().catch(error => { console.error(error); process.exitCode = 1 }).finally(() => server.close())
