# Web SEO 与性能续改（2026-10-29）

## 本轮实现

- 生产静态网关实际在 `chat-room/docker/nginx/nginx.conf.template`，不能只改独立 Web 容器配置。两份配置均按已知路径读取自己的 HTML，未知路径返回真正的 404；生产网关保留 Markdown 内容协商、API 路由及限流。
- 补齐 `/reset` 的构建路径、三语标题和描述、初始 noindex；恢复链接可直接访问，不进入 sitemap。
- 三语公开文案明确 TLS/QUIC 传输加密与端到端加密的区别，删除 Protobuf 自带防篡改、自部署自动增加端到端加密的误导。
- 从 SSR manifest 提取公开页面实际用到的模块生成 modulepreload，提前发现懒加载路由代码。只对可索引公开页启用，避免重型 `/app` 依赖争抢首帧资源；首页仍不加载聊天模块或认证接口。
- 主线程 Protobuf codec 改为按需动态加载，正常编解码继续走 Worker。保留 Worker 初始化失败、请求错误、崩溃和超时的降级；解码前保留原始 buffer，避免转移所有权后无法降级。
- 修复虚拟消息列表“跳到最新”与动态行高测量竞争的问题：立即定位末尾，后续测量继续跟随末尾；用户滚离末尾或恢复非末尾阅读位置后停止跟随。没有改变消息 ACK、重试、历史落库及协议。

## 本地性能测量

工作目录 `CharRoom/web`，先执行 `npm run build`：

```text
node scripts/measure-page-performance.cjs <Chromium-or-Edge-executable> <report.json>
node scripts/measure-page-performance.cjs <Chromium-or-Edge-executable> <comparison.json> --compare-preloads --home-only
```

脚本仅启动本机静态服务器，阻断外部请求，不使用真实账号；禁用 Service Worker 注册，每次使用新浏览器上下文，1180×850、CPU 4 倍减速、RTT 100ms、下载 1.6Mbps。记录未压缩脚本体积、FCP/LCP、CLS、长任务和 hydration 完成时间。报告可能含本机资源路径，不含认证数据。命令成功后自行关闭服务器和浏览器。

同一构建交替关闭/开启公开路由预加载，各三次：

| 首页中位数 | 关闭预加载 | 开启预加载 |
| --- | ---: | ---: |
| FCP / LCP | 584ms | 524ms |
| hydration 完成 | 1850ms | 1827ms |
| CLS | 0 | 0 |

这是小样本实验，首个样本存在浏览器冷启动波动，不能作为线上百分比收益或 Core Web Vitals 达标证明。

匿名 `/app` 初始脚本（包含 Worker）从 605029B 减到 515729B，减少 89300B，约 14.8%。主要收益是正常路径不再提前加载约 89KB 主线程 Protobuf。构建哈希和文案会使精确字节数小幅变化；gzip/Brotli 传输节省需在部署环境测量。最终三次本地 `/app` 的 FCP/LCP 中位数为 920ms，hydration 3300ms；基线与最终测量的后台负载和 Service Worker 设置不完全相同，不把时间差算作严格前后收益。该匿名实验不等同于已登录会话恢复或持续聊天性能测量。

## 验证与边界

通过：136 项隔离测试、类型检查和生产构建；首屏 SSR/hydration、SEO 无 JavaScript、完整双账号聊天、1200 条历史窗口浏览器回归。完整聊天回归包含消息收发、ACK、输入法组合输入、搜索、滚动和跳到最新。强制禁用 Worker 的生产构建回归验证实际动态 chunk 加载、双向收发和 ACK；正常 Worker 路径验证未提前加载主线程 codec。

当前机器没有 Nginx/Docker；两份配置已做静态断言，但没有执行真实 `nginx -t` 或 Nginx HTTP 联调。未部署，未连接真实账号或真实后端，没有测量线上 p75 INP/LCP。前一轮文档中记录的 jump-to-latest 回归失败已在本轮修复并通过完整回归。

跨端评估：SEO、SSR manifest、浏览器 Worker 和 ResizeObserver 虚拟列表为 Web 专属实现。已核对原生 `ChatScreen`、`GroupChatScreen` 使用 Compose LazyColumn 自己的滚动接口；本轮无需同步桌面、CLI、Android。消息协议及后端握手策略未变，未重复运行原生构建。

## 后续优先级

1. 部署后先验证真实首页/功能/FAQ 为 200、未知页为 404、私有页初始 noindex，再由站点所有者在 Search Console 提交 sitemap、检查 URL 抓取与索引。技术 SEO 的价值取决于真实 HTTP 响应，不能以本地静态服务器代替生产验证。
2. 依据真实搜索需求补充有实质内容的安装/平台支持、自部署、Web 使用指南，并从首页和功能页自然链接。先确认支持范围和可复现步骤，再开放索引；暂不开放未经核实的竞品对比页。避免关键词堆叠及无内容的批量页面。
3. 当前语言偏好共用 URL，先保留中文静态版本。若要做英文/日文自然搜索，再实现稳定独立语言 URL、对应 SSR 内容和相互 hreflang。不要在同一 URL 上伪造三套语言地址。
4. 首页优化以公开页冷启动为目标；聊天体验另测长会话、快速切换、长消息/图片行高、输入期间主线程长任务。新增分析采集需明确数据范围，不能采集 token 或聊天正文；本轮只增加本地实验工具，没有接入第三方分析。
5. 继续拆分模块之前先查资源瀑布与长任务；不延迟 ACK、落库、补拉，不缩减历史正确性来换首屏数字。保持 Worker 降级、阅读锚点、消息可靠性回归作为门槛。
6. 单独验证 PWA 安装和更新：当前 Service Worker 在页面 load 后注册，并预缓存全部 JS/CSS。它可能在后台下载本次延迟的 codec，因此上面的禁用 SW 实验不能代表整个安装期流量。后续应测量它与聊天请求的带宽竞争，再决定注册时机和预缓存范围，同时覆盖离线启动、旧版本更新和懒加载失败，避免直接删缓存破坏体验。

参考：[Google JavaScript SEO](https://developers.google.com/search/docs/crawling-indexing/javascript/javascript-seo-basics)、[Vite SSR manifest](https://vite.dev/guide/ssr)、[优化 INP](https://web.dev/articles/optimize-inp)。Google 已于 2026 年 5 月停止展示 FAQ 富媒体搜索结果，继续维护 FAQ 是为了回答用户问题，不把 FAQ schema 当作流量收益承诺，见 [Search 更新](https://developers.google.com/search/updates)。
