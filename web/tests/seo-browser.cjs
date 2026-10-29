const { strict: assert } = require('node:assert');
const http = require('node:http');
const fs = require('node:fs/promises');
const path = require('node:path');
const puppeteer = require('puppeteer-core');
const config = require('../site.routes.json');
const { catalogPaths, resourceCatalog } = require('../scripts/resource-catalog.cjs');
const root = path.resolve(__dirname, '../dist');
(async () => {
  const server = http.createServer(async (req, res) => {
    try {
      const url = new URL(req.url, 'http://localhost');
      const indexRoute = config.routes.find(route => url.pathname === (route.path === '/' ? '/index.html' : route.path + '/index.html'));
      if (indexRoute) { res.writeHead(301, { Location:indexRoute.path + url.search }); res.end(); return; }
      let file = path.resolve(root, '.' + url.pathname);
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
    // Check actual build output over HTTP, including scanners asking for Markdown.
    for (const uri of catalogPaths) {
      const response = await fetch(origin + uri, { headers:{ Accept:'text/markdown' } });
      assert.equal(response.status, 200);
      assert.match(response.headers.get('content-type'), /^application\/json/);
      assert.deepEqual(await response.json(), resourceCatalog());
    }
    const robots = await (await fetch(origin + '/robots.txt')).text();
    assert.match(robots, /Sitemap: .*\/sitemap\.xml/);
    assert.doesNotMatch(robots, /^Agentmap:/m);
    for (const route of config.routes) {
      const indexPath = route.path === '/' ? '/index.html' : route.path + '/index.html';
      const response = await fetch(origin + indexPath + '?source=fixture', { redirect:'manual' });
      assert.equal(response.status, 301);
      assert.equal(response.headers.get('location'), route.path + '?source=fixture');
    }
    browser = await puppeteer.launch({ executablePath:process.env.CHROME_BIN || process.argv[2], headless:'new', args:['--no-sandbox'] });
    const page = await browser.newPage();
    await page.setJavaScriptEnabled(false);
    for (const route of config.routes) {
      const response = await page.goto(origin + route.path);
      assert.equal(response.status(), 200);
      const head = await page.evaluate(() => ({ canonical:[...document.querySelectorAll('link[rel="canonical"]')].map(el => el.href), robots:document.querySelector('meta[name="robots"]').content, title:document.title, h1:document.querySelector('#app h1')?.textContent }));
      assert.deepEqual(head.canonical, [new URL(route.path, config.baseUrl).href]);
      assert.equal(head.robots.includes('noindex'), !route.index);
      if (route.index) {
        assert.ok(head.h1?.trim(), `No static content at ${route.path}`);
        assert.equal(await page.$$eval('#app h1', elements => elements.length), 1);
        for (const href of ['https://github.com/jkljklkjj/CharRoom', 'https://gitee.com/zhenye55/chat-room']) assert.ok(await page.$(`a[href="${href}"]`));
      }
      if (route.index && route.path !== '/') {
        const breadcrumb = await page.$eval('#breadcrumb-schema', element => JSON.parse(element.textContent));
        assert.deepEqual(breadcrumb.itemListElement.map(item => item.item), [config.baseUrl + '/', config.baseUrl + route.path]);
        assert.equal(await page.$eval('.public-breadcrumbs [aria-current="page"]', element => element.textContent), breadcrumb.itemListElement[1].name);
        const other = route.path === '/faq' ? '/features' : '/faq';
        assert.ok(await page.$(`a[href="${other}"]`));
      } else assert.equal(await page.$('#breadcrumb-schema'), null);
    }
    assert.equal((await page.goto(origin + '/does-not-exist')).status(), 404);
    await page.setJavaScriptEnabled(true);
    await page.setRequestInterception(true);
    page.on('request', req => req.url().startsWith(origin) || req.url().startsWith('data:') ? req.continue() : req.abort());
    await page.evaluateOnNewDocument(() => { if (!localStorage.getItem('locale')) localStorage.setItem('locale', 'zh-CN'); });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(origin, {waitUntil:'networkidle0'});
    await page.waitForSelector('a[href="/faq"]');
    assert.equal(await page.$$eval('#app-toast', els => els.length), 1);
    await page.click('a[href="/faq"]');
    await page.waitForFunction(() => location.pathname === '/faq' && document.title.includes('常见问题'));
    assert.equal(await page.$eval('link[rel="canonical"]', el => el.href), config.baseUrl + '/faq');
    assert.equal(await page.$('#site-schema'), null);
    // Language packs are lazy now; exercise the real bootstrap path that loads the pack.
    await page.evaluate(() => localStorage.setItem('locale', 'en'));
    await page.reload({waitUntil:'networkidle0'});
    await page.waitForFunction(() => document.title.includes('ChatLite FAQ') && document.documentElement.lang === 'en');
    assert.match(await page.$eval('meta[name="description"]', el => el.content), /supported platforms/);
    assert.equal(await page.$eval('#breadcrumb-schema', el => JSON.parse(el.textContent).itemListElement[0].name), 'ChatLite home');
    await page.click('a[href="/"]');
    await page.waitForFunction(() => location.pathname === '/' && !!document.querySelector('#site-schema'));
    const schema = await page.$eval('#site-schema', el => JSON.parse(el.textContent));
    assert.equal(schema.inLanguage, 'en');
    assert.equal(await page.$('#breadcrumb-schema'), null);
    await page.goto(origin + '/features/', {waitUntil:'networkidle0'});
    assert.equal(await page.$eval('meta[name="robots"]', el => el.content.includes('noindex')), false);
    assert.equal(await page.$eval('link[rel="canonical"]', el => el.href), config.baseUrl + '/features');
    await page.click('a[href="/#downloads"]');
    await page.waitForFunction(() => location.pathname === '/' && location.hash === '#downloads' && document.querySelector('#downloads').getBoundingClientRect().top < innerHeight);
    await page.goto(origin + '/app', { waitUntil:'domcontentloaded' });
    await page.waitForFunction(() => !!document.querySelector('#app').__vue_app__);
    assert.equal(await page.$('#breadcrumb-schema'), null);
    assert.equal(await page.$('#site-schema'), null);
    assert.ok(await page.$eval('meta[name="robots"]', element => element.content.includes('noindex')));
    assert.deepEqual(errors, []);
    console.log('PASS: static HTML without JavaScript, public content, private noindex, unique canonical, real 404, SPA navigation, locale updates and schema lifecycle.');
  } finally { if (browser) await browser.close(); await new Promise(resolve => server.close(resolve)); }
})().catch(error => { console.error(error); process.exitCode = 1; });
