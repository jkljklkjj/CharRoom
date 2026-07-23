# TODO — CharRoom 全栈项目

---

## 🌐 前端 Web

- [x] WebTransport 断连修复（transport.closed + _safeOnClose）
- [x] isReconnecting 锁死修复
- [x] flushQueue 门控（仅登录成功后触发）
- [x] XSS 漏洞修复（DOMPurify）
- [x] seqId 全链路贯通（接收消息提取 seqId）
- [x] v-for key 改用 messageId
- [x] DOMPurify 预清洗

- [ ] localStorage O(n) 消息持久化 → IndexedDB

- [ ] WebTransport Datagram（长期）
- [ ] WASM 编解码（长期）

---

## 📱 KMP 客户端

### P0 — Bug

- [x] **seqId 全链路贯通** — MessageReceiveListener 增加 seqId/conversationId 参数
- [x] **`forwardMessage` 忽略 targetUser** — 改为调用 sendPrivateMessage
- [x] **`ConversationSyncService` 和 `MessageSender` 是死代码** — 已删除
- [x] **`MessageIdGenerator` 分钟级时间戳碰撞** — 改用毫秒级时间戳 + 原子计数器

### P1 — 性能

- [ ] `syncAllConversations` 每页重建 existingIds Set → 循环外构建，增量更新
- [ ] `ChatScreen` 收集全局 allMessages 但只用 per-conversation slice → 移除不必要的 collectAsState
- [ ] `UserListScreen` 收集全量消息计算 subtitle → 缓存 preview
- [ ] `ChatState` emit 每次创建新 List → 结构相等性检查

### P2 — 代码质量

- [ ] `println()` 替换为 Kermit logger
- [ ] 三层 API 封装合并（ApiClient → ApiService → RemoteDataSource）
- [ ] Koin DI 模块从未使用，Global 单例泛滥
- [ ] `ChatScreen` 和 `GroupChatScreen` 70% 代码重复 → 提取公共逻辑
- [ ] `LocalDataSourceImpl` 用 `java.io.File` — 非 KMP 兼容
- [ ] `Util.kt` 用 Jackson 而非 kotlinx-serialization — 死代码
- [ ] `formatDate()` 硬编码中文字符串 — 未用 i18n

### 低优

- [ ] build.gradle Groovy → Kotlin DSL 迁移
- [ ] 静态分析（detekt / ktlint）

---

## 🖥 CLI 客户端

### 中优

- [ ] `/msg <userId> <text>` 直聊命令
- [ ] 密码输入安全（Windows 下 `System.console()` 返回 null）
- [ ] 日志框架（slf4j-simple 或 Kermit 替代 println）
- [ ] 离线消息拉取（登录后调用 syncMessages）
- [ ] 自动重连（连接断开后指数退避重试）

### 低优

- [ ] 命令历史与自动补全（jline3）
- [ ] 终端彩色输出
- [ ] `/help <command>` 详细帮助
- [ ] 配置文件持久化（`~/.qingliao/config.properties`）

---

## 前后端协同

| # | 问题 | 建议 |
|---|------|------|
| 1 | 消息 ID 双标准 | 统一由后端生成 |
| 2 | 群组 ID 符号约定 | 统一为正值 |
| 3 | 设备管理协议 | 实现 DeviceMessage |
| 4 | Protobuf 版本演进 | buf breaking CI 检测 |
