/**
 * 首屏静态壳（LCP 优化）。
 *
 * 问题：index.html 里 `<div id="app"></div>` 是空的，落地页的内容要等
 * JS 下载 → 解析 → Vue 挂载后才出现。Lighthouse 报的 "元素渲染延迟 6.37s"
 * 主要就是这段等待；CSS 已经内联，剩下的关键路径是入口 JS。
 *
 * 做法：把落地页骨架（标题/正文/按钮/示例卡片）直接写进 index.html，
 * 让 LCP 元素在 HTML 解析完就能绘制；Vue 挂载后 #app 非空，
 * `#app:not(:empty) + .boot-shell { display:none }` 自动隐藏，无需额外 JS。
 *
 * 只处理 dist/index.html（根路由）。其余路由的 HTML 由 build-route-heads.cjs
 * 从 index.html 复制生成，因此本脚本必须排在它**之后**执行，否则会把落地页
 * 骨架带进 /app、/admin 等页面。
 */
const fs = require('node:fs')
const path = require('node:path')

const dist = path.resolve(__dirname, '../dist')
const indexFile = path.join(dist, 'index.html')
const MARKER = 'class="boot-shell"'

const STYLE = `
<style>
/* 落地页静态壳：仅首屏可见，Vue 挂载后由相邻选择器隐藏 */
.boot-shell{background:var(--bg);color:var(--text-primary);min-height:100vh;box-sizing:border-box}
.boot-shell .boot-container{width:min(1120px,calc(100% - 64px));margin-inline:auto}
.boot-shell .boot-header{display:flex;align-items:center;justify-content:space-between;gap:20px;padding-block:28px}
.boot-shell .boot-brand{display:flex;align-items:center;gap:10px;font-size:16px;font-weight:600}
.boot-shell .boot-brand i{width:26px;height:26px;border-radius:9px;background:var(--apricot);color:var(--action);display:grid;place-items:center;font-style:normal;font-size:13px}
.boot-shell .boot-nav{display:flex;align-items:center;gap:30px;font-size:13px;color:var(--text-secondary)}
.boot-shell .boot-nav .open{color:var(--text-primary);border-bottom:1px solid var(--surface-border-strong);padding:10px 0}
.boot-shell .boot-hero{display:grid;grid-template-columns:1fr 1fr;gap:100px;align-items:start;padding-block:58px 110px}
.boot-shell .boot-copy{padding-top:58px}
.boot-shell .boot-eyebrow{display:inline-block;border-left:2px solid var(--action);padding-left:10px;font-size:11px;letter-spacing:.08em;color:var(--muted)}
.boot-shell h1{font-size:clamp(36px,4.5vw,58px);letter-spacing:-.045em;line-height:1.32;font-weight:600;margin:24px 0}
.boot-shell h1 span{display:block}
.boot-shell .boot-lead{font-size:16px;line-height:1.9;max-width:380px;color:var(--text-secondary);margin:0}
.boot-shell .boot-cta{display:flex;gap:12px;flex-wrap:wrap;margin-top:30px}
.boot-shell .boot-btn{border-radius:12px;padding:14px 20px;font-size:14px;font-weight:600}
.boot-shell .boot-btn.primary{background:var(--action);color:var(--on-action)}
.boot-shell .boot-btn.secondary{border:1px solid var(--surface-border-strong);color:var(--text-primary)}
.boot-shell .boot-note{font-size:12px;color:var(--muted);margin:16px 0 0}
.boot-shell .boot-card{background:var(--panel);border:1px solid var(--surface-border-strong);border-radius:18px;overflow:hidden}
.boot-shell .boot-card header{display:flex;align-items:center;gap:12px;padding:18px 22px;border-bottom:1px solid var(--surface-border)}
.boot-shell .boot-card header b{display:block;font-size:15px;font-weight:600}
.boot-shell .boot-card header small{display:block;color:var(--muted);font-size:11px;margin-top:4px}
.boot-shell .boot-avatar{width:34px;height:34px;border-radius:12px;background:var(--apricot);color:var(--action);display:grid;place-items:center;font-size:13px}
.boot-shell .boot-thread{display:grid;gap:17px;padding:16px 22px 24px;background:var(--bg)}
.boot-shell .boot-time{text-align:center;color:var(--muted);font-size:11px;margin:0 0 3px}
.boot-shell .boot-row{display:flex;align-items:flex-start;gap:8px}
.boot-shell .boot-row.mine{justify-content:flex-end;padding-left:36px}
.boot-shell .boot-bubble{font-size:14px;max-width:82%;background:var(--panel);border:1px solid var(--surface-border);border-radius:14px;padding:9px 13px;line-height:1.6}
.boot-shell .boot-bubble.mine{background:var(--apricot);border-color:transparent}
.boot-shell .boot-compose{display:flex;align-items:center;justify-content:space-between;gap:8px;margin:14px 20px;color:var(--muted);font-size:12px}
.boot-shell .boot-send{display:grid;place-items:center;width:32px;height:32px;border-radius:11px;background:var(--action);color:var(--on-action);font-size:18px}
#app:not(:empty) + .boot-shell{display:none}
@media(max-width:900px){.boot-shell .boot-hero{gap:36px;padding-block:35px 75px}.boot-shell .boot-copy{padding-top:24px}.boot-shell .boot-nav{gap:16px}}
@media(max-width:640px){.boot-shell .boot-container{width:calc(100% - 40px)}.boot-shell .boot-header{padding-block:22px}.boot-shell .boot-hero{grid-template-columns:1fr;gap:36px;padding-block:26px 65px}.boot-shell .boot-copy{padding-top:0}.boot-shell h1{font-size:38px}.boot-shell .boot-card{display:none}.boot-shell .boot-nav span:not(.open){display:none}}
</style>`

