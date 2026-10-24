# Android 传输与发送状态核对

2026-10-24：Android 已从 Cronet 切换到 quiche/JNI 的原生 QUIC。已验证原生库与隔离协议互通；APK 和真机验证仍未完成。原生界面三态模型尚未迁移。

## 界面已经区分了什么

| 客户端 | 当前实现 | 缺口 |
| --- | --- | --- |
| Web | `sending` 显示沙漏，`failed` 显示重试标记；成功 ACK 后变为 `sent`，移除等待/失败标记 | 已有三态；不需要重复增加同一套显示 |
| 桌面 / Android Compose | 失败时显示“发送失败”和重发入口；共享模型只有 `isSent: Boolean` | 缺少独立的等待 ACK 状态；当前默认 true，会提前显示为成功外观 |

源码入口：

- Web：`web/src/components/ChatWindow.vue`、`web/src/services/chatSocket.ts`。
- 共享模型：`src/commonMain/kotlin/model/data.kt` 的 Message、GroupMessage。
- 原生发送：`presentation/viewmodel/ChatViewModel.kt` 的 sendPrivateMessage、sendGroupMessage；两者先创建 `isSent=true` 的本地消息。
- 原生渲染：`component/chat/MessageBubble.kt`、ChatScreen.kt、GroupChatScreen.kt 以 `!isSent` 显示失败/重发。

上述省略前缀的路径相对于 `src/commonMain/kotlin/`。AndroidChatViewModel 继承共享 ChatViewModel，没有另一套三态实现。

原生状态迁移应使用明确的 `SENDING / SENT / FAILED`，贯穿消息模型、ChatState、ViewModel、气泡和桌面/Android 历史存储。首次发送和重试进入 SENDING，匹配成功 ACK 才进入 SENT，服务端拒绝、超时或最终发送失败进入 FAILED；自动重试期间保持 SENDING。重试沿用 messageId。旧历史的 true/false 可分别迁移为 SENT/FAILED；新版本必须保存明确状态，恢复遗留 SENDING 时不能当作已确认成功。

这里的 SENT 只表示服务端已接受，不能用于证明对方已收到或已读。

## 原 Cronet 路径的问题

已移除的 `CronetQuicClient` 使用 `newBidirectionalStreamBuilder` 访问 `/.well-known/webtransport`，随后对 `/stream/<targetId>` 创建独立 HTTP 请求，并读写“4 字节长度 + protobuf”。它没有建立浏览器采用的 WebTransport 会话与子流。

Cronet 的 BidirectionalStream 是 HTTP/2 或 QUIC 上的 HTTP 双向请求，默认 HTTP 方法为 POST，公开 Java 接口没有 WebTransport 会话/子流操作。参见 [Chromium BidirectionalStream 接口](https://chromium.googlesource.com/chromium/src/+/lkgr/components/cronet/android/api/src/org/chromium/net/BidirectionalStream.java) 和 [Android Builder 文档](https://developer.android.com/develop/connectivity/cronet/reference/org/chromium/net/BidirectionalStream.Builder)。这里依据公开 API 和项目调用方式判断，不代表 Chromium 内部没有 WebTransport 实现。

后端 `QuicWebTransportHandler` 在 WebTransport 路径处理 CONNECT，会话建立后的数据子流还需要正确的类型前缀与 sessionId；CONNECT 流中的 HTTP DATA 被当作胶囊数据跳过。单独设置 HTTP 方法为 CONNECT，仍不具备所需的子流能力。

旧实现还在 `stream.start()` 后立即设为已连接，没有等待登录结果，并缺少首次读取。新实现已移除这条路径及 Play Services Cronet 依赖。

## 已实现的原生 QUIC 路径

后端 `QuicServer` 已同时声明 `custom` 与 `h3` ALPN；`QuicStreamRouter` 对 custom 的 stream 0 处理控制消息，其他流处理会话消息。因此 Android 可以像桌面一样走已有的原生 QUIC 通道，Web 继续使用 WebTransport。

当前采用固定版本 Cloudflare quiche，经 Rust JNI 驱动接入 `NativeQuicChatTransport`，由 `QuicheChatClient` 适配 Android。构建 arm64-v8a 与 x86_64，并打包 NDK 的 C++ 运行时。构建脚本、CI 前置条件及复现命令见 [原生 QUIC](../../native/README.md)。

连接依次等待 QUIC/TLS 握手、控制流 LOGIN 响应、会话流 STREAM_INIT_ACK。私聊与群聊根据匹配业务 ACK 返回发送结果；失败、超时与关闭清理待发回调。证书链和主机名校验保持开启；网络故障通过退避重连并重新登录恢复。

已完成的验证与剩余验收：

1. 共享会话测试覆盖登录前不可发送、ACK 匹配/拒绝、分片读取、初始化超时、重连、身份不匹配、关闭与首条写入异常的批量回调清理。
2. Rust 驱动测试通过；两个 Android ABI 的原生共享库已交叉编译，并检查 ELF 的 16 KiB 页对齐和运行时依赖。
3. 真实 Kotlin/JNI 连接隔离的后端 QuicServer：两个临时身份双向收发 48 KiB 单聊，收到对应业务 ACK；错误主机名和不可信证书均被拒绝。认证及投递使用替身，不访问线上账号、持久化或生产服务。
4. APK 构建仍受本机 Gradle 的 `Unable to establish loopback connection` 阻断；Android API 适配器已做独立编译（全局状态为测试替身），不能据此宣称整包通过。尚无设备验证安装、群聊、前后台、断网重连与账号切换。

后端目前 protobuf 解析上限为 64 KiB，与拆帧器的 1 MiB 上限不同；Android 按实际限制提前拒绝超限包，避免等待被静默忽略的消息。未修改后端部署、恢复用户删除的 Wrapper 或发布 APK。
