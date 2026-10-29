#!/usr/bin/env node
/**
 * 把 SSR 渲染结果注入各 index 路由的 HTML。
 *
 * 输入：dist-ssr/entry-server.mjs（vite build --ssr 产物）、dist/<route>/index.html
 * 输出：对应 HTML 的 <div id="app" data-ssr>…</div>，客户端 main.ts 检测到 data-ssr
 *       后会用 createSSRApp 复用这份 DOM，而不是整棵重建。
 *
 * 公开索引页及 /app 的真实认证入口均预渲染。/app 保持 noindex，构建不读取账号数据。
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const dist = path.resolve(__dirname, '../dist')
const ssrEntry = path.resolve(__dirname, '../dist-ssr/entry-server.mjs')

const { routes } = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../site.routes.json'), 'utf8'))
const targets = routes.filter(route => route.index || route.path === '/app')
const manifest = JSON.parse(fs.readFileSync(path.join(dist, '.vite/ssr-manifest.json'), 'utf8'))

if (!fs.existsSync(ssrEntry)) {
  console.error(`[ssr] 未找到 SSR 产物：${ssrEntry}，先执行 vite build --ssr`)
  process.exit(1)
}

const { render } = await import(pathToFileURL(ssrEntry).href)

let rendered = 0
for (const route of targets) {
  const file = path.join(dist, route.path.slice(1), 'index.html')
  if (!fs.existsSync(file)) {
    console.warn(`[ssr] 跳过（HTML 不存在）：${path.relative(dist, file)}`)
    continue
  }
  const context = {}
  const html = await render(route.path, context)
  const shell = fs.readFileSync(file, 'utf8')
  if (shell.includes('data-ssr')) {
    console.log(`[ssr] 跳过（已渲染）：${route.path}`)
    continue
  }
  const empty = '<div id="app"></div>'
  if (!shell.includes(empty)) {
    throw new Error(`[ssr] ${route.path} 的模板里找不到空的 #app 容器`)
  }
  // 懒加载路由的 CSS 不在入口样式里；首帧必须连同真实组件样式一起输出。
  const styles = new Set()
  const scripts = new Set()
  for (const id of context.modules || []) {
    for (const asset of manifest[id] || []) {
      if (asset.endsWith('.css')) styles.add(asset)
      if (route.index && asset.endsWith('.js') && !shell.includes(`href="${asset}"`)) scripts.add(asset)
    }
  }
  const css = [...styles].map(asset => fs.readFileSync(path.join(dist, asset.replace(/^\//, '')), 'utf8')).join('\n')
  if (/<\/style/i.test(css)) throw new Error(`[ssr] ${route.path} 的样式含有非法结束标签`)
  const result = shell.replace(empty, `<div id="app" data-ssr>${html}</div>`)
    .replace('</head>', `${[...scripts].map(asset => `<link rel="modulepreload" crossorigin data-ssr-preload href="${asset}">`).join('')}<style data-ssr-styles>${css}</style></head>`)
  fs.writeFileSync(file, result, 'utf8')
  rendered += 1
  console.log(`[ssr] rendered ${route.path} (${Buffer.byteLength(html)} bytes)`)
}

console.log(`[ssr] done, ${rendered} route(s)`)
