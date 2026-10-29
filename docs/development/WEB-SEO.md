# Web SEO 与部署验证

SEO 配置由 `web/site.routes.json` 管理；`src/services/pageSeo.ts` 更新客户端路由和语言切换后的元信息，`scripts/build-route-heads.cjs` 为每个已知路由生成独立初始 HTML。标题与描述在三份 locale 文件的 `seo.*` 中维护。

## 页面范围

- `/`、`/features`、`/faq` 可以索引，站点地图仅包含这三个规范 URL。
- `/app`、`/verify`、`/reset`、`/admin`、`/copilot` 初始 HTML 和运行时均为 `noindex,follow`，不进入 sitemap。robots.txt 允许抓取，以便搜索引擎看到 noindex；访问控制仍由认证与后端权限负责。
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

构建期 SSR 输出直接写入 `dist/index.html`、`dist/features/index.html`、`dist/faq/index.html` 和 `dist/app/index.html`；`/app` 只渲染无账号数据的真实认证入口，仍为 noindex，不进入 sitemap。其他私有路由保持无账号数据的入口 shell。原有脚本误把首页写到 `dist/index.html/index.html` 的路径错误已修复。生成产物不手改、不提交。

部署 `build:seo` 产生的整个 dist。独立 Web 容器使用本仓库 `web/nginx.conf`；聚合部署实际使用后端 `chat-room/docker/nginx/nginx.conf.template`，两处都必须保持已知路由静态文件优先、未知 URL 404，不能把所有地址都返回首页。canonical 与 sitemap 的正式域名来自同一配置；更换域名时修改配置及 index.html 默认分享图片/站点数据，部署前检查结果，不再通过单独 SITE_BASE_URL 覆盖造成域名不一致。

Google Search Console / 百度站长平台的站点验证、sitemap 提交、实际抓取和收录需通过站点所有者账号在部署后完成。本次不代替账号操作，也不承诺搜索排名。发布后确认首页、FAQ、功能页 HTTP 200，未知页 404，私有页 noindex；检查公开 HTML 正文无需 JavaScript 即可读取。

