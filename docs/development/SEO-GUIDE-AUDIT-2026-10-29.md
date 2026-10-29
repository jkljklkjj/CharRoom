# SEO Guide 审查与实施（2026-10-29）

参考用户指定的 [seo-guide SKILL.md](https://github.com/lesishu/seo-guide-skill/blob/main/SKILL.md)，阅读其 seo-audit、technical-seo、on-page-seo、tkd-standard、seo-principles。使用其页面审查和技术审查流程，发生冲突时以当前 Google Search Central 文档和实际源码为准。未将外部 skill 安装到全局，也未增加分析脚本。

## 本轮落地

| 发现 | 实现 |
| --- | --- |
| 首页标题和首段主要是情绪化文案，产品用途不够明确 | 三语标题、描述、首屏说明明确网页聊天、跨平台即时通讯和 AI 助手；保留原有视觉主题 |
| 功能与 FAQ 页面缺少互链、可见层级和源码入口 | 增加下载及页面互链、真实 GitHub/Gitee 源码链接、main 地标和可见面包屑；移动端链接可换行 |
| 公开子页缺少与导航匹配的结构化层级 | SSR 与客户端共用 pageBreadcrumb，生成 BreadcrumbList；语言切换更新名称，进入首页、私有页或未知页清除 |
| 显式 index.html 地址可能生成重复内容或在 hydration 后落入不存在页 | 两份 Nginx 配置按原始 request_uri 将首页和已知路由的 index.html 301 到规范路径，保留查询参数；目录内部索引不触发重定向 |
| llms.txt 有错误仓库和已下线的传输地址 | 改为真实源码、公开文档和当前 WebTransport/QUIC 边界；不推荐私有页、未经核实的对比页或不存在的接口 |
| FAQ 过度承诺全部浏览器、全部历史、无需配置、一键部署及自由分发 | 三语明确 WebTransport 支持、新设备最近 7 天历史、AI 配置和权限、部署前置条件；源码公开不等于虚构授权条款 |
| Lighthouse 将 robots 中 Agentmap 判为 Unknown directive | 删除非标准指令，保留 User-agent、Allow、Sitemap；ARD 使用 HTML rel=ard 和 well-known URI，保留 specVersion 1.0 与旧版地址 |

公开页面的主题映射：`/` 介绍产品及使用入口，`/features` 解释功能和平台，`/faq` 回答浏览器、下载、隐私、历史、部署和 AI 设置问题。这是按当前内容推导的搜索意图，不是声称已取得关键词搜索量或排名数据。

## 实际验证

- Lighthouse 13.6.0，Edge 无头浏览器、本机 dist 静态服务器，仅运行 SEO 分类。首次 92 分，唯一失败项 robots-txt，明确指出 Agentmap 未知指令；移除后同样配置为 100 分，robots、description、canonical 均通过。没有测量或承诺线上 SEO 分数、排名及收录。
- 143 项隔离测试串行通过，生产构建含类型检查和四个页面 SSR 通过。
- SEO 浏览器回归覆盖无 JavaScript 正文、每页一个 H1、真实源码链接、静态与动态面包屑、英文切换、私有页清除结构化数据、canonical/noindex、清单 HTTP 响应和显式 index URL 重定向契约。
- 首页与 /app 在桌面及移动宽度的首帧和 hydration 回归通过，首页不加载聊天运行时或认证接口。
- 聊天启动回归通过两份虚构账号、双向消息、ACK 和真实 Worker；远端服务被 mock。本轮未改变收发、落库、token 或握手策略，未重复长历史压力测试。
- Nginx 配置有静态回归断言；本机没有 Nginx/Docker，HTTP 测试服务器验证的是预期契约，不是实际 Nginx 联调。上线前仍需 nginx -t 和真实响应验证。

可在构建后启动本地 preview，用另一个终端执行下列命令复测首页（需安装浏览器，并通过 CHROME_PATH 指定）：

```text
npm run preview -- --host 127.0.0.1 --port 4173 --strictPort
npx --yes lighthouse@13.6.0 http://127.0.0.1:4173/ --only-categories=seo --output=json --output-path=seo-report.json --chrome-flags="--headless=new"
```

## 适用边界与后续

不照搬固定字数、关键词密度、每页必须有图片、无独立语言 URL 也添加 hreflang 等机械检查；不制造评分、价格、授权、发布日期或 sitemap lastmod。Google 标题和摘要会按设备显示空间截断，参考 [标题链接](https://developers.google.com/search/docs/appearance/title-link)、[摘要](https://developers.google.com/search/docs/appearance/snippet)、[面包屑](https://developers.google.com/search/docs/appearance/structured-data/breadcrumb)、[规范 URL](https://developers.google.com/search/docs/crawling-indexing/consolidate-duplicate-urls)。

部署后由站点所有者用 Search Console 检查规范 URL、索引覆盖、真实查询和点击，再决定新增安装/部署教程；本轮未获取这些账号数据，不批量发布薄内容或自动开展外链推广。保留比较页 noindex，FAQ 不承诺富媒体搜索结果。

跨端评估：仅 Web 公开内容、HTML 元信息、导航及静态网关路径改变，桌面/CLI/Android 没有 SSR 或搜索元信息链路，无需同步运行时代码，未执行原生构建。
