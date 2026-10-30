# Web 前端协作约定

本目录是独立 Vue 3 / Vite / TypeScript 应用，属于 `CharRoom` Git 仓库。先读 [客户端总指南](../AGENTS.md)。依赖与命令以 [package.json](package.json)、`package-lock.json` 为准。

## 查找入口

- 应用与路由：`src/main.ts`、`src/App.vue`、`src/router/index.ts`。
- 版本管理后台：`/admin` → `views/AdminConsole.vue` → `api/admin.ts`，独立管理会话；权限必须由后端名单裁决，启用步骤见 [后台说明](../../chat-room/docs/development/ADMIN-CONSOLE.md)。
- 发布须填写安装包类型、架构、字节数、SHA-256 和 Android versionCode；同步契约与校验边界见 [可靠更新](../docs/development/RELIABLE-UPDATES.md)。
- 产品介绍页：`src/views/Home.vue` 等；聊天入口：`src/views/WebApp.vue`。
- Web 纸感品牌在 `styles.css`，与原生 `QingliaoPalette` 对齐；`BrandMark.vue` / `ConversationPreview.vue` 共享首页和登录的视觉元素。产品范围与跨页验证见 [Web 产品设计](../docs/design/WEB-PAPER-PRODUCT.md)。
- 普通浏览器不额外预留顶部栏高度；独立应用保留真实刘海安全区。`/app` 根滚动锁由初始HTML标记与 `pageViewport.ts` 管理，不能只等WebApp挂载；改动验证见 [手机视口](../docs/development/MOBILE-VIEWPORT.md)。
- 聊天 UI：`src/components/ChatWindow.vue`、`SidebarUsers.vue`、`LoginRegister.vue`。
- REST：`src/api/index.ts`（请求与刷新在 `http.ts`）；状态：`src/store/{state,auth,users,messages,storage}.ts`。
- `/app` 使用 JWT 账号/exp 恢复本地 UI，未到期不请求 `/auth/validate`，后台握手与 REST 拒绝共用懒刷新；握手滚动一分钟每设备最多 6 次、每账号合计最多 12 次，冷却不可当作登出。入口、存储和验证见 [会话恢复](../docs/development/SESSION-RESTORATION.md)。
- 实时：`src/services/chatSocket.ts` → `src/services/transport/TransportFactory.ts` / `WebTransport.ts`。文件名 chatSocket 不代表 WebSocket。
- 草稿：`services/composerDrafts.ts` 定义按账号/会话的版本状态，`composables/useComposerDrafts.ts` 跨布局共享；附件经 `api/attachments.ts` 上传，发送前捕获账号与目标。
- 本地存储：`src/utils/messageDB.ts`；待发消息：`src/services/offlineQueue.ts`。新设备七天历史入口与 IndexedDB 合并规则见 [历史恢复](../docs/development/RECENT-HISTORY.md)。
- 联系人和群组由 `store/contactDirectory.ts` 统一同步，先读账号快照再校验版本；组件不直接拉取完整列表。`services/conversationViews.ts` 保存有上限的会话页面与阅读位置。消息库 v8 将增量消息、游标和 ACK 别名同事务提交，详见 [本地优先加载](../docs/development/LOCAL-FIRST-DIRECTORY-AND-HISTORY.md)。
- 会话列表支持右键/长按置顶和删除；删除只隐藏会话行、不删历史，新消息由 `updateConversationState` 自动恢复。偏好按账号存 localStorage，见 [会话列表动作](../docs/development/CONVERSATION-LIST-ACTIONS.md)。
- 历史行同时携带服务端主键与 `clientMessageId`：`utils/historyMessages.ts` 透传，`utils/messageDB.ts` 在合并事务里写别名并删除被取代的占位行，`store/messages.ts` 的 `mergeRows` 先按这个映射归一，文本+时间窗只作为老数据的兜底。
- 联系人远程搜索延迟3秒，Enter/表单立即提交；只按昵称模糊匹配，账号/ID 不参与；每页6条最多4页，滚动接近底部加载下一页。每账号滚动60秒8次，前端预算与后端429冷却配合，不按IP或token分桶。名片与跨端验证见 [搜索与名片](../docs/development/CONTACT-SEARCH-AND-PROFILE.md)。
- 发现页短时缓存由 `services/socialViewCache.ts` 按登录代次管理；资料、关系与回答修改后失效，不将他人资料落盘。本人名片与今日一问草稿分别在 `utils/socialProfileCache.ts` / `dailyAnswerDraft.ts`，账号切换、退出和异步完成必须核对所有者。
- `services/gatewayOwner.ts` 在每次实时连接前解析 `/cluster/owner`；outbox v3 按账号和重试时间索引分批读取，发送成功只能由匹配的服务端 ACK 确认。
- 增量补拉：`src/services/conversationSync.ts`；登录生命周期：`sessionEpoch.ts` / `sessionBootstrap.ts`（同目录）；消息虚拟列表：`src/components/VirtualMessageList.vue`。游标落库边界、IndexedDB v8 见 [本地优先加载](../docs/development/LOCAL-FIRST-DIRECTORY-AND-HISTORY.md)，其他验证命令见 [客户端优化](../docs/development/CLIENT-SYNC-AND-PERFORMANCE.md)。
- protobuf：`src/proto/index.ts` / `worker.ts`（通过共享 `codec.ts` 编解码），运行时 URL `/proto/message.proto`；公共资源在 `public/proto/`。
- 协议常量：`src/proto/constants.ts`，由 `scripts/generate-protocol-constants.cjs` 生成；组件从这里导入，不再从 chatSocket 服务导入类型常量。业务常量仍在 `src/constants.ts`；wire 接口 `src/proto/messages.ts` 同时生成，store/UI 类型放 `src/types/`。
- PWA/构建：`vite.config.ts`、`scripts/generate-static-site-files.cjs`；文案在 `src/locales/` 三份语言文件。
- PWA 不将所有导航回退到首页，也不预缓存尚未完成 SSR 的 HTML；已渲染静态入口按路由 NetworkFirst 缓存。改 SSR 路由须评估对应导航规则，回归用 `tests/pwa-navigation-browser.cjs`（真实 Worker）和首屏测试，不能仅测 bypassServiceWorker。
- SEO 路由与索引范围在 `site.routes.json`，运行时由 `services/pageSeo.ts` 同步；正式 SEO 产物用 `npm run build:seo`（需 `CHROME_BIN`），部署与验证见 [Web SEO](../docs/development/WEB-SEO.md)。私有入口保持 noindex，不能重新放入 sitemap。
- ARD 清单由 `scripts/resource-catalog.cjs` 经静态资源生成脚本输出到 `/.well-known/ard.json` 及旧版 ai-catalog 地址；仅描述真实公开资源，部署必须保留 `.well-known`，清单路由不得回退 HTML。
- robots.txt 只保留标准指令，ARD 用 HTML 链接发现，不添加会令 Lighthouse 扣分的 Agentmap。公开子页面包屑由 `pageSeo.ts` 同时供 SSR 与客户端生成，导航到私有页须清除；验证与 skill 审查见 [SEO Guide 实施](../docs/development/SEO-GUIDE-AUDIT-2026-10-29.md)。
- SEO 审查也覆盖 `public/` 独立 HTML，开发演示和简略 API 目录保持初始 noindex；完整参考文档矩阵与未完成项见 [全量对照](../docs/development/SEO-GUIDE-FULL-AUDIT-2026-10-29.md)。网关直接使用构建 metadata，不另设文案替换规则。
- 生产聚合网关也需同步 `chat-room/docker/nginx/nginx.conf.template`。本地冷启动测量用 `node scripts/measure-page-performance.cjs <浏览器路径> <report.json>`；指标条件、Worker 降级和聊天回归门槛见 [SEO 与性能续改](../docs/development/WEB-SEO-PERFORMANCE-2026-10-29.md)。
- `npm run build` 已对公开索引页和 `/app` 真实认证入口执行构建期 SSR；`render-ssr.mjs` 根据客户端 SSR manifest 内联路由 CSS，`main.ts` 复用 DOM。首屏回归用 `node tests/app-ssr-browser.cjs <浏览器路径>`；首页不做认证，静态 HTML 不得写入账号数据。

