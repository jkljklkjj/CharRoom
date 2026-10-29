// Built documents with real Chromium safe-area emulation; no production APIs/accounts.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const http = require('node:http')
const puppeteer = require('puppeteer-core')
const dist = path.resolve(__dirname, '../dist')
const server = http.createServer((req, res) => {
  let file = path.resolve(dist, '.' + new URL(req.url, 'http://localhost').pathname)
  if (file !== dist && !file.startsWith(dist + path.sep)) return res.writeHead(404).end()
  if (fs.existsSync(file) && fs.statSync(file).isDirectory()) file = path.join(file, 'index.html')
  if (!fs.existsSync(file)) return res.writeHead(404).end()
  res.setHeader('Content-Type', { '.html':'text/html; charset=utf-8', '.js':'application/javascript', '.css':'text/css', '.svg':'image/svg+xml' }[path.extname(file)] || 'application/octet-stream')
  res.end(fs.readFileSync(file))
})
async function main() {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const origin = `http://127.0.0.1:${server.address().port}`
  const browser = await puppeteer.launch({ executablePath: process.argv[2], headless:'new', args:['--no-proxy-server'] })
  try {
    const page = await browser.newPage(), errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.setViewport({width:390,height:844,isMobile:true,hasTouch:true})
    await page.setBypassServiceWorker(true)
    const client = await page.target().createCDPSession()
    await client.send('Emulation.setSafeAreaInsetsOverride', {insets:{top:64,bottom:24,left:0,right:0}})
    await page.setJavaScriptEnabled(false)
    for (const route of ['/', '/app']) {
      await page.goto(origin + route)
      const initial = await page.evaluate(() => ({ top:getComputedStyle(document.body).paddingTop,
        locked:document.documentElement.hasAttribute('data-app-viewport'), boundary:getComputedStyle(document.documentElement).overscrollBehaviorY }))
      assert.equal(initial.top, '0px', `${route} must not reserve a visible browser-bar spacer`)
      assert.equal(initial.boundary, 'none', 'the root stops dragging at its content boundary')
      assert.equal(initial.locked, route === '/app', 'app lock must exist in the original HTML')
      if (route === '/app') {
        await page.evaluate(() => scrollTo(0, 200))
        assert.equal(await page.evaluate(() => scrollY), 0, 'app document cannot scroll even before hydration')
      }
    }
    await page.setJavaScriptEnabled(true)
    await page.setRequestInterception(true)
    page.on('request', request => request.url().startsWith(origin) || request.url().startsWith('data:') ? request.continue() : request.abort())
    await page.goto(origin)
    await page.waitForFunction(() => !!document.querySelector('#app').__vue_app__)
    await page.evaluate(() => scrollTo(0, 300))
    assert.ok(await page.evaluate(() => scrollY > 0), 'home still scrolls normally')
    await page.evaluate(() => scrollTo(0, 0))
    await client.send('Input.synthesizeScrollGesture', {x:195,y:150,yDistance:500,gestureSourceType:'touch'})
    assert.equal(await page.evaluate(() => scrollY), 0, 'pulling down at the home boundary cannot reveal an extra document region')
    await page.click('.home-open')
    await page.waitForSelector('#login-password', {visible:true})
    assert.equal(await page.evaluate(() => document.documentElement.hasAttribute('data-app-viewport')), true)
    assert.equal(await page.evaluate(() => scrollY), 0)
    // Browser chrome and viewport size changes must not add top space again.
    await page.setViewport({width:390,height:700,isMobile:true,hasTouch:true})
    await client.send('Emulation.setSafeAreaInsetsOverride', {insets:{top:48,bottom:24,left:0,right:0}})
    assert.equal(await page.$eval('.app-wrap', element => element.getBoundingClientRect().top), 0)
    await page.click('.login-back')
    await page.waitForSelector('.home-hero')
    assert.equal(await page.evaluate(() => document.documentElement.hasAttribute('data-app-viewport')), false)
    await page.evaluate(() => scrollTo(0, 300))
    assert.ok(await page.evaluate(() => scrollY > 0), 'leaving app releases the root scroll lock')
    assert.deepEqual(errors, [])
    console.log('PASS: real 64px/48px safe-area, home/app initial HTML, drag boundary, resize, SPA scroll-lock lifecycle')
  } finally { await browser.close() }
}
main().catch(error => { console.error(error); process.exitCode=1 }).finally(() => server.close())
