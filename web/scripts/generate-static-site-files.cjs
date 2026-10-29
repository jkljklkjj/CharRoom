const fs = require('node:fs');
const path = require('node:path');
const { sitemap } = require('./site-seo.cjs');
const { baseUrl } = require('../site.routes.json');
const { resourceCatalog, catalogPaths } = require('./resource-catalog.cjs');
const root = path.resolve(__dirname, '..');
const publicDir = path.join(root, 'public');
fs.mkdirSync(path.join(publicDir, 'proto'), { recursive: true });
for (const name of ['message.proto', 'agent_stream.proto']) {
  fs.copyFileSync(path.join(root, 'src/proto', name), path.join(publicDir, 'proto', name));
}
// Omit lastmod when there is no reliable content revision; rebuilding is not a content edit.
fs.writeFileSync(path.join(publicDir, 'sitemap.xml'), sitemap());
// Private routes remain crawlable so crawlers can observe noindex. This is not access control.
fs.writeFileSync(path.join(publicDir, 'robots.txt'), `User-agent: *\nAllow: /\nSitemap: ${baseUrl}/sitemap.xml\n`);
const catalog = JSON.stringify(resourceCatalog(), null, 2) + '\n';
for (const uri of catalogPaths) {
  const file = path.join(publicDir, uri.slice(1));
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, catalog);
}
console.log(`Generated sitemap.xml, robots.txt and ARD catalogs for ${baseUrl}`);
