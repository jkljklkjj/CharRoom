预渲染（Prerender）CI 使用说明

目标
- 提供可选的预渲染脚本，用于在需要时把 `dist/` 中的路由生成静态 HTML（`dist/<route>/index.html`），以便 NGINX 直接托管、搜索引擎抓取无需执行 JS。

说明
- 本仓库包含 `scripts/prerender.cjs`（可选），它使用 `puppeteer-core` 将 `dist/` 中的路由渲染为静态 HTML（路由来自 `site.routes.json`）。
- 注意：`npm run build` 默认不会执行预渲染步骤，构建不会自动触发 Chromium 下载或运行。若需预渲染，请手动运行 `npm run prerender` 或在 CI 中单独调用该脚本。
- `puppeteer-core` 本身不会下载 Chromium；若要运行预渲染，需要在运行环境提供 Chrome/Chromium 二进制，并通过 `CHROME_BIN` / `CHROME_PATH` / `PUPPETEER_EXECUTABLE_PATH` 指定路径。

推荐做法（GitHub Actions 示例）
- 两种可选方案：1) 在 runner 上安装 Chromium 并设置 `CHROME_BIN`；2) 在一个预装 Chrome 的容器中运行构建。

示例 A：Ubuntu runner 上安装 Chromium（适用于 github actions）

```yaml
name: Build and Prerender
on: [push]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Install dependencies
        run: |
          sudo apt-get update
          sudo apt-get install -y chromium-browser
          export CHROME_BIN=/usr/bin/chromium-browser
          export PUPPETEER_SKIP_DOWNLOAD=true
          npm ci
      - name: Build (includes prerender)
        run: |
          export CHROME_BIN=/usr/bin/chromium-browser
          npm run build
      - name: Upload artifacts
        uses: actions/upload-artifact@v4
        with:
          name: dist
          path: dist
```

说明：`PUPPETEER_SKIP_DOWNLOAD=true` 可防止 `puppeteer-core`/`puppeteer` 在 `npm install` 时尝试下载 Chromium（CI 可网络受限时推荐）。

示例 B：在容器中运行（使用官方带 Chrome 的镜像）

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    container:
      image: zenika/alpine-chrome:with-node
    steps:
      - uses: actions/checkout@v4
      - name: Install deps & build
        run: |
          export CHROME_BIN=/usr/bin/chromium-browser
          export PUPPETEER_SKIP_DOWNLOAD=true
          npm ci
          npm run build
      - name: Upload dist
        uses: actions/upload-artifact@v4
        with:
          name: dist
          path: dist
```

注意事项
- 如果你使用私有/self-hosted CI，请确保 Chrome 可执行文件路径正确，并在运行 `npm ci` 之前设置 `PUPPETEER_SKIP_DOWNLOAD=true`。
- 如果希望在本地或 CI 中让 `prerender.cjs` 指向不同的 Chrome 可执行文件，请设置 `PUPPETEER_EXECUTABLE_PATH` 或 `CHROME_BIN` 环境变量。

自托管 Prerender 服务（可选）
- 如果不希望在 CI 下载或安装 Chrome，可使用 Prerender.io 等服务，并在 `nginx.conf.template` 中将爬虫代理到该服务（已在模板中加入示例代理逻辑，使用 `PRERENDER_IO_TOKEN` 环境变量）。

故障排查
- "npm install" 报错提示无法下载 Chromium：设置 `PUPPETEER_SKIP_DOWNLOAD=true` 并确保 CI 提前安装 Chrome。
- prerender 输出中页面渲染不完整：检查 `site.routes.json` 是否包含所有需要预渲染的路由，或在 `prerender.cjs` 中延长 `page.goto` 的 `timeout` 或 `waitUntil` 选项。

需要我做的额外工作
- 我可以把上面的 GitHub Actions workflow 文件添加到 `.github/workflows/ci-prerender.yml` 并提交。
- 我也可以把 `prerender.cjs` 改成使用 `puppeteer-core` + 更丰富的等待逻辑（例如等待某个 DOM selector 出现），并把 CI 示例写成可直接使用的 workflow 文件。