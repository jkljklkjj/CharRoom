# 预渲染 CI

完整规范见 [Web SEO](../docs/development/WEB-SEO.md)。

工作目录为 `CharRoom/web`。使用 Node 与已安装的 Chrome/Chromium，设置 `CHROME_BIN`，再执行：

```text
npm ci
npm run build:seo
node --test tests/seo.test.cjs
node tests/seo-browser.cjs <Chrome-or-Chromium-executable>
```

`npm run build` 输出各路由独立元信息，但不执行浏览器预渲染；生产 SEO 发布用 `build:seo`。预渲染仅包含 `site.routes.json` 中 `index: true` 的公开页，固定中文，不读取真实账户。脚本不会下载浏览器，缺少浏览器或任一页面失败时返回非零退出码。

CI 应提供预装浏览器的 runner，将可执行文件路径写入任务级环境变量 `CHROME_BIN`，构建成功后才上传整个 `web/dist`。无需把本机路径提交到仓库。托管层使用 `nginx.conf` 或等效路由规则，未知 URL 必须返回 404，不能把所有请求都回退到首页。
