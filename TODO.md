# TODO — CharRoom 全栈项目

## 🌐 前端 Web

### 🔴 高优

- [ ] **Protobuf Web Worker 化**
  - 当前 `decodeMessage` 在主线程执行，大消息阻塞 UI
  - `web/src/proto/worker.js` 已有 Worker 骨架，但 fallback 到主线程
  - 确保 Worker 路径始终可用，移除主线程 fallback

- [ ] **乐观 UI + 服务端确认回滚**
  - 发送消息立即显示（isSent=optimistic）
  - ACK 返回后替换为服务端 seqId
  - 超时 16s 标记为 failed，支持重试
  - 现状：`pendingAcks` 已有基础逻辑，但 UI 状态更新不完整

- [ ] **sync_hint 触发增量同步**
  - 后端推送 `sync_hint` 后，前端应调用 `POST /sync/messages` 拉取增量
  - 现状：`chatSocket.js` 已处理 sync_hint 更新 seqId 游标，但未触发实际拉取

### 🟡 中优

- [ ] **离线首屏加载策略**
  - 虚拟滚动 + 分页 + seqId 增量同步
  - 现状：消息分页已实现（PAGE_SIZE=50），但无虚拟滚动

- [ ] **PWA 离线队列持久化**
  - IndexedDB + Service Worker 后台重发
  - 现状：`pendingQueue` 仅内存，页面刷新丢失

- [ ] **Agent 工具调用 UI 展示**
  - 后端已支持 `AgentStreamChunk`（TOOL_CALL/TOOL_RESULT/USAGE）
  - 前端 `WebApp.vue` 只处理 TEXT/DONE/ERROR，需扩展 TOOL_CALL/TOOL_RESULT 展示

- [ ] **消息重试 UI**
  - 发送失败（isSent=failed）的消息支持点击重试
  - 重新编码 protobuf 并发送

### 🔵 长期

- [ ] WebTransport Datagram（不可靠传输，心跳/状态同步）
- [ ] WASM 编解码（protobuf.wasm 提速 3-5×）

---

## 📱 KMP 客户端

### 🔴 高优

- [ ] **Agent 流式协议适配**
  - 后端已切换为 `AgentStreamChunk`（TEXT/TOOL_CALL/TOOL_RESULT/USAGE/DONE/ERROR）
  - `ProtobufResponseParser.kt` 已更新解析逻辑
  - `QuicClientImpl.kt` + `CronetQuicClient.kt` 已更新传输层
  - 需验证：ChatApp.kt 的 `onAgentStreamChunk` 回调是否正确处理新协议

- [ ] **sync_hint 处理**
  - 后端推送 `sync_hint` 后，KMP 客户端应触发增量同步
  - 检查 `MessageReceiveListener` 是否有 sync_hint 回调

- [ ] **Thread safety 修复验证**
  - 已修复：QuicClientImpl.sessions → ConcurrentHashMap
  - 已修复：rttWindow → Collections.synchronizedList
  - 已修复：CronetQuicClient listener synchronized
  - 已修复：ProtobufBuilders cachedDeviceId → lazy
  - 已修复：Throttle.lastRun → ConcurrentHashMap
  - 需验证：编译通过 + 运行测试

### 🟡 中优

- [ ] **Android 双网络栈统一**
  - `AndroidWebSocketClient`（Netty）+ `NetworkRepository`（OkHttp/Ktor）
  - 应统一为 Ktor，删除 Netty 依赖（仅桌面端保留）

- [ ] **Agent 工具调用 UI**
  - 后端已支持 TOOL_CALL/TOOL_RESULT/USAGE
  - KMP 需扩展 `onAgentStreamChunk` 处理工具调用展示

- [ ] **离线消息队列持久化**
  - 当前 `pendingMessages` 仅内存，应用重启丢失
  - 应持久化到本地 DB（SQLDelight）

### 🔵 低优

- [ ] build.gradle Groovy → Kotlin DSL 迁移
- [ ] fat JAR 打包改用 Shadow plugin
- [ ] 静态分析（detekt / ktlint）

---

## 🖥 CLI 客户端

### 🟡 中优

- [ ] `/msg <userId> <text>` 直聊命令
- [ ] 密码输入安全（Windows 下 `System.console()` 返回 null）
- [ ] 日志框架（slf4j-simple 或 Kermit 替代 println）
- [ ] 离线消息拉取（登录后调用 syncMessages）
- [ ] 自动重连（连接断开后指数退避重试）

### 🔵 低优

- [ ] 命令历史与自动补全（jline3）
- [ ] 终端彩色输出
- [ ] `/help <command>` 详细帮助
- [ ] 配置文件持久化（`~/.qingliao/config.properties`）

---

## 前后端协同

| # | 问题 | 状态 | 建议 |
|---|------|------|------|
| 1 | 消息 ID 双标准 | 后端 UUID，客户端自算 | 统一由后端生成 |
| 2 | 群组 ID 符号约定 | Android 负值，Web 正值 | 统一为正值 |
| 3 | 设备管理协议 | 登录有 deviceType，无踢下线 | 实现 DeviceMessage |
| 4 | Protobuf 版本演进 | 无版本字段 | buf breaking CI 检测 |
