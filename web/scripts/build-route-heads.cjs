const fs = require('node:fs')
const path = require('node:path')
const config = require('../site.routes.json')
const { renderHead } = require('./site-seo.cjs')
const dist = path.resolve(__dirname, '../dist')
const html = fs.readFileSync(path.join(dist, 'index.html'), 'utf8')
for (const route of config.routes) {
  const file = path.join(dist, route.path.slice(1), 'index.html')
  fs.mkdirSync(path.dirname(file), { recursive: true })
  fs.writeFileSync(file, renderHead(html, route))
}
const missing = renderHead(html, { path: '/', key: 'missing', index: false })
fs.writeFileSync(path.join(dist, '404.html'), missing)
