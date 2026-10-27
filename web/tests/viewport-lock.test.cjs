const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const read = file => fs.readFileSync(path.resolve(__dirname, '..', file), 'utf8')

// 移动端顶部安全区留白要保留（避免网址栏压住“轻聊”标题），
// 但文档本身不能可滚动：否则下拉拖拽/橡皮筋会把这块留白拖出来。
test('app view keeps the safe-area placeholder but locks document scrolling', () => {
  const css = read('src/styles.css')
  assert.match(css, /body \{[\s\S]*?padding-top: env\(safe-area-inset-top, 0\)/)
  const lock = css.match(/body\.app-viewport \{([\s\S]*?)\n\}/)
  assert.ok(lock, '缺少 body.app-viewport 规则')
  const rule = lock[1]
  assert.match(rule, /box-sizing: border-box/, 'padding 必须计入 100dvh，否则文档会多出可拖拽的溢出高度')
  assert.match(rule, /height: 100dvh/)
  assert.match(rule, /overflow: hidden/)
  assert.match(rule, /overscroll-behavior: none/)
})

test('chat app toggles the lock and keeps heights inside the padded box', () => {
  const app = read('src/views/WebApp.vue')
  assert.match(app, /document\.body\.classList\.add\('app-viewport'\)/)
  assert.match(app, /document\.body\.classList\.remove\('app-viewport'\)/)
  // 100dvh 写在 body 上会连同 padding 一起溢出，应用容器只能用百分比高度
  assert.doesNotMatch(app, /height:100dvh/)
  assert.doesNotMatch(app, /height: 100dvh/)
  assert.match(app, /\.app-wrap\{height:100%;/)
  assert.match(app, /\.app-main\.mobile-view \{[\s\S]*?height: 100%;/)
  assert.match(app, /\.app-main\.mobile-view \.sidebar \{[\s\S]*?height: 100%;/)
  // 固定在视口上的聊天视图也要让开顶部安全区
  assert.match(app, /\.app-main\.mobile-view \.chat-view \{[\s\S]*?top: env\(safe-area-inset-top, 0\)/)
})

test('inner scroll areas contain overscroll instead of chaining to the document', () => {
  assert.match(read('src/components/SidebarUsers.vue'), /\.directory-panel\{[^}]*overscroll-behavior:contain/)
  assert.match(read('src/components/ChatWindow.vue'), /\.messages\{[^}]*overscroll-behavior:contain/)
})
