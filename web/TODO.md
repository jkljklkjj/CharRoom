# TODO — CharRoom (Frontend)

## 🔴 P0 — Bug（已完成）

- [x] **WebTransport `_onclose` 不触发** — 监听 `transport.closed` + `_safeOnClose()` 防重复
- [x] **`isReconnecting` 永久锁死** — `onclose` 重置 + `.finally()` 兜底
- [x] **transport.ready 无 timeout** — `Promise.race` 15s 超时
- [x] **`flushQueue` 非登录响应也触发** — 加 `loggedIn` 门控，仅登录成功后 flush 一次
- [x] **XSS 漏洞** — `DOMPurify.sanitize()` 替代手工转义
- [x] **seqId 全链路贯通** — 接收消息从 payload 提取 seqId 更新游标

## 🟡 P1 — 性能优化

- [x] **`v-for` 用 index 做 key** — 改用 `messageId` 做 key，避免全量 DOM 重建
- [x] **DOMPurify 每次渲染每条消息都调用** — 预清洗存入 store，`formatText` 简化为直接返回
- [x] **`rebuildConversationStates` 每次用户列表变化读所有会话** — 添加 `previewCache`，仅消息变化时 invalidate
- [x] **`currentMessages` computed 每次创建新数组** — memoize 结果，仅长度变化时重新 slice
- [x] **`sortedUsers` 每次 reactive 变化都 map+sort** — hash-based memoize，仅数据变化时重排
- [ ] **localStorage O(n) 消息持久化** — 每条消息读写全量 JSON，500 条消息时严重退化 → 改用 IndexedDB 或写缓冲

## 🟢 P2 — 代码质量

- [ ] **`mergeUsers` 删除缓存中存在但 API 返回中不存在的用户** — 应只删 API 明确返回的
- [ ] **循环依赖 `messages.js` ↔ `users.js`** — 首次调用 `updateConversationState` 静默丢失 → 拆分到第三个模块
- [ ] **`getDeviceId()` / `getDeviceType()` 重复定义** — 提取到 `utils/device.js`
- [ ] **`avatarSrc()` 逻辑重复** — 提取到 `utils/format.js`
- [ ] **debug `console.log` 未清理** — `api/index.js`, `SidebarUsers.vue`
- [ ] **`window.$toast` 全局模式脆弱** — 改用 provide/inject 或独立 service
- [ ] **`callAgentStream` 未用 `safeFetch`** — 无 auth token
- [ ] **`TokenQuotaDialog` 相对 URL + `credentials:include`** — CSRF 风险
- [ ] **双 `onMounted` + async onMounted** — 合并为单个 onMounted
- [ ] **`time()` 函数死代码** — ChatWindow.vue
- [ ] **`removeUser` 用 splice** — 改用 filter 保持一致性

## 🔵 P3 — 安全

- [ ] **Token 存 localStorage** — XSS 可窃取 → httpOnly cookie 或 sessionStorage
- [ ] **`v-html` 依赖 DOMPurify 配置不变** — 添加安全注释

## ⚪ P4 — 功能缺失（vs KMP）

- [ ] Emoji 选择器
- [ ] 消息转发（ForwardSelectDialog）
- [ ] 回复预览栏（ReplyPreviewBar）
- [ ] 用户资料编辑 / 头像裁剪
- [ ] 群组管理 UI
- [ ] 文件下载支持（非图片文件只发文件名）
- [ ] 相对时间实时更新（formatRelativeTime 不刷新）
- [ ] 退出确认弹窗
