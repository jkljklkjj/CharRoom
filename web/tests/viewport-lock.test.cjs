const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const read = file => fs.readFileSync(path.resolve(__dirname, '..', file), 'utf8')

const { loadTs } = require('./helpers.cjs')
const { renderHead } = require('../scripts/site-seo.cjs')
const config = require('../site.routes.json')

test('initial app HTML locks the root before scripts; public documents never inherit that lock', () => {
  const template = read('index.html')
  const app = renderHead(template, config.routes.find(route => route.path === '/app'))
  assert.match(app.match(/<html[^>]*>/)[0], /data-app-viewport/)
  for (const route of config.routes.filter(route => route.path !== '/app')) {
    assert.doesNotMatch(renderHead(app, route).match(/<html[^>]*>/)[0], /data-app-viewport/)
  }
})

test('SPA app entry resets outer scroll once; leaving app releases the root and body', async () => {
  const attributes = new Set(), classes = new Set(), positions = []
  const { exports: { syncPageViewport } } = await loadTs(path.resolve(__dirname, '../src/services/pageViewport.ts'), {
    document: {
      documentElement: { hasAttribute: name => attributes.has(name), toggleAttribute: (name, enabled) => enabled ? attributes.add(name) : attributes.delete(name) },
      body: { classList: { toggle: (name, enabled) => enabled ? classes.add(name) : classes.delete(name) } }
    }, window: { scrollTo: (...args) => positions.push(args) }
  })
  syncPageViewport('/app'); syncPageViewport('/app/')
  assert.equal(attributes.has('data-app-viewport'), true)
  assert.equal(classes.has('app-viewport'), true)
  assert.deepEqual(positions, [[0, 0]])
  syncPageViewport('/')
  assert.equal(attributes.size, 0); assert.equal(classes.size, 0)
})

test('inner scroll areas contain overscroll instead of chaining to the document', () => {
  assert.match(read('src/components/SidebarUsers.vue'), /\.directory-panel\{[^}]*overscroll-behavior:contain/)
  assert.match(read('src/components/ChatWindow.vue'), /\.messages\{[^}]*overscroll-behavior:contain/)
})
