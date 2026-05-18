const fs = require('fs');
const path = require('path');

const projectRoot = path.resolve(__dirname, '..');
const publicDir = path.join(projectRoot, 'public');
const protoSource = path.join(projectRoot, 'src', 'proto', 'message.proto');
const protoTargetDir = path.join(publicDir, 'proto');
const protoTarget = path.join(protoTargetDir, 'message.proto');
const routesConfigPath = path.join(projectRoot, 'site.routes.json');

function ensureDir(dirPath) {
  fs.mkdirSync(dirPath, { recursive: true });
}

function escapeXml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function joinUrl(baseUrl, routePath) {
  const normalizedBaseUrl = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`;
  if (routePath === '/') {
    return normalizedBaseUrl;
  }
  return new URL(routePath.replace(/^\//, ''), normalizedBaseUrl).toString();
}

function formatDate(date) {
  return date.toISOString().slice(0, 10);
}

function main() {
  const config = JSON.parse(fs.readFileSync(routesConfigPath, 'utf8'));
  const baseUrl = (process.env.SITE_BASE_URL || config.baseUrl || 'https://www.chatlite.xin').replace(/\/$/, '');
  const routes = Array.isArray(config.routes) ? config.routes : [];
  const lastmod = formatDate(new Date());

  ensureDir(publicDir);
  ensureDir(protoTargetDir);

  if (fs.existsSync(protoSource)) {
    fs.copyFileSync(protoSource, protoTarget);
  }

  const sitemapEntries = routes
    .map((route) => {
      if (!route || typeof route.path !== 'string') {
        return null;
      }

      const loc = escapeXml(joinUrl(baseUrl, route.path));
      const changefreq = route.changefreq ? `    <changefreq>${escapeXml(route.changefreq)}</changefreq>\n` : '';
      const priority = typeof route.priority === 'number' ? `    <priority>${route.priority.toFixed(1)}</priority>\n` : '';

      return [
        '  <url>',
        `    <loc>${loc}</loc>`,
        `    <lastmod>${lastmod}</lastmod>`,
        changefreq.trimEnd(),
        priority.trimEnd(),
        '  </url>'
      ]
        .filter(Boolean)
        .join('\n');
    })
    .filter(Boolean)
    .join('\n');

  const sitemap = `<?xml version="1.0" encoding="UTF-8"?>\n` +
    `<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n` +
    `${sitemapEntries}\n` +
    `</urlset>\n`;

  const robots = `User-agent: *\nAllow: /\nSitemap: ${baseUrl}/sitemap.xml\n`;

  fs.writeFileSync(path.join(publicDir, 'sitemap.xml'), sitemap, 'utf8');
  fs.writeFileSync(path.join(publicDir, 'robots.txt'), robots, 'utf8');

  console.log(`Generated sitemap.xml and robots.txt for ${baseUrl}`);
}

main();