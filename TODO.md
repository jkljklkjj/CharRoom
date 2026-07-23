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
- [x] localStorage O(n) 消息持久化 → IndexedDB

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

- [x] **`syncAllConversations` 每页重建 existingIds Set** — 循环外构建，增量更新
- [x] **`ChatScreen` 收集全局 allMessages 但只用 per-conversation slice** — 移除不必要的 collectAsState
- [x] **`UserListScreen` 收集全量消息计算 subtitle** — userPreviews 缓存 subtitle 和 lastMessageTime
- [x] **`ChatState` emit 每次创建新 List** — 结构相等性检查

### P2 — 代码质量

- [x] **`println()` 替换为 AppLogger** — 创建 AppLogger 封装，ChatViewModel 已替换
- [x] **三层 API 封装简化** — 删除 RemoteDataSource，Repository 直接调用 ApiClient，减少 300+ 行
- [x] **Koin DI 迁移** — ViewModels 使用 koinInject()，移除 Global 单例
- [x] **ChatScreen/GroupChatScreen 代码重复** — 已提取 MessageBubble, ChatInputBar, MessageLongPressMenu 等公共组件，消除 731 行重复
- [x] **LocalDataSourceImpl KMP 兼容** — Desktop/CLI 使用 FileProvider 接口，Android 使用 SharedPreferences 实现
- [x] **`Util.kt` 用 Jackson — 已删除（死代码）**
- [x] **`formatDate()` 硬编码中文 — 已用 i18n 替代**
- [x] **`UserListScreen` 重复 formatTime — 已移除**

### 低优

- [ ] build.gradle Groovy → Kotlin DSL 迁移
- [ ] 静态分析（detekt / ktlint）

---

## 🖥 CLI 客户端

### 中优

- [x] **`/msg <userId> <text>` 直聊命令** — 已实现
- [x] **密码输入安全** — 使用 `System.console()?.readPassword()`，不回显
- [ ] 日志框架（slf4j-simple 或 Kermit 替代 println）
- [x] **离线消息拉取** — 登录后自动拉取
- [x] **自动重连** — 连接断开后指数退避重试（最多10次）

### 低优

- [ ] 命令历史与自动补全（jline3）
- [ ] 终端彩色输出
- [ ] `/help <command>` 详细帮助
- [ ] 配置文件持久化（`~/.qingliao/config.properties`）

---

## 前后端协同

| # | 问题 | 状态 | 说明 |
|---|------|------|------|
| 1 | 消息 ID 双标准 | ✅ 已正确 | 客户端生成 ID 用于 ACK 关联，后端使用同一 ID 做去重。`CommonUtil.generateMessageId()` 仅在客户端未提供 ID 时作为 fallback |
| 2 | 群组 ID 符号约定 | ✅ 已正确 | 前端用负数区分群组/用户（UI 约定），后端用正数，protobuf 用 string 类型。转换在 `toUiUser()` 中处理 |
| 3 | 设备管理协议 | ⏳ 待实现 | 需要 `DeviceMessage` protobuf + 多设备管理端点。当前已有 `device_type` 和 `device_id` 字段 |
| 4 | Protobuf 版本演进 | ✅ 已完成 | buf breaking CI 检测已配置（`.github/workflows/ci.yml`） |

### 设备管理协议详细设计

**需要实现的功能：**
1. `DeviceMessage` protobuf 定义
2. 设备注册/注销端点
3. 多设备在线状态查询
4. 设备间消息同步
5. 设备管理 UI（查看/删除设备）

**现有基础：**
- `LoginMessage` 已有 `device_type` 和 `device_id` 字段
- `SessionManager` 已支持多设备连接
- 前端已有设备 ID 生成逻辑
