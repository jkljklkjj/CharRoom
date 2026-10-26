# 新设备历史恢复

登录后 Web `WebApp.syncAllConversations` 和 Kotlin `ChatViewModel.syncAllConversations` 分页请求 `POST /api/messages/recent`，恢复最近七天的双向私聊和当前所属群聊。接口身份来自认证上下文，群范围由后端计算。旧离线接口只取未送达消息，不能承担历史恢复。

后端返回固定 `windowEnd`、`nextTimestamp`、`nextId`、`hasMore`；后续请求携带同一窗口与联合游标，直到无下一页。消息 ID 与 seqId 保持十进制字符串（Kotlin 需要时转 Long），时间戳是 epoch 毫秒。失败保留已应用页面，下次登录从第一页重复恢复并去重；没有永久的“已完成”标记。

历史恢复不增加未读数、不修改送达标记、不推进增量 seq 游标。Web 用 `restoreHistoryPage` 按会话批量合并，IndexedDB v5 增加按账号、会话、消息 ID 的索引，防止恢复与实时重投重复落库。会话记录按时间和 ID 排序，账号切换后丢弃旧响应并保留同步期间的新消息。

Kotlin 使用 `core/RecentHistory.kt` 的明确 wire DTO 与分页协调函数。每页先写入账号所属的本地文件，再合并到 UI；UI 的 200 条 LRU 不代表完整历史。桌面与 Android 按对方 ID 保存私聊、按消息 ID 合并文件，不再以 500 条截断历史；手动删除消息显式移除文件内的记录。文件同时保存 seqId、conversationId 和附件/引用字段，字段本身不代表同步进度。

## 本轮验证

- Web：`npm run typecheck`、`npm test`（33 项）、`npm run build` 通过。
- Desktop/JVM：`scripts/gradle-windows.ps1 test`，49 项通过；包含同时间戳多页、账号切换、失败页、停滞游标、无未读/游标副作用，以及 650 条双向消息落库后再读取、删除与账号隔离。
- Android：Kotlin 编译和现有 10 项单元测试通过，命令为 `scripts/gradle-windows.ps1 test :androidApp:compileDebugKotlin -x :androidApp:buildQuicNative`。该检查跳过原生构建，不能代表 APK 完整构建或运行通过；完整流程受本机 NDK 缺少主机工具链阻断。
- 未启动本地服务进行两个设备登录联调，未验证真实分片数据库的查询性能；安装与真机收发未运行。

更新顺序为后端新接口、客户端。接口不可用时记录历史恢复失败，不将离线接口结果当成完整历史。
