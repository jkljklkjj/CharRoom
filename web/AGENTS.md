# Web 前端协作约定

本目录是独立 Vue 3 / Vite / TypeScript 应用，属于 `CharRoom` Git 仓库。先读 [客户端总指南](../AGENTS.md)。依赖与命令以 [package.json](package.json)、`package-lock.json` 为准。

## 查找入口

- 应用与路由：`src/main.ts`、`src/App.vue`、`src/router/index.ts`。
- 产品介绍页：`src/views/Home.vue` 等；聊天入口：`src/views/WebApp.vue`。
- 聊天 UI：`src/components/ChatWindow.vue`、`SidebarUsers.vue`、`LoginRegister.vue`。
- REST：`src/api/index.ts`（请求与刷新在 `http.ts`）；状态：`src/store/{state,auth,users,messages,storage}.ts`。
- 实时：`src/services/chatSocket.ts` → `src/services/transport/TransportFactory.ts` / `WebTransport.ts`。文件名 chatSocket 不代表 WebSocket。
- 本地存储：`src/utils/messageDB.ts`；待发消息：`src/services/offlineQueue.ts`。
- protobuf：`src/proto/index.ts` / `worker.ts`（通过共享 `codec.ts` 编解码），运行时 URL `/proto/message.proto`；公共资源在 `public/proto/`。
- 协议常量：`src/proto/constants.ts`，由 `scripts/generate-protocol-constants.cjs` 生成；组件从这里导入，不再从 chatSocket 服务导入类型常量。业务常量仍在 `src/constants.ts`；wire 接口 `src/proto/messages.ts` 同时生成，store/UI 类型放 `src/types/`。
- PWA/构建：`vite.config.ts`、`scripts/generate-static-site-files.cjs`；文案在 `src/locales/` 三份语言文件。

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

- 组件负责交互与渲染，传输、持久化、重试放 service/store；先核对调用方和导出签名，防止只完成一半重构。
- import 的类名不得遮蔽浏览器能力探测；探测原生 API 使用 `globalThis`。
- 协议不能只改 `public/proto/`：构建脚本会从 `src/proto/` 复制 message.proto 和 agent_stream.proto。两个 proto 均须对齐后端主定义；信封和嵌套枚举在 Web 内统一用数字，int64 解码为十进制字符串以保留精度。
- Worker 使用可由构建系统解析的入口，必须处理加载失败、请求失败和超时；transferable buffer 转移后不能假定主线程仍可读取。
- 发消息区分已入本地队列、写入传输层、服务端 ACK、对端送达、已读。只在匹配的服务端确认后移除待发项；重试保持 messageId，登录完成后才恢复队列。
- IndexedDB、PWA Cache 与内存状态按账号隔离。退出登录捕获原账号再异步清理，防止切换账号后读错或删除错数据；不要缓存认证后的 REST 响应到共享缓存。
- 消息文本保持净化与转义，token/密码不进日志，不记录完整聊天正文。
- 新增用户可见文本同步 `zh-CN`、`en`、`ja`；不顺手引入另一套 UI/状态框架。

## 验证范围

社交发现入口为 `components/SocialHub.vue`，资料卡复用 `SocialProfileCard.vue`；请求在 `api/social.ts`，临时房生命周期在 `composables/useEncounter.ts`。已有设置控件样式统一从 `styles/app-controls.css` 复用；社交回归见 `tests/social-browser.cjs` 和 [实施说明](../docs/development/SOCIAL-DISCOVERY.md)。

按改动选择：构建；缺失 API/浏览器不支持；登录刷新失败；单聊/群聊编解码；断网重试；账号 A→B 隔离；ACK 超时/重复；Agent 流式输出结束与取消。未配置的测试工具先作为任务补齐，不能把手动检查冒充自动化覆盖。

已确认构建与协议缺陷见 [2026-10-23 审查](../../docs/architecture/2026-10-23-现状审查.md)（聚合工作区文档）。

TypeScript 迁移与浏览器测试见 [迁移说明](../docs/development/WEB-TYPESCRIPT.md)。Node 工具和测试保持 `.cjs`，运行时不新增 JS 文件或使用 ts-nocheck 绕过检查。