const SHELL = `
<div class="boot-shell" aria-hidden="true">
  <header class="boot-header boot-container">
    <span class="boot-brand"><i>轻</i>轻聊</span>
    <nav class="boot-nav"><span>在轻聊的日常</span><span>下载客户端</span><span class="open">打开轻聊 ↗</span></nav>
  </header>
  <main class="boot-hero boot-container">
    <div class="boot-copy">
      <span class="boot-eyebrow">轻聊 / 日常聊天</span>
      <h1>聊点小事，<span>也聊点新鲜事。</span></h1>
      <p class="boot-lead">和朋友接着聊，遇见有趣的人，也让小助手搭把手。</p>
      <div class="boot-cta"><span class="boot-btn primary">打开轻聊 ↗</span><span class="boot-btn secondary">下载客户端</span></div>
      <p class="boot-note">打开浏览器，就能开始。</p>
    </div>
    <div class="boot-card">
      <header><span class="boot-avatar">林</span><div><b>小林</b><small>界面示意</small></div></header>
      <div class="boot-thread">
        <p class="boot-time">今天 · 18:24</p>
        <div class="boot-row"><span class="boot-bubble">下班去吃面？</span></div>
        <div class="boot-row mine"><span class="boot-bubble mine">走，还是上次那家 🍜</span></div>
        <div class="boot-row"><span class="boot-bubble">这次记得加蛋。</span></div>
      </div>
      <div class="boot-compose"><span>说点什么吧…</span><span class="boot-send">↗</span></div>
    </div>
  </main>
</div>`

let html = fs.readFileSync(indexFile, 'utf8')

const emptyApp = /<div id="app"[^>]*><\/div>/

if (html.includes(MARKER)) {
  console.log('[inline-shell] skipped (already present)')
} else if (!emptyApp.test(html)) {
  // 预渲染产物：#app 里已是真实内容，静态壳没有意义（且会被相邻选择器立即隐藏）
  console.log('[inline-shell] skipped (#app 已有预渲染内容)')
} else {
  html = html.replace('</head>', `${STYLE}\n</head>`)
  html = html.replace(emptyApp, match => `${match}${SHELL}`)
  fs.writeFileSync(indexFile, html)
  console.log(`[inline-shell] injected (${Buffer.byteLength(SHELL)} bytes shell + ${Buffer.byteLength(STYLE)} bytes style)`)
}
