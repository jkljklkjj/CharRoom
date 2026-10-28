const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const config = require('../site.routes.json')
const { renderHead, sitemap } = require('../scripts/site-seo.cjs')
const html = fs.readFileSync(path.resolve(__dirname, '../index.html'), 'utf8')

test('sitemap contains only public landing, features and FAQ with no fabricated modification date', () => {
  const xml = sitemap()
  assert.equal((xml.match(/<loc>/g) || []).length, 3)
  for (const route of config.routes) assert.equal(xml.includes(new URL(route.path, config.baseUrl).href + '</loc>'), route.index)
  assert.ok(!xml.includes('lastmod'))
})
test('each route has exactly one canonical, title, description and robots in initial HTML', () => {
  const titles = new Set()
  for (const route of config.routes) {
    const result = renderHead(html, route)
    assert.equal((result.match(/rel="canonical"/g) || []).length, 1)
    assert.ok(result.includes(`href="${new URL(route.path, config.baseUrl).href}"`))
    assert.equal((result.match(/name="robots"/g) || []).length, 1)
    assert.ok(result.includes(`content="${route.index ? 'index,follow,max-image-preview:large' : 'noindex,follow'}"`))
    const title = result.match(/<title>(.*?)<\/title>/)[1]
    assert.ok(title && !title.includes('undefined'))
    assert.ok(!titles.has(title)); titles.add(title)
    assert.equal(result.includes('id="site-schema"'), route.path === '/')
  }
})
test('all route metadata translations are present and nonempty', () => {
  for (const locale of ['zh-CN', 'en', 'ja']) {
    const messages = require(`../src/locales/${locale}.json`)
    for (const key of [...config.routes.map(route => route.key), 'missing']) {
      assert.ok(messages[`seo.${key}.title`]?.trim())
      assert.ok(messages[`seo.${key}.description`]?.trim())
    }
  }
})
test('structured data contains no unsupported search action or operating systems', () => {
  const data = JSON.parse(html.match(/<script id="site-schema"[^>]*>(.*?)<\/script>/s)[1])
  assert.equal(data['@type'], 'WebSite')
  assert.equal(data.url, new URL('/', config.baseUrl).href)
  assert.ok(!data.potentialAction)
  assert.ok(!html.includes('iOS'))
})
