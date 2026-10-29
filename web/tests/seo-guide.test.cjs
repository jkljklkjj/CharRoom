const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { loadTs } = require('./helpers.cjs')
const config = require('../site.routes.json')

test('public breadcrumbs use canonical URLs and translations, and never describe private pages', async () => {
  const { exports: seo } = await loadTs(path.resolve(__dirname, '../src/services/pageSeo.ts'), {}, { '../../site.routes.json': { default:config } })
  for (const locale of ['zh-CN', 'en', 'ja']) {
    const messages = require(`../src/locales/${locale}.json`)
    const t = key => messages[key]
    for (const route of config.routes) {
      const schema = seo.pageBreadcrumb(route.path + (route.path === '/' ? '' : '/'), t)
      if (!route.index || route.path === '/') { assert.equal(schema, null); continue }
      assert.equal(schema['@type'], 'BreadcrumbList')
      assert.deepEqual(Array.from(schema.itemListElement, entry => entry.item), [config.baseUrl + '/', config.baseUrl + route.path])
      assert.deepEqual(Array.from(schema.itemListElement, entry => entry.name), [t('seo.breadcrumb.home'), t(`${route.key}.title`)])
      assert.deepEqual(Array.from(schema.itemListElement, entry => entry.position), [1, 2])
    }
    assert.equal(seo.pageBreadcrumb('/does-not-exist', t), null)
  }
})

test('AI-facing site guide has current repositories and no removed transport or private search targets', () => {
  const guide = fs.readFileSync(path.resolve(__dirname, '../public/llms.txt'), 'utf8')
  assert.ok(guide.includes('https://github.com/jkljklkjj/CharRoom'))
  assert.ok(guide.includes('https://gitee.com/zhenye55/chat-room'))
  assert.ok(!/zhenye\/CharRoom|WebSocket|wss:|\/verify|\/compare/.test(guide))
  assert.ok(guide.includes('端到端加密'))
})

test('explicit index.html URLs redirect without intercepting internal Nginx index resolution', () => {
  for (const file of ['../nginx.conf', '../../../chat-room/docker/nginx/nginx.conf.template']) {
    const nginx = fs.readFileSync(path.resolve(__dirname, file), 'utf8')
    assert.ok(nginx.includes('absolute_redirect off;'))
    assert.ok(nginx.includes('$request_uri ~ "^/index\\.html(?:\\?|$)"'))
    assert.ok(nginx.includes('(?<static_index_route>features|faq|compare|app|verify|reset|admin|copilot)'))
    assert.ok(nginx.includes('return 301 /$static_index_route$is_args$args;'))
  }
})

test('robots uses supported directives while ARD remains discoverable through HTML', () => {
  const robots = fs.readFileSync(path.resolve(__dirname, '../public/robots.txt'), 'utf8')
  for (const line of robots.trim().split(/\r?\n/)) assert.match(line, /^(User-agent|Allow|Disallow|Sitemap):/)
  assert.ok(robots.includes('Sitemap: ' + config.baseUrl + '/sitemap.xml'))
  const html = fs.readFileSync(path.resolve(__dirname, '../index.html'), 'utf8')
  assert.ok(html.includes('rel="ard" href="/.well-known/ard.json"'))
})
