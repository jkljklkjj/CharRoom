// 把入口 CSS 内联进 HTML，消除首次渲染的阻塞请求。
//
// Lighthouse 会把 <link rel="stylesheet"> 计入 critical request chain：
// 即使只有 6KB，也要多一次 RTT（实测 170ms）。入口 CSS 是应用外壳样式，
// 基本都属于首屏关键路径，整体内联比拆分 critical/非 critical 更简单可靠。
//
// 必须在 build-route-heads.cjs 之前执行：该脚本以 dist/index.html 为模板
// 生成各路由的 HTML，内联后所有路由都会继承。
const fs = require('node:fs')
const path = require('node:path')

const dist = path.resolve(__dirname, '../dist')
const indexFile = path.join(dist, 'index.html')
const STYLESHEET_LINK = /<link[^>]*rel="stylesheet"[^>]*>/g

const html = fs.readFileSync(indexFile, 'utf8')
let inlined = 0
let bytes = 0

const result = html.replace(STYLESHEET_LINK, tag => {
  const href = /href="([^"]+\.css)"/.exec(tag)?.[1]
  if (!href) return tag
  const cssPath = path.join(dist, href.replace(/^\//, ''))
  if (!fs.existsSync(cssPath)) return tag
  const css = fs.readFileSync(cssPath, 'utf8')
  // 内联后不能再出现 </style>，否则会提前闭合样式块
  if (css.includes('</style')) return tag
  inlined += 1
  bytes += Buffer.byteLength(css)
  return `<style>${css}</style>`
})

fs.writeFileSync(indexFile, result)
console.log(`[inline-critical-css] inlined=${inlined} bytes=${bytes}`)
