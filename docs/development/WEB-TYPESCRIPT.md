# Web TypeScript 迁移

`web/src/` 的运行时模块、全部 Vue script 与 `vite.config.ts` 已迁移到 TypeScript。`tsconfig.json` 启用 `strict`、`isolatedModules` 和 `verbatimModuleSyntax`；`vue-tsc` 同时检查 TS 与 Vue 模板，生产构建先做类型检查。Node 构建脚本与测试保持 `.cjs`，浏览器产物仍由 Vite 编译为 JavaScript。

## 类型与职责

| 位置 | 职责 |
| --- | --- |
| `web/src/proto/constants.ts`、`messages.ts` | 从两个 proto 生成 wire 枚举常量及消息接口；禁止手改生成文件 |
| `web/src/proto/codec.ts` | 主线程与 Worker 共用的 protobuf 编解码，数字枚举、十进制字符串 int64 |
| `web/src/proto/workerMessages.ts` | Worker 请求/响应联合类型 |
| `web/src/types/chat.ts` | 用户、会话、消息、store 状态；与 wire DTO 分开 |
| `web/src/types/api.ts` | REST 响应、认证、离线消息、配额接口 |
| `web/src/types/transport.ts` | 二进制数据及流路由选项 |
| `web/src/api/http.ts` | fetch、响应解包、共享 token 刷新；业务 API 不再循环依赖配额模块 |
| `web/src/constants.ts` | 助手账号等业务常量；协议数值仍来自 proto |

Worker 使用 `new Worker(new URL('./worker.ts', import.meta.url), { type: 'module' })`，由 Vite 打包；初始化、请求失败、崩溃和超时会拒绝等待请求并回退。解码转移输入的副本，主线程回退仍能访问原始 buffer。

迁移同时修复了阻塞构建/运行的既存接口问题：IndexedDB 缺失分页与删除导出、离线队列缺失重试上限、配额 API 缺失导入/导出、部分页面残留全局 `$toast` 调用。用户详情统一返回解包后的 User。刷新失败会结束全部等待请求，刷新接口直接 fetch，避免递归刷新。

IndexedDB 升级到版本 2，增加账号/会话/消息序号复合索引，兼容旧版数值账号键。分页按消息序号排序；删除后追加使用最大序号，避免覆盖仍在的消息；批量替换等待旧记录删除完成再写入新记录。退出清理捕获原账号 ID，避免异步清理读取到新账号。

## 验证命令

工作目录为 `CharRoom/web`，使用锁文件安装依赖；本轮执行环境为 Node 24。

```text
npm ci
npm run generate:protocol
npm run typecheck
npm test
npm run build
npm run test:browser -- <Chromium或Edge可执行文件>
```

浏览器路径也可通过 `BROWSER_EXECUTABLE` 提供。浏览器脚本仅在 127.0.0.1 启动静态服务，拦截全部远端请求；不使用真实账号、线上消息或实际 QUIC 服务。协议测试需聚合工作区的相邻 `chat-room` 主 proto。

2026-10-24 验证：严格类型检查、15 项自动化测试、完整生产构建均通过；生产产物浏览器回归通过双账号双向收发、成功 ACK 与真实 Worker 编解码。存储测试包括分页、删除后追加、账号隔离、批量替换和旧版数据库升级；刷新测试包括并发成功/失败，Worker 测试包括请求错误、崩溃与超时回退。

构建仍提示 Vite CJS API 弃用、protobufjs 依赖中的 eval 和离线队列同时静态/动态导入；本轮未升级应用框架或改部署。测试不代表线上双账号实测，也不覆盖完整断网重放/历史游标正确性、PWA 缓存账号隔离或 Android WebTransport 联通；这些既存可靠性事项仍需单独完成。原生构建限制见 [消息契约](MESSAGE-CONTRACT.md)。本轮没有推送或部署。
