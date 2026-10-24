# TODO — CharRoom (Frontend)

## 🔴 P0 — Bug（已完成）

- [x] **WebTransport `_onclose` 不触发** — 监听 `transport.closed` + `_safeOnClose()` 防重复
- [x] **`isReconnecting` 永久锁死** — `onclose` 重置 + `.finally()` 兜底
- [x] **transport.ready 无 timeout** — `Promise.race` 15s 超时
- [x] **`flushQueue` 非登录响应也触发** — 加 `loggedIn` 门控，仅登录成功后 flush 一次
- [x] **XSS 漏洞** — `DOMPurify.sanitize()` 替代手工转义
- [x] **seqId 全链路贯通** — 接收消息从 payload 提取 seqId 更新游标
- [x] **好友列表增量更新** — acceptFriend 返回用户信息，前端增量添加

## 🟡 P1 — 性能优化

- [x] **`v-for` 用 index 做 key** — 改用 `messageId` 做 key，避免全量 DOM 重建
- [x] **DOMPurify 每次渲染每条消息都调用** — 预清洗存入 store，`formatText` 简化为直接返回
- [x] **`rebuildConversationStates` 每次用户列表变化读所有会话** — 添加 `previewCache`，仅消息变化时 invalidate
- [x] **`currentMessages` computed 每次创建新数组** — memoize 结果，仅长度变化时重新 slice
- [x] **`sortedUsers` 每次 reactive 变化都 map+sort** — hash-based memoize，仅数据变化时重排
- [x] **localStorage O(n) 消息持久化** — 迁移到 IndexedDB，O(1) 追加

## 🟢 P2 — 代码质量

- [x] **`mergeUsers` 删除缓存中存在但 API 返回中不存在的用户** — 已修复，merge 只做合并
- [x] **循环依赖 `messages.js` ↔ `users.js`** — 预加载 + pending queue 修复
- [x] **`getDeviceId()` / `getDeviceType()` 重复定义** — 提取到 `utils/device.js`
- [x] **`avatarSrc()` 逻辑重复** — 提取到 `utils/format.js`
- [x] **debug `console.log` 未清理** — 已移除
- [x] **`window.$toast` 全局模式** — 改用 utils/toast.js 独立 service
- [x] **`callAgentStream` 未用 `safeFetch`** — 已添加 auth header
- [x] **`TokenQuotaDialog` CSRF 风险** — 改用 safeFetch
- [x] **双 `onMounted` + async onMounted** — 提取 `restoreSession` 函数
- [x] **`time()` 函数死代码** — 已移除
- [x] **`removeUser` 用 splice** — 改用 filter

## 🔵 P3 — 安全

- [x] **Token 存 localStorage** — 改用 sessionStorage（XSS 安全）
- [x] **`v-html` 安全** — 添加安全注释

---

## 🔴 第二轮审查发现（2026-08）

### P0 — 真实 Bug（本轮已修复）

- [x] **会话预览读取已死的 localStorage** — IndexedDB 迁移后 `users.js:loadConversationPreview` 仍读 `charroom_chat_history_*`（无任何代码再写）；侧边栏最后消息时间永远为空且 rebuild 覆盖内存 patch 值。已改读 IndexedDB `conversation_meta`（messageDB v2 新增元数据存储，append/save 时同步维护 lastIncomingMessageTime）
- [x] **`/login` 路由不存在** — `api/index.js:onRefreshFailure` → `window.location.href = '/login'`，router 无此路由，token 刷新失败跳 404。已改跳 `/app`（未登录态展示 LoginRegister）
- [x] **删除消息不落 IndexedDB** — `messages.js:deleteMessage` 只 filter 内存，messageDB 无单条删除 API，刷新后消息复活。已补 `messageDB.deleteMessage()` + 接收消息（实时/同步两条路径）透传 messageId
- [x] **`window.$toast` 未替换完** — `Home.vue`、`VerifyCode.vue`（5 处）、`WebApp.vue` 仍用全局模式，已全部改用 `utils/toast`
- [x] **离线队列无重试上限** — `incrementRetry`/`retryCount` 死代码，失败消息无限重发。已接线 + `MAX_RETRIES=5` 超限丢弃 + `MAX_QUEUE_SIZE=1000` 容量上限

### P1 — 性能（本轮已修复）

- [x] **IndexedDB `appendMessage` 实为 O(n)** — 用 `index.count(range)` 算 nextIndex，非 TODO 声称的 O(1)；`getMessages` 全量读 + JS sort，翻页每次全量读。已修复：messageDB v3 新增 `accountId_conversationId_index` 复合索引 + 会话元数据 `nextIndex` 游标（append O(1)，旧会话一次性 count 回退）+ `getMessagesPage` 游标分页（翻页成本 O(limit)），messages.js 改用分页 + pageCursors
- [x] **`getAvatar()` 每条消息 O(好友数)** — ChatWindow 模板每消息 `users.find`，100 消息 × 50 好友 = 每次重渲染 5000 次。已改 `avatarMap` computed（仅 users 变化时重建）

### P2 — 死代码 / 清理（本轮已修复）

- [x] **`priorityQueues` + `flushQueue()` 死代码** — 声明且遍历但从不 push。已删除；顺带移除 write-only 的 `isReconnecting`、未用的 `ACK_CONFIRM_MS`、死 import `clearQueue`/`getConversationKey`/`restoreConversationSeqIds`/`loadConversation`
- [x] **`pendingQueue` 内存队列永不排空** — 重连只 flush IndexedDB，内存缓存泄漏。已删除内存队列，断线消息只走 IndexedDB（容量上限 1000 在 saveToQueue）
- [x] **双 SW 注册** — vite-plugin-pwa `injectRegister:'inline'` + main.js 手动注册 `/sw.js`。已删除 main.js 手动注册，交给插件
- [x] **offlineQueue 双份存储** — `Array.from(Uint8Array)` 转普通数组（IDB 原生支持 typed array）+ buffer/wrapperObj 双份。已改直接存 Uint8Array（flush 兼容旧数组条目）
- [x] **70 处 `console.*` 带进生产**（22 处 debug）— main.js 生产环境静默 `console.debug`；eslint 揪出并修复 api/index.js 未导入的 `getDeviceType()`（登录 ReferenceError 真 bug）
- [x] **web 无测试/无 linter** — 测试已落地为 node:test + fake-indexeddb（`tests/message-db.test.cjs` 等，覆盖游标分页/删除/元数据），构建内置 `vue-tsc --noEmit` 类型检查；`npm test`
  - 说明：原本地分支曾引入 vitest + eslint 10 flat config，后因与既有 node:test / vue-tsc 方案重复未合入，仅保留测试内容本身

## ⚪ P4 — 功能缺失（vs KMP）

- [x] **相对时间实时更新** — ChatWindow 每 30s 刷新 now ref，模板重新渲染相对时间
- [x] **退出确认弹窗** — 设置页登出按钮先弹确认（含 i18n 三语）
- [ ] Emoji 选择器
- [x] **消息转发** — 上下文菜单加"转发"（ForwardSelectDialog：好友列表弹窗，内容原样转发给目标，纯前端实现）
- [x] **回复预览栏** — 输入框上方显示引用条（可取消），发送时携带 replyToMessageId/replyToContent/replyToSender（协议已支持，替代原先的文本拼接回复）
- [x] **用户资料编辑** — 设置页新增编辑资料（昵称/签名，PUT /users/me/profile）；头像裁剪待做
- [ ] 群组管理 UI
- [ ] 文件下载支持（非图片文件只发文件名）
