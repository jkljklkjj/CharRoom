const http = require('node:http');
const path = require('node:path');
const fs = require('node:fs/promises');
const puppeteer = require('puppeteer-core');
const { routes } = require('../site.routes.json');
const dist = path.resolve(__dirname, '../dist');

async function prerender() {
  let browser;
  const server = http.createServer(async (req, res) => {
    try {
      const pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
      let file = path.resolve(dist, '.' + pathname);
      if (file !== dist && !file.startsWith(dist + path.sep)) throw new Error('Invalid path');
      const stat = await fs.stat(file);
      if (stat.isDirectory()) file = path.join(file, 'index.html');
      const types = { '.html':'text/html; charset=utf-8', '.js':'application/javascript', '.css':'text/css', '.svg':'image/svg+xml', '.png':'image/png', '.json':'application/json' };
      res.writeHead(200, { 'Content-Type':types[path.extname(file)] || 'application/octet-stream' });
      res.end(await fs.readFile(file));
    } catch {
      res.writeHead(404); res.end('Not found');
    }
  });
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  try {
    const executablePath = process.env.CHROME_BIN || process.env.CHROME_PATH || process.env.PUPPETEER_EXECUTABLE_PATH;
    if (!executablePath) throw new Error('Set CHROME_BIN to a system Chrome/Chromium executable before prerendering');
    browser = await puppeteer.launch({ executablePath, headless: 'new', args: ['--no-sandbox', '--disable-setuid-sandbox'] });
    const page = await browser.newPage();
    await page.setRequestInterception(true);
    page.on('request', request => request.url().startsWith(`http://127.0.0.1:${server.address().port}/`) || request.url().startsWith('data:') ? request.continue() : request.abort());
    await page.evaluateOnNewDocument(() => { localStorage.setItem('locale', 'zh-CN'); localStorage.setItem('theme', 'light'); });
    // Read every route before replacing any file: navigation must use the original production shells.
    const output = [];
    for (const route of routes.filter(route => route.index)) {
      await page.goto(`http://127.0.0.1:${server.address().port}${route.path}`, { waitUntil:'networkidle0', timeout:30000 });
      await page.waitForFunction(() => document.querySelector('#app h1')?.textContent.trim().length > 0);
      await page.evaluate(() => {
        // Runtime mounts these once. Do not serialize a second toast host or theme preference.
        document.querySelector('#app-toast')?.remove();
        document.documentElement.removeAttribute('data-theme');
      });
      output.push([path.join(dist, route.path.slice(1), 'index.html'), await page.content()]);
    }
    for (const [file, html] of output) {
      await fs.mkdir(path.dirname(file), { recursive:true });
      await fs.writeFile(file, html);
      console.log('Prerendered', path.relative(dist, file));
    }
  } finally {
    if (browser) await browser.close();
    await new Promise(resolve => server.close(resolve));
  }
}
prerender().catch(error => { console.error(error); process.exitCode = 1; });
