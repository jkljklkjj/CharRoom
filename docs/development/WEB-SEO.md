# Web SEO 与部署验证

SEO 配置由 `web/site.routes.json` 管理；`src/services/pageSeo.ts` 更新客户端路由和语言切换后的元信息，`scripts/build-route-heads.cjs` 为每个已知路由生成独立初始 HTML。标题与描述在三份 locale 文件的 `seo.*` 中维护。

## 页面范围

- `/`、`/features`、`/faq` 可以索引，站点地图仅包含这三个规范 URL。
- `/app`、`/verify`、`/admin`、`/copilot` 初始 HTML 和运行时均为 `noindex,follow`，不进入 sitemap。robots.txt 允许抓取，以便搜索引擎看到 noindex；访问控制仍由认证与后端权限负责。
- `/compare` 暂时 noindex。现有对竞品和加密能力的描述需要单独核实，不将未核实内容作为搜索入口。
- Nginx 对未知地址返回真实 404，前端也有本地导航的不存在页。私有入口不包含首页结构化数据；删除了未实现的 SearchAction 和 iOS 支持声明。
- 不再使用构建时间作为 sitemap lastmod；没有可靠内容修订日期时省略。canonical 不含查询参数或 fragment，OG URL 与 canonical 一致。
- 目前语言选择仍使用同一 URL 的浏览器/本地偏好，没有独立语言 URL，因此不伪造 hreflang。发布静态内容固定中文，用户进入后仍可按现有逻辑切换语言。

## 构建与验证

在 `CharRoom/web` 执行：

```text
npm run build
npm run build:seo
node --test tests/seo.test.cjs
node tests/seo-browser.cjs <Chrome-or-Chromium-executable>
```

`build` 生成每个路由的 head；面向生产的完整 SEO 构建使用 `build:seo`，需预先设置 `CHROME_BIN` 为已安装的 Chrome/Chromium 可执行文件。CI 可使用预装浏览器，本脚本不会下载浏览器。预渲染只访问本机 dist，只渲染可索引公开页，固定中文、亮色，不带真实账号，不请求外部 API。任何页面失败都会使命令失败并释放浏览器和本地服务器，不再悄悄发布不完整产物。

预渲染输出直接写入 `dist/index.html`、`dist/features/index.html`、`dist/faq/index.html`；私有路由保持无账号数据的入口 shell。原有脚本误把首页写到 `dist/index.html/index.html` 的路径错误已修复。生成产物不手改、不提交。

部署 `build:seo` 产生的整个 dist，并使用本仓库 `web/nginx.conf`；其他托管服务需保持已知路由静态文件优先、未知 URL 404，不能把所有地址都返回首页。canonical 与 sitemap 的正式域名来自同一配置；更换域名时修改配置及 index.html 默认分享图片/站点数据，部署前检查结果，不再通过单独 SITE_BASE_URL 覆盖造成域名不一致。

Google Search Console / 百度站长平台的站点验证、sitemap 提交、实际抓取和收录需通过站点所有者账号在部署后完成。本次不代替账号操作，也不承诺搜索排名。发布后确认首页、FAQ、功能页 HTTP 200，未知页 404，私有页 noindex；检查公开 HTML 正文无需 JavaScript 即可读取。

参考：[Google JavaScript SEO](https://developers.google.com/search/docs/crawling-indexing/javascript/javascript-seo-basics)、[sitemap 与 lastmod](https://developers.google.com/search/docs/crawling-indexing/sitemaps/build-sitemap)、[noindex](https://developers.google.com/search/docs/crawling-indexing/block-indexing)。

跨端评估：本轮只改变 Web 搜索、链接分享和部署输出，原生桌面、CLI、Android 不被网页搜索抓取，无需移植 SEO 元信息；聊天、ACK、本地缓存和接口协议未变更。

## 本次验证（2026-10-28）

Web 前置检查通过；120 项隔离测试全部通过（含新增 4 项 SEO 测试）；类型检查、生产构建和三个公开页预渲染通过。Edge 无头浏览器验证了禁用 JavaScript 后的公开正文、所有入口初始 canonical/noindex、404、单页导航、语言切换、结构化数据增删和尾斜杠路径。构建首次遇到协议生成文件的临时文件访问错误，重跑完整构建通过，未修改协议生成逻辑。

本机没有 Nginx/Docker，未执行 `nginx -t` 或真实 Nginx HTTP 验证；404 状态测试使用本地静态测试服务器。未部署、未提交搜索引擎站长平台，实际收录与线上响应需部署后检查。原生客户端和后端本轮无改动，未重复执行其构建。
