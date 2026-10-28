#!/usr/bin/env node
/**
 * 把 SSR 渲染结果注入各 index 路由的 HTML。
 *
 * 输入：dist-ssr/entry-server.mjs（vite build --ssr 产物）、dist/<route>/index.html
 * 输出：对应 HTML 的 <div id="app" data-ssr>…</div>，客户端 main.ts 检测到 data-ssr
 *       后会用 createSSRApp 复用这份 DOM，而不是整棵重建。
 *
 * 只处理 site.routes.json 中 index=true 的路由；/app 等登录后页面继续走纯客户端渲染。
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const dist = path.resolve(__dirname, '../dist')
const ssrEntry = path.resolve(__dirname, '../dist-ssr/entry-server.mjs')

const { routes } = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../site.routes.json'), 'utf8'))
const targets = routes.filter(route => route.index)

if (!fs.existsSync(ssrEntry)) {
  console.error(`[ssr] 未找到 SSR 产物：${ssrEntry}，先执行 vite build --ssr`)
  process.exit(1)
}

const { render } = await import(ssrEntry)

let rendered = 0
for (const route of targets) {
  const file = path.join(dist, route.path.slice(1), 'index.html')
  if (!fs.existsSync(file)) {
    console.warn(`[ssr] 跳过（HTML 不存在）：${path.relative(dist, file)}`)
    continue
  }
  const html = await render(route.path)
  const shell = fs.readFileSync(file, 'utf8')
  if (shell.includes('data-ssr')) {
    console.log(`[ssr] 跳过（已渲染）：${route.path}`)
    continue
  }
  const empty = '<div id="app"></div>'
  if (!shell.includes(empty)) {
    throw new Error(`[ssr] ${route.path} 的模板里找不到空的 #app 容器`)
  }
  fs.writeFileSync(file, shell.replace(empty, `<div id="app" data-ssr>${html}</div>`), 'utf8')
  rendered += 1
  console.log(`[ssr] rendered ${route.path} (${Buffer.byteLength(html)} bytes)`)
}

console.log(`[ssr] done, ${rendered} route(s)`)
