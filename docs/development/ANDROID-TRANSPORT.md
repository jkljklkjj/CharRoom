# Android 传输与发送状态核对

2026-10-24 依据本地源码与 Cronet 公开接口核对。本文件记录现状与修复要求，不表示 Android 传输适配或原生三态已经实现。

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

## Android 当前为什么不能直接连接 WebTransport

当前 `CronetQuicClient` 使用 `newBidirectionalStreamBuilder` 访问 `/.well-known/webtransport`，随后又对 `/stream/<targetId>` 创建独立 HTTP 请求，并读写“4 字节长度 + protobuf”。这不是浏览器采用的 WebTransport 会话与子流。

Cronet 的 BidirectionalStream 是 HTTP/2 或 QUIC 上的 HTTP 双向请求，默认 HTTP 方法为 POST，公开 Java 接口没有 WebTransport 会话/子流操作。参见 [Chromium BidirectionalStream 接口](https://chromium.googlesource.com/chromium/src/+/lkgr/components/cronet/android/api/src/org/chromium/net/BidirectionalStream.java) 和 [Android Builder 文档](https://developer.android.com/develop/connectivity/cronet/reference/org/chromium/net/BidirectionalStream.Builder)。这里依据公开 API 和项目调用方式判断，不代表 Chromium 内部没有 WebTransport 实现。

后端 `QuicWebTransportHandler` 在 WebTransport 路径处理 CONNECT，会话建立后的数据子流还需要正确的类型前缀与 sessionId；CONNECT 流中的 HTTP DATA 被当作胶囊数据跳过。单独设置 HTTP 方法为 CONNECT，仍不具备所需的子流能力。

此外客户端目前在 `stream.start()` 之后立即 `connected=true` 并写登录包，没有等待流就绪和登录结果；读取只在 onReadCompleted 中继续，缺少第一次 read。现有类注释把 HTTP BidirectionalStream 等同于原生 QUIC stream，也应在替换实现时更正。

## 推荐的适配方向

后端 `QuicServer` 已同时声明 `custom` 与 `h3` ALPN；`QuicStreamRouter` 对 custom 的 stream 0 处理控制消息，其他流处理会话消息。因此 Android 可以像桌面一样走已有的原生 QUIC 通道，Web 继续使用 WebTransport。

这条路径需要 Android 可用的 QUIC 实现，再适配现有 ChatTransport；不能直接假定桌面 Netty 的 linux/windows/osx 原生依赖能装入 Android。选库前先验证目标 ABI、证书与主机名校验、自定义 ALPN、双向流、入站流和取消能力。当前方案不指定未经 Android 构建与实机验证的库，也不需要先新增后端 POST 隧道。

如果目标是所有客户端统一 WebTransport，则应采用实际暴露 HTTP/3 extended CONNECT、会话及子流能力的 Android 实现，并按后端路径适配；这是另一条完整传输实现，不能靠 Cronet 的普通请求参数完成。

适配验收必须覆盖：

1. 单独区分传输建立、控制流就绪和业务登录成功；认证失败不显示在线，不恢复待发队列。
2. 复用已有 protobuf builder、按流解帧器、分发器与 PendingMessageAcks；写入完成不更新为 SENT。
3. 真正启动读取，处理半包/合并包、失败回调、旧连接回调、取消及心跳资源清理。
4. 使用隔离账号在实机上验证双向单聊/群聊、失败/超时 ACK、断网重连与账号切换；分别报告编译、协议测试和实机联通。

本次仅做源码/API 核对与文档澄清。Android 前置检查发现 SDK 目录存在，但两个 Gradle Wrapper 文件仍为原有删除状态；未恢复文件，也未运行 APK 构建或声称实机互通。
