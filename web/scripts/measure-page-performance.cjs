// Local lab measurement, not production Core Web Vitals. Never connects to remote APIs.
const fs = require('node:fs'), path = require('node:path'), http = require('node:http')
const puppeteer = require('puppeteer-core')
const dist = path.resolve(__dirname, '../dist')
const server = http.createServer((req, res) => {
  let file = path.resolve(dist, '.' + new URL(req.url, 'http://localhost').pathname)
  if (file !== dist && !file.startsWith(dist + path.sep)) return res.writeHead(404).end()
  if (fs.existsSync(file) && fs.statSync(file).isDirectory()) file = path.join(file, 'index.html')
  if (!fs.existsSync(file)) return res.writeHead(404).end()
  res.setHeader('Content-Type', { '.html':'text/html; charset=utf-8', '.js':'application/javascript', '.css':'text/css', '.svg':'image/svg+xml' }[path.extname(file)] || 'application/octet-stream')
  const content = fs.readFileSync(file)
  res.end(path.extname(file) === '.html' && req.headers['x-profile-preloads'] === 'off'
    ? content.toString().replace(/<link\b(?=[^>]*data-ssr-preload)[^>]*>/g, '') : content)
})
async function main() {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const origin = `http://127.0.0.1:${server.address().port}`
  const browser = await puppeteer.launch({ executablePath: process.argv[2] || process.env.BROWSER_EXECUTABLE, headless:'new', args:['--no-proxy-server'] })
  const report = { conditions:'Local uncompressed static server; cold cache; desktop 1180x850; CPU 4x; RTT 100ms; download 1.6Mbps; upload 750Kbps; anonymous; service worker registration disabled', samples:[] }
  try {
    const variants = process.argv.includes('--compare-preloads') ? ['off', 'on'] : ['on']
    for (const route of (process.argv.includes('--home-only') ? ['/'] : ['/', '/app'])) for (let i = 0; i < 3; i++) for (const variant of variants) {
      const context = await browser.createIncognitoBrowserContext(), page = await context.newPage()
      await page.setViewport({ width:1180, height:850 }); await page.setBypassServiceWorker(true)
      await page.setExtraHTTPHeaders({ 'x-profile-preloads':variant })
      await page.setRequestInterception(true)
      page.on('request', req => req.url().startsWith(origin) || req.url().startsWith('data:') ? req.continue() : req.abort())
      await page.evaluateOnNewDocument(() => {
        localStorage.setItem('locale', 'zh-CN')
        if (navigator.serviceWorker) navigator.serviceWorker.register = async () => ({})
        window.__measure = { lcp:0, cls:0, longTasks:0, longTaskMs:0 }
        for (const type of ['largest-contentful-paint', 'layout-shift', 'longtask']) new PerformanceObserver(list => {
          for (const e of list.getEntries()) {
            if (type === 'largest-contentful-paint') window.__measure.lcp = e.startTime
            if (type === 'layout-shift' && !e.hadRecentInput) window.__measure.cls += e.value
            if (type === 'longtask') { window.__measure.longTasks++; window.__measure.longTaskMs += e.duration }
          }
        }).observe({ type, buffered:true })
      })
      const cdp = await page.target().createCDPSession()
      await cdp.send('Network.enable')
      await cdp.send('Network.emulateNetworkConditions', { offline:false, latency:100, downloadThroughput:1600000/8, uploadThroughput:750000/8 })
      await cdp.send('Emulation.setCPUThrottlingRate', { rate:4 })
      await page.goto(origin + route, { waitUntil:'domcontentloaded' })
      await page.waitForFunction(route => document.querySelector('#app').__vue_app__ && (route === '/' || document.querySelector('.login-form:not(.is-verifying)')), {}, route)
      const readyMs = await page.evaluate(() => performance.now())
      await page.waitForTimeout(150)
      const sample = await page.evaluate(() => ({ ...window.__measure,
        fcp:performance.getEntriesByName('first-contentful-paint')[0]?.startTime,
        scripts:performance.getEntriesByType('resource').filter(r => r.name.endsWith('.js')).map(r => ({ name:new URL(r.name).pathname, start:Math.round(r.startTime), end:Math.round(r.responseEnd), bytes:r.decodedBodySize })) }))
      report.samples.push({ route, variant, readyMs:Math.round(readyMs), ...sample })
      await context.close()
    }
    if (process.argv[3]) fs.writeFileSync(process.argv[3], JSON.stringify(report, null, 2))
    console.log(JSON.stringify(report, null, 2))
  } finally { await browser.close() }
}
main().catch(error => { console.error(error); process.exitCode = 1 }).finally(() => server.close())
