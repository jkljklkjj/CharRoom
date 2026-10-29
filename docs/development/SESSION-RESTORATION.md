# Web 本地会话恢复与后台握手

## /app 启动

首页 `/` 不执行认证。`/app` 的构建期 SSR 仍只有无账号数据的真实认证入口；挂载后读取本地缓存的 access token 和刷新凭证。

`services/accessSession.ts` 解析 JWT 的账号与 `exp`，验证字段形状、账号一致性和到期时间。access token 与毫秒到期时间保存在 sessionStorage，refresh token 沿用 localStorage；不增加明文账号密码缓存。缓存的独立到期字段仅是元数据，不能覆盖 token 内的 `exp`。签名与撤销检查由后端完成，本地结果仅用于恢复 UI。

- 未过期：直接按 JWT 账号建立本地会话并显示应用，不请求 `/auth/validate`，不等待 `/users/me` 或握手；JWT 账号优先于陈旧的单独账号缓存。
- 缺失/到期：有 refresh token 则刷新一次后恢复；无可用凭证回到登录表单。
- 联系人本地缓存、历史恢复、在线状态和 WebTransport owner 发现/连接/LOGIN 通过 `bootstrapSession` 后台执行。握手完成前，待发消息继续保存在 outbox；只有匹配的服务端 ACK 才能完成发送。
- 不定时重复校验 token。后续 REST 的 HTTP 401 / 业务码 401、1002、1006，或实时认证拒绝，按需触发恢复。

## 懒刷新与失效

`api/http.ts` 的 `refreshSession` 把同一登录代次的 REST 和实时拒绝归并到一次 refresh，提交新 token 和缓存后才释放共享请求。重试一次原 REST；重试仍拒绝或 refresh 明确认证失败才清理会话。网络错误、5xx、刷新限流不当作退出登录。账号切换/退出会取消旧代次，迟到刷新不能覆盖新账号。

`services/chatSocket.ts` 暂停认证失败连接与队列发送，恢复成功后后台重新握手。暂时无法刷新则退避重试；不会以传输连接建立代替 LOGIN 确认。

## 每设备握手限流

最终规则为 **token 校验后的账号 + 签名设备信息，滚动一分钟每设备最多 6 次，每账号所有设备合计最多 12 次**。两个限制同时满足才允许；不同账号独立，不按 IP，不按 token 字符串拆桶，刷新 token 不重置额度。服务端原子检查并同时扣减两级额度，拒绝不消耗另一层额度。所有端共用后端 `LoginHandler` 的同一入口，详细键、Redis 原子实现及兼容策略见后端 `docs/development/SESSION-AND-HANDSHAKE.md`。

`HANDSHAKE_RATE_LIMITED:<剩余毫秒>` 是可重试冷却，不是凭证失效。Web、桌面、Android 共用传输均等待冷却后再连；Web 额外通过 `HandshakeBudget` 约束本页面的尝试频率。客户端节流不替代服务端限制。

本次 `/app` 本地 JWT 首屏恢复属于 Web 路由优化；原生自动登录仍沿用其认证 Repository 流程。原生本次同步的是新限流响应的识别和重连退避，避免后端更新后误退出。

## 验证命令

Web（`web` 目录）：

```text
npm test
npm run build
node tests/browser-smoke.cjs <浏览器路径> --startup-only
node tests/app-ssr-browser.cjs <浏览器路径>
node tests/seo-browser.cjs <浏览器路径>
```

隔离测试覆盖到期边界、并发刷新、临时失败保留凭证、刷新后再次拒绝、账号切换隔离和客户端握手窗口。浏览器夹具同时覆盖未过期 token 不验证、到期 token 只刷新一次、篡改独立到期缓存不能延长 JWT、陈旧账号缓存不覆盖 JWT，以及 owner/LOGIN 被延迟时应用仍已显示、双向消息与 ACK。

Windows 原生（仓库根目录）：

```text
./scripts/gradle-windows.ps1 :test --tests core.DurableReceiveTest --tests core.NativeQuicChatTransportTest
./scripts/gradle-windows.ps1 :cli:test :cli:verifyHeadlessRuntime
```

这些测试使用虚构凭证和 fake driver/transport，不访问真实用户或付费模型。原生 JVM 编译、限流兼容与原生传输离线测试、CLI 测试分别报告；它们不代表 Android APK 构建或真实跨端 QUIC/Redis 集群联调。

2026-10-29 验证：Web 133 项隔离测试、生产构建（含类型检查）、协议消息测试、首屏/SEO 浏览器回归、双账号启动与收发通过；原生两组共 18 项测试和 JVM 编译、CLI 测试及无 GUI 运行时检查通过。Android SDK 前置检查通过，但未运行 APK 构建或设备联调；后端增加账号总额后 15 项目标单测通过，Redis 实例/集群和真实 QUIC 联调未运行。账号总额沿用已有冷却响应，无需 Web/原生运行时代码同步，本次未重复客户端构建。完整历史滚动回归保留上一轮记录，本次只运行聊天启动范围。
