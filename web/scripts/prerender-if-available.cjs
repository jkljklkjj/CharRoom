/**
 * 预渲染开关：构建镜像里装了 Chromium（Dockerfile builder 阶段），
 * 本地没装也不会让构建失败——检测不到可执行文件就跳过，保持 SPA 外壳。
 *
 * 预渲染后 dist/index.html 的 #app 里已经是真实内容，后续 inline-shell 会自动跳过。
 */
const hasChrome = process.env.CHROME_BIN || process.env.CHROME_PATH || process.env.PUPPETEER_EXECUTABLE_PATH

if (!hasChrome) {
  console.log('[prerender] skipped (未设置 CHROME_BIN / CHROME_PATH / PUPPETEER_EXECUTABLE_PATH)')
  process.exit(0)
}

require('./prerender.cjs')
