const http = require('http');
const path = require('path');
const fs = require('fs/promises');
const url = require('url');
// Use puppeteer-core so CI can provide a system Chrome/Chromium binary.
const puppeteer = require('puppeteer-core');

const distDir = path.resolve(__dirname, '..', 'dist');
const routesFile = path.resolve(__dirname, '..', 'site.routes.json');

// Simple static file server for dist
function createStaticServer(root, port = 4321) {
  const server = http.createServer(async (req, res) => {
    try {
      const parsed = url.parse(req.url);
      let filePath = path.join(root, decodeURIComponent(parsed.pathname));
      // if path ends with / serve index.html
      const stat = await fs.stat(filePath).catch(() => null);
      if (!stat) {
        // try adding .html or index.html
        if (filePath.endsWith('/')) filePath = path.join(filePath, 'index.html');
        else filePath = filePath + '.html';
      } else if (stat.isDirectory()) {
        filePath = path.join(filePath, 'index.html');
      }

      const data = await fs.readFile(filePath);
      const ext = path.extname(filePath).toLowerCase();
      const types = { '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.png': 'image/png', '.jpg': 'image/jpeg', '.svg': 'image/svg+xml', '.json': 'application/json' };
      res.writeHead(200, { 'Content-Type': types[ext] || 'application/octet-stream' });
      res.end(data);
    } catch (e) {
      res.writeHead(404);
      res.end('Not found');
    }
  });
  return new Promise((resolve) => server.listen(4321, () => resolve(server)));
}

async function prerender() {
  const routesJson = JSON.parse(await fs.readFile(routesFile, 'utf8'));
  const routes = routesJson.routes.map(r => r.path);

  const server = await createStaticServer(distDir, 4321);
  // Prefer an explicit executable path provided by CI via CHROME_BIN or CHROME_PATH.
  // If not provided, attempt to launch without an executablePath (may fail if no browser available).
  const executablePath = process.env.CHROME_BIN || process.env.CHROME_PATH || process.env.PUPPETEER_EXECUTABLE_PATH;
  const launchOptions = { args: ['--no-sandbox','--disable-setuid-sandbox'] };
  if (executablePath) launchOptions.executablePath = executablePath;
  const browser = await puppeteer.launch(launchOptions);
  const page = await browser.newPage();

  for (const route of routes) {
    const target = `http://127.0.0.1:4321${route}`;
    console.log('Prerendering', target);
    try {
      await page.goto(target, { waitUntil: 'networkidle0', timeout: 60000 });
      const html = await page.content();
      const outPath = path.join(distDir, route === '/' ? 'index.html' : route.replace(/^\//, ''), 'index.html');
      const outDir = path.dirname(outPath);
      await fs.mkdir(outDir, { recursive: true });
      await fs.writeFile(outPath, html, 'utf8');
      console.log('Wrote', outPath);
    } catch (e) {
      console.error('Failed prerender', route, e && e.message);
    }
  }

  await browser.close();
  server.close();
}

prerender().catch((e) => { console.error(e); process.exit(1); });
