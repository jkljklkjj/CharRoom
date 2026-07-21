# TODO — CharRoom 全栈项目

## 🌐 前端 Web

### 🔴 高优

- [x] **Protobuf Web Worker 化** ✅
  - Worker 启动时立即初始化，作为主要编解码路径
  - 主线程仅在 Worker 不可用时 fallback
  - 新增 getWorkerStatus() 调试接口

- [ ] **乐观 UI + 服务端确认回滚**
  - 发送消息立即显示（isSent=optimistic）
  - ACK 返回后替换为服务端 seqId
  - 超时 16s 标记为 failed，支持重试
  - 现状：`pendingAcks` 已有基础逻辑，但 UI 状态更新不完整

- [x] **sync_hint 触发增量同步** ✅
  - 新增 syncConversation(conversationId, seqId) 函数
  - 收到 sync_hint 后调用 /sync/messages 拉取增量消息

- [x] **Agent 工具调用 UI** ✅
  - TOOL_CALL: 显示工具名称（🔧 调用工具: xxx）
  - TOOL_RESULT: 显示执行结果（✅/❌ 结果: xxx）
  - USAGE: 显示 Token 用量统计（📊 Token: input/output）

### 🟡 中优

- [ ] **离线首屏加载策略**
  - 虚拟滚动 + 分页 + seqId 增量同步
  - 现状：消息分页已实现（PAGE_SIZE=50），但无虚拟滚动

- [ ] **PWA 离线队列持久化**
  - IndexedDB + Service Worker 后台重发
  - 现状：`pendingQueue` 仅内存，页面刷新丢失

- [x] **Agent 工具调用 UI 展示** ✅
  - TOOL_CALL / TOOL_RESULT / USAGE 已在 WebApp.vue 中展示

- [x] **消息重试 UI** ✅
  - ⚠ 失败图标可点击，触发 retryMessage()
  - 生成新 messageId，重新编码 protobuf 发送

### 🔵 长期

- [ ] WebTransport Datagram（不可靠传输，心跳/状态同步）
- [ ] WASM 编解码（protobuf.wasm 提速 3-5×）

---

## 📱 KMP 客户端

### 🔴 高优

- [x] **Agent 流式协议适配** ✅
  - ProtobufResponseParser.kt 按 payloadCase 分发
  - QuicClientImpl + CronetQuicClient 使用 AgentStreamChunk
  - ChatApp.kt onAgentStreamChunk 回调正常

- [x] **sync_hint 处理** ✅
  - MsgType 新增 SYNC_HINT
  - MessageReceiveListener 新增 onSyncHint
  - QuicClientImpl + CronetQuicClient 解析 sync_hint
  - ChatApp 注册回调 → syncConversation 增量同步

- [x] **Thread safety 修复验证** ✅
  - sessions → ConcurrentHashMap
  - rttWindow → Collections.synchronizedList
  - listener add/remove → synchronized
  - cachedDeviceId → lazy
  - Throttle.lastRun → ConcurrentHashMap

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
