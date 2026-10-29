const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const config = require('../site.routes.json')
const { resourceCatalog, catalogPaths } = require('../scripts/resource-catalog.cjs')

test('ARD catalog describes only real public resources with valid discovery fields', () => {
  const catalog = resourceCatalog()
  assert.deepEqual(catalog.entries.map(entry => new URL(entry.url).pathname), config.routes.filter(route => route.index).map(route => route.path))
  assert.equal(new Set(catalog.entries.map(entry => entry.identifier)).size, catalog.entries.length)
  for (const entry of catalog.entries) {
    assert.ok(entry.identifier.startsWith(`urn:air:${new URL(config.baseUrl).hostname}:`))
    assert.match(entry.identifier, /^urn:air:[a-zA-Z0-9.-]+(:[a-zA-Z0-9._-]+)+$/)
    assert.ok(entry.displayName.trim())
    assert.equal(entry.type, 'text/html')
    assert.equal(new URL(entry.url).origin, new URL(config.baseUrl).origin)
    assert.equal('data' in entry, false)
    assert.equal('trustManifest' in entry, false)
    assert.ok(entry.representativeQueries.length >= 2 && entry.representativeQueries.length <= 5)
  }
})

test('catalog discovery supports current ARD and legacy catalog scanners', () => {
  assert.deepEqual(catalogPaths, ['/.well-known/ard.json', '/.well-known/ai-catalog.json', '/ai-catalog.json'])
  const html = fs.readFileSync(path.resolve(__dirname, '../index.html'), 'utf8')
  assert.match(html, /<link rel="ard" href="\/\.well-known\/ard\.json"/)
  assert.match(html, /<link rel="ai-catalog" href="\/\.well-known\/ai-catalog\.json"/)
  for (const file of ['../nginx.conf', '../../../chat-room/docker/nginx/nginx.conf.template']) {
    const nginx = fs.readFileSync(path.resolve(__dirname, file), 'utf8')
    assert.match(nginx, /location ~ \^\/\(\?:\\\.well-known\/\(\?:ard\|ai-catalog\)\|ai-catalog\)\\\.json\$/)
    assert.match(nginx, /default_type application\/json;/)
    assert.match(nginx, /error_page 404 = @catalog_not_found;/)
  }
})