参考：[Google JavaScript SEO](https://developers.google.com/search/docs/crawling-indexing/javascript/javascript-seo-basics)、[sitemap 与 lastmod](https://developers.google.com/search/docs/crawling-indexing/sitemaps/build-sitemap)、[noindex](https://developers.google.com/search/docs/crawling-indexing/block-indexing)。

## ARD 资源发现清单

`scripts/resource-catalog.cjs` 从同一索引路由配置与中文元信息生成清单，`generate-static-site-files.cjs` 在构建时写出三个内容相同的 JSON：

- `/.well-known/ard.json`：现行 ARD 规范入口。
- `/.well-known/ai-catalog.json`：兼容旧版消费者。
- `/ai-catalog.json`：兼容直接请求根路径的扫描器。

HTML head 提供 `rel="ard"` 和旧版 `rel="ai-catalog"` 链接。robots.txt 只保留标准指令，不使用会触发 Lighthouse Unknown directive 的 Agentmap；ARD 仍通过 HTML 链接及 well-known URI 发现。目录只列出首页、功能和 FAQ 的公开文档，类型为真实的 `text/html`，不将登录入口或内部聊天 Agent 冒充公开 MCP/A2A 服务，也不声明未经验证的信任签名。

兼容旧版 Lighthouse：根部保留 `specVersion: "1.0"`。现行 ARD 的 ArdManifest 只要求 entries 并允许额外字段，但旧版 AI Catalog schema 还要求 specVersion 为字符串 1.0；只通过现行官方校验不能证明旧版 Lighthouse 通过。三个地址使用同一份兼容格式，并已用两份官方 JSON Schema 逐一验证。新增回归锁定根部版本字段；本轮 139 项测试串行通过、生产构建和 SEO HTTP 回归通过。text/html 的低级别类型提示仍保留真实语义，旧版检测器可能因此轻微扣分，不能承诺部署后 SEO 一定是 100。参见 [旧版 AI Catalog Schema](https://github.com/ards-project/ard-spec/blob/main/spec/schemas/ai-catalog.schema.json) 与 [Lighthouse 的 ARD 兼容问题](https://github.com/GoogleChrome/lighthouse/issues/17251)。

两份 Nginx 配置都为清单提供独立 JSON 路由，绕过首页和 Markdown 回退；文件缺失返回 JSON 404。部署必须包含 dist 的隐藏目录 `.well-known`，不能只复制 `dist/*`。当前 Docker COPY 和共享卷 `cp -a .../.` 会保留该目录。发布时同时更新前端镜像和生产网关配置；仅推送代码不会改变在线响应。如 CDN 缓存了旧的 HTML 回退，需要刷新这三个精确 URL，再检查状态码、Content-Type 与 JSON 正文。

2026-10-29 实测修复前线上三个清单 URL 均为 `200 text/html`，返回首页 doctype；本地修复后生产构建和 HTTP 浏览器测试确认三个地址均为 `application/json`，包括请求 Accept 为 text/markdown 的场景。官方 ARD conformance CLI 使用其 JSON Schema 校验 ArdManifest 与三个 ArdEntry 均通过，无错误；三条提示是 text/html 不在该工具内置的 Agent 类型列表中，不是 schema 失败。现行规范允许按真实 IANA 媒体类型描述资源，见 [ARD §3.3、§4、§5.1](https://agenticresourcediscovery.org/spec/)。

138 项隔离测试串行运行通过；默认并发运行曾遇到 Node 测试运行器 IPC 反序列化错误，串行全量复跑通过。生产构建与 SEO 浏览器回归通过。本机没有 Nginx/Docker，未执行真实 Nginx 联调；HTTP 回归使用 dist 静态服务器，Nginx 分流另有配置断言。跨端评估：只新增 Web 静态发现资源与路由，不改变聊天运行时和协议，桌面/CLI/Android 无对应修改。

跨端评估：本轮只改变 Web 搜索、链接分享和部署输出，原生桌面、CLI、Android 不被网页搜索抓取，无需移植 SEO 元信息；聊天、ACK、本地缓存和接口协议未变更。

## 首页与 /app 首屏（2026-10-29）

优先保证首页 `/` 在 JavaScript 到达前即可完整呈现。`vite build` 生成客户端 SSR manifest，`scripts/render-ssr.mjs` 收集实际渲染组件的 CSS 并内联进对应 HTML，避免只有正文却缺少懒加载路由样式的首帧。首页首屏对话示例不在 hydration 后重播入场动画；聊天设置与社交样式移到 `/app` 按需加载。首页不读取登录凭证、不校验登录、不请求业务 API，也不预加载聊天运行时。

`/app` 使用同一 `WebApp.vue` / `LoginRegister.vue` 组件渲染与 hydration。验证期间保留品牌、欢迎区与面板；真实空白登录表单以 `visibility:hidden` 保留尺寸并设置 `inert`，完成后原位显示表单或进入聊天，避免猜测骨架高度造成位移。移动端宽度在挂载后读取，SSR 不访问 window；Windows 动态导入 SSR 入口使用 file URL。构建不连接服务器、不保存上次聊天 DOM，不在 HTML 中写入凭证、联系人或消息。已登录用户的聊天内容仍由客户端恢复会话后加载，本项不是私有聊天内容的服务端渲染。

在 `web` 执行（浏览器路径为本机已安装的 Chromium/Edge）：

```text
npm run build
npm test
node tests/app-ssr-browser.cjs <浏览器路径>
node tests/seo-browser.cjs <浏览器路径>
node tests/browser-smoke.cjs <浏览器路径> --startup-only
```

首屏测试暂缓所有 JavaScript，验证桌面 1180px、移动端 390px 的真实首帧样式，然后放行脚本验证 DOM 复用、布局宽度、首页无认证/聊天加载及首页到 `/app` 导航。可选 `CLIENT_SCREENSHOT_DIR` 保存截图。SEO 测试覆盖无 JavaScript 正文、canonical/noindex、404、语言恢复与导航。聊天启动回归使用两份虚构账号、mock REST/WebTransport 与真实 Worker/IndexedDB；`--startup-only` 仅验证会话启动、双向收发和 ACK，不包含完整历史滚动回归。旧测试夹具同步了当前的目录快照、网关发现、length-v1 帧与虚拟列表可交互行。

本次生产构建（含类型检查）、128 项隔离测试、首屏浏览器回归、SEO 浏览器回归和双账号启动回归通过。完整 `browser-smoke.cjs` 在“jump to latest”滚动位置检查超时；该项未修复，不计为完整聊天回归通过。没有连接真实后端或部署，未测线上网络条件下的 LCP 分数。

跨端评估：已检查原生 `App.kt`、`component/LoginRegisterApp.kt` 与 `presentation/viewmodel/AuthViewModel.kt` 的登录入口。本次改变的是 Web 静态 HTML、CSS 装载与 hydration；桌面/CLI/Android 没有对应 SSR 链路，不适用，未修改其登录契约或执行原生构建。

## 本次验证（2026-10-28）

Web 前置检查通过；120 项隔离测试全部通过（含新增 4 项 SEO 测试）；类型检查、生产构建和三个公开页预渲染通过。Edge 无头浏览器验证了禁用 JavaScript 后的公开正文、所有入口初始 canonical/noindex、404、单页导航、语言切换、结构化数据增删和尾斜杠路径。构建首次遇到协议生成文件的临时文件访问错误，重跑完整构建通过，未修改协议生成逻辑。

本机没有 Nginx/Docker，未执行 `nginx -t` 或真实 Nginx HTTP 验证；404 状态测试使用本地静态测试服务器。未部署、未提交搜索引擎站长平台，实际收录与线上响应需部署后检查。原生客户端和后端本轮无改动，未重复执行其构建。
