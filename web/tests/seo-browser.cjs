const { strict: assert } = require('node:assert');
const http = require('node:http');
const fs = require('node:fs/promises');
const path = require('node:path');
const puppeteer = require('puppeteer-core');
const config = require('../site.routes.json');
const root = path.resolve(__dirname, '../dist');
(async () => {
  const server = http.createServer(async (req, res) => {
    try {
      let file = path.resolve(root, '.' + new URL(req.url, 'http://localhost').pathname);
      if (file !== root && !file.startsWith(root + path.sep)) throw Error('Invalid path');
      if ((await fs.stat(file)).isDirectory()) file = path.join(file, 'index.html');
      const types = { '.html':'text/html; charset=utf-8', '.js':'application/javascript', '.css':'text/css', '.json':'application/json', '.svg':'image/svg+xml' };
      res.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream' });
      res.end(await fs.readFile(file));
    } catch { res.writeHead(404); res.end(await fs.readFile(path.join(root, '404.html'))); }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  let browser;
  try {
    const origin = `http://127.0.0.1:${server.address().port}`;
    browser = await puppeteer.launch({ executablePath:process.env.CHROME_BIN || process.argv[2], headless:'new', args:['--no-sandbox'] });
    const page = await browser.newPage();
    await page.setJavaScriptEnabled(false);
    for (const route of config.routes) {
      const response = await page.goto(origin + route.path);
      assert.equal(response.status(), 200);
      const head = await page.evaluate(() => ({ canonical:[...document.querySelectorAll('link[rel="canonical"]')].map(el => el.href), robots:document.querySelector('meta[name="robots"]').content, title:document.title, h1:document.querySelector('#app h1')?.textContent }));
      assert.deepEqual(head.canonical, [new URL(route.path, config.baseUrl).href]);
      assert.equal(head.robots.includes('noindex'), !route.index);
      if (route.index) assert.ok(head.h1?.trim(), `No static content at ${route.path}`);
    }
    assert.equal((await page.goto(origin + '/does-not-exist')).status(), 404);
    await page.setJavaScriptEnabled(true);
    await page.setRequestInterception(true);
    page.on('request', req => req.url().startsWith(origin) || req.url().startsWith('data:') ? req.continue() : req.abort());
    await page.evaluateOnNewDocument(() => localStorage.setItem('locale', 'zh-CN'));
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(origin, {waitUntil:'networkidle0'});
    await page.waitForSelector('a[href="/faq"]');
    assert.equal(await page.$$eval('#app-toast', els => els.length), 1);
    await page.click('a[href="/faq"]');
    await page.waitForFunction(() => location.pathname === '/faq' && document.title.includes('常见问题'));
    assert.equal(await page.$eval('link[rel="canonical"]', el => el.href), config.baseUrl + '/faq');
    assert.equal(await page.$('#site-schema'), null);
    await page.evaluate(() => { document.querySelector('#app').__vue_app__.config.globalProperties.$i18n.locale = 'en'; });
    await page.waitForFunction(() => document.title.includes('ChatLite FAQ') && document.documentElement.lang === 'en');
    assert.match(await page.$eval('meta[name="description"]', el => el.content), /supported platforms/);
    await page.click('a[href="/"]');
    await page.waitForFunction(() => location.pathname === '/' && !!document.querySelector('#site-schema'));
    const schema = await page.$eval('#site-schema', el => JSON.parse(el.textContent));
    assert.equal(schema.inLanguage, 'en');
    await page.goto(origin + '/features/', {waitUntil:'networkidle0'});
    assert.equal(await page.$eval('meta[name="robots"]', el => el.content.includes('noindex')), false);
    assert.equal(await page.$eval('link[rel="canonical"]', el => el.href), config.baseUrl + '/features');
    assert.deepEqual(errors, []);
    console.log('PASS: static HTML without JavaScript, public content, private noindex, unique canonical, real 404, SPA navigation, locale updates and schema lifecycle.');
  } finally { if (browser) await browser.close(); await new Promise(resolve => server.close(resolve)); }
})().catch(error => { console.error(error); process.exitCode = 1; });
