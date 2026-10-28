# 客户端会话标识

本地会话由“类型 + 服务端原始 ID”组成，不用 ID 正负号表示私聊或群聊。
例如用户 42 和群组 42 是两个会话，联系人去重、UI key、选择状态、未读数、草稿和页面缓存均须保留类型。

## 原生桌面与 Android

`model/ConversationKey.kt` 定义 `ConversationKind` 和 `ConversationKey`：

```kotlin
ConversationKey.privateChat(42) // user:42
ConversationKey.group(42)       // group:42
```

`User` 作为联系人展示数据时携带 `conversationKind`，默认是私聊；`Group.toUiUser()` 明确设置群聊类型并保留正群 ID。
使用 `user.conversationKey` 合并联系人、比较选择、查找预览及生成 Compose key，不能只比较数字 ID。
用户 0 继续表示私聊助手，群 ID 必须大于零。

消息类型已经区分 `Message` 和 `GroupMessage`，各自缓存以原始正 ID 为键；不再转换负群 ID。
所有群消息插入、状态更新、ACK ID 归并和删除都访问同一个正 ID 桶。
会话预览使用 `Map<ConversationKey, ConversationPreviewState>`。
通知跳转同样传递 `ConversationKey`，避免同号联系人跳错会话。

## Web

`utils/conversationId.ts` 是唯一的构造、校验和解析入口：

```ts
ConversationId.privateChat('42').key // user:42
ConversationId.group('42').key       // group:42
ConversationId.forContact(contact)  // 根据 conversationKind 构造
ConversationId.parse('group:42').id // 原始 ID：42
```

`selectedChatId`、联系人 DOM/虚拟行 key、会话预览使用带类型的 `ConversationKey` 字符串。
联系人 `id` 和消息 `targetId`/`groupId` 保留服务端原始 ID，群联系人通过 `conversationKind: 'group'` 标记。
草稿与阅读位置跟随带类型的选择状态，用户和群组同号时互不覆盖。
ID 用十进制字符串保留精度，拒绝负数及不安全的 JavaScript 数字；不增加旧负 ID 解析分支。

## 存储与传输边界

- Web IndexedDB 的既有 schema：私聊使用对端 ID，群聊使用 `group:<id>`。通过 `historyKey` / `fromHistoryKey` 显式转换；这些转换是存储映射，不是 UI 类型推断。已有 IndexedDB 历史与游标无需迁移。
- 原生历史本来按私聊/群聊分目录并保留正 ID 文件名，本次不重写历史文件，也不增加旧格式兼容读取。新写入的群历史记录不再把接收 ID 取负。
- REST 与 Protobuf 的目标 ID 始终为原始 ID；消息类型决定私聊或群聊。服务端私聊会话 ID `user:<双方ID>` 与本地 `user:<对端ID>` 职责不同，不能互相替代。
- seqId 补拉、服务端 ACK、outbox 使用现有协议会话和消息 ID。此改造不改变协议字段、发送确认语义或重试身份。

## 验证

原生测试覆盖同号联系人合并、分别计未读、选择群只清群未读、群消息确认与删除；通知跳转测试按类型断言。
Web 测试覆盖严格解析、大 ID 精度、同号会话缓存与实时消息隔离；浏览器覆盖同号用户/群的草稿、发送类型、原始目标 ID、群未读和历史隔离。

2026-10-28 实际结果：Web 构建、102 项自动化测试及联系人/历史/社交三组浏览器回归通过；
桌面/CLI 编译与全量 87 项 JVM 测试通过；Android Kotlin 编译与 5 项单元测试通过。
Android 检查使用 `-x buildQuicNative`，未构建 JNI、未生成 APK 或装机；浏览器使用隔离夹具，未进行线上或真实账号收发测试。
本次没有修改后端或协议，也没有提交、推送。