## 开发命令（工作目录：CharRoom/web）

```text
npm ci
npm run dev
npm run typecheck
npm test
npm run build
npm run generate:protocol
npm run test:messages
npm run preview
```

开发端口默认 5173。`npm run typecheck` 使用 vue-tsc 严格检查 TS 与 Vue 模板；`npm test` 执行隔离测试。当前没有 lint 脚本。`npm run build` 会先重写 public 图标、sitemap、两个 proto 副本和协议常量；运行后审查 diff，只保留本次有意产生的变化。构建通过只能说明打包成功，聊天行为还需独立验证。

`npm run test:messages` 先检查常量生成文件是否过期，再用后端主协议生成夹具，验证枚举、Web 编解码、消息分发与成功/失败 ACK；使用 Node 内置测试工具，需启用脚本中的 VM Modules 参数。它不连接服务器，不能代替真实双账号收发验证。

REST 支持 `VITE_API_BASE`，默认指向线上；QUIC 目标还要单独检查 `chatSocket.ts` / 调用方，设置 REST 地址不会自动改变实时目标。

## 修改约定

- 活跃历史窗口受 `historyWindow.ts` 约束，补拉直接合并已提交批次，最旧边界用消息索引查询；截断窗口必须保留“查看最新消息”及阅读锚点。轻遇轮询遵守可见性、联网和退避策略，见 [第二轮性能改进](../docs/development/CLIENT-PERFORMANCE-SECOND-PASS.md)。
- 历史删除使用精确索引和 key cursor，清空会话时消息与摘要同事务；不要重新引入全会话正文扫描或两阶段清理，见 [历史存储优化](../docs/development/HISTORY-STORAGE-PERFORMANCE.md)。
- 会话选择、预览和 UI key 使用 `ConversationId` 生成的 `user:<id>` / `group:<id>`；联系人 ID 保留原值，存储与协议显式转换。禁止用正负号或 `Math.abs` 判断会话类型，见 [会话标识](../docs/development/CONVERSATION-IDENTITY.md)。
- 联系人虚拟列表使用 `utils/virtualContacts.ts` 的固定行高，修改行样式须同步行高并验证键盘导航；消息测量复用 `IncrementalVirtualLayout`。跨端边界与验证见 [性能改进](../docs/development/CLIENT-PERFORMANCE-2026-10-28.md)。
- 每次修改 Web 都要评估桌面、CLI、Android 的对应行为是否需要同步；原生端修改也须反向检查 Web。按 [跨端修改评估](../AGENTS.md#跨端修改评估) 执行，并在交付中说明同步结论与理由。
- 组件负责交互与渲染，传输、持久化、重试放 service/store；先核对调用方和导出签名，防止只完成一半重构。
- import 的类名不得遮蔽浏览器能力探测；探测原生 API 使用 `globalThis`。
- 协议不能只改 `public/proto/`：构建脚本会从 `src/proto/` 复制 message.proto 和 agent_stream.proto。两个 proto 均须对齐后端主定义；信封和嵌套枚举在 Web 内统一用数字，int64 解码为十进制字符串以保留精度。
- Worker 使用可由构建系统解析的入口，必须处理加载失败、请求失败和超时；transferable buffer 转移后不能假定主线程仍可读取。
- ACK 的 `messageId` 匹配待发请求，可选 `serverMessageId` 是历史主键；先在 IndexedDB 单事务中归并两种 ID，再删除对应账号的 outbox。本地乐观行与服务端历史/回显按“对端 + 文本 + 2 秒时间窗”归并，ACK 后把 `serverMessageId` 记为已见，避免同一条消息显示两行；手动重试必须有 toast 反馈。
- 发消息区分已入本地队列、写入传输层、服务端 ACK、对端送达、已读。只在匹配的服务端确认后移除待发项；重试保持 messageId，登录完成后才恢复队列。
- IndexedDB、PWA Cache 与内存状态按账号隔离。退出登录捕获原账号再异步清理，防止切换账号后读错或删除错数据；不要缓存认证后的 REST 响应到共享缓存。
- 历史中的收到私聊消息必须在 IndexedDB 提交后才调用 `/messages/offline/ack`，查询本身不确认接收；切换账号或写入失败不得发送回执。跨端约定见 [CLI 与离线确认](../docs/development/CLI.md)。
- 消息文本保持净化与转义，token/密码不进日志，不记录完整聊天正文。
- 新增用户可见文本同步 `zh-CN`、`en`、`ja`；不顺手引入另一套 UI/状态框架。

## 验证范围

社交发现入口为 `components/SocialHub.vue`，资料卡复用 `SocialProfileCard.vue`；请求在 `api/social.ts`，临时房生命周期在 `composables/useEncounter.ts`。已有设置控件样式统一从 `styles/app-controls.css` 复用；社交回归见 `tests/social-browser.cjs` 和 [实施说明](../docs/development/SOCIAL-DISCOVERY.md)。

按改动选择：构建；缺失 API/浏览器不支持；登录刷新失败；单聊/群聊编解码；断网重试；账号 A→B 隔离；ACK 超时/重复；Agent 流式输出结束与取消。未配置的测试工具先作为任务补齐，不能把手动检查冒充自动化覆盖。

已确认构建与协议缺陷见 [2026-10-23 审查](../../docs/architecture/2026-10-23-现状审查.md)（聚合工作区文档）。

TypeScript 迁移与浏览器测试见 [迁移说明](../docs/development/WEB-TYPESCRIPT.md)。Node 工具和测试保持 `.cjs`，运行时不新增 JS 文件或使用 ts-nocheck 绕过检查。
