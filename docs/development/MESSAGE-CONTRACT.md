# 客户端消息契约与代码组织

## 协议常量只有一个来源

主定义是相邻后端 `chat-room/src/main/proto/{message,agent_stream}.proto`。Kotlin 编译输入是 `src/main/proto/`，Web 构建输入是 `web/src/proto/`。

- Kotlin 的 `core.MsgType` 是生成的 `MessageWrapperType` 的类型别名，不再维护字符串枚举。构造和分发均使用生成的 protobuf 类型。
- Web 的 `src/proto/constants.ts` 由 `scripts/generate-protocol-constants.cjs` 生成；界面、socket、Agent 流分发都从这里导入。业务常量（例如助手账号）继续留在 `src/constants.ts`。
- `MSG_TYPE`、`PROTO_DEVICE_TYPE`、`CONVERSATION_TYPE`、`AGENT_STREAM_TYPE` 保留协议原名与数值。REST 设备标签仍是 `web` 等字符串；`DEVICE_TYPE_NUM` 是生成的标签到 wire 枚举映射。
- Web 枚举解码为数字；int64 解码为十进制字符串，避免精度损失。不要把显示名称或 REST 字符串当成 wire 类型。

Web 修改协议后运行 `npm run generate:protocol`。`npm run test:messages` 检查生成文件是否过期，并将所有枚举值与后端主定义比较；不会先自动重写常量来掩盖漂移。`npm run build` 会生成常量及两个 public proto 副本。

## 共享逻辑与平台边界

| 文件 | 职责 |
| --- | --- |
| `core/MessageProtocol.kt` | protobuf 类型别名、消息处理策略常量、发送消息 ID 提取 |
| `core/ProtobufBuilders.kt` | 编码消息，写入可重试的 messageId 和时间戳 |
| `core/QuicStreamProtocol.kt` | 长度前缀、帧大小限制、每条流独立的增量解码器 |
| `core/ProtobufMessageDispatcher.kt` | 每包解析/去重一次，然后分发给所有监听器；处理 ACK、Agent 和同步提示 |
| `core/PendingMessageAcks.kt` | 按 messageId 关联发送结果；拒绝、超时和关闭失败只回调一次 |
| 桌面 / Android 适配器 | 建立连接、提供已拥有的超时调度、收发字节、维护平台状态 |

消息 ID 必须在首次发送前确定，重试沿用同一个 ID。调用方已有 ID 时显式传给 builder。发送写入成功不触发聊天成功回调，匹配的成功 ACK 才会触发；非聊天控制包仍只报告本地写入结果。

去重不能放在遍历监听器的循环内。重复包不重复通知，但必须再次 ACK，允许服务端从 ACK 丢失中恢复。没有消费者的新包不会被记为已送达或回 ACK。Agent 独立 handler 不依赖是否注册 UI 监听器。发送 ACK 不自动推进完整历史同步游标。

QUIC 是字节流；Netty/Cronet 回调不是消息边界。每条流持有自己的 `QuicStreamProtocol.Decoder`，保留不完整头/负载，处理合并帧并拒绝非法长度。Cronet 回调的 buffer position 位于新写入数据末尾，读取前需要 `flip()`，参见 [Cronet API 契约](https://android.googlesource.com/platform/external/cronet/+/f3665646f37be4c4b0172c525db263759c1581fc/components/cronet/android/api/src/org/chromium/net/BidirectionalStream.java)。

## 2026-10-24 检查与验证

未发现 Kotlin 活跃收包路径中存在 Web 那种字符串/数值比较。确认并修改了以下问题：

- 原生 builder 遗漏 messageId，群聊还遗漏时间戳；ViewModel 未把已有 ID 传给 builder。
- 桌面和 Android 在监听器循环内去重，第二个监听器会漏收；桌面 Agent 独立 handler 也错误依赖监听器循环。
- 私聊/群聊写入后立即回调成功，未等待服务端 ACK。
- 桌面解帧丢弃跨 read 的半包；Android 直接把读回调当成 protobuf，且未转换 buffer position。
- 登出调用方将 token 传给已要求数字用户 ID 的 builder。

验证结果：

- Web：6 项 `npm run test:messages` 契约/行为测试通过。
- 共享 Kotlin：通过独立 `javac`、`kotlinc`、JUnitCore 执行四个测试类，覆盖 builder、分发、ACK 和增量解帧。ActionLogger 使用空快照测试替身；这不包含 Netty、Cronet、Compose 或实际服务器。
- 完整桌面/CLI/Android 构建未通过验证：两个 Wrapper 文件仍处于原有删除状态；调用缓存 Gradle 时，在配置阶段报 `Unable to establish loopback connection`。没有恢复 Wrapper。
- 后续 TypeScript 迁移已补齐 IndexedDB 分页/删除接口，完整 Web 构建通过；新增存储、Worker 回退与 API 刷新测试，详见 [迁移说明](WEB-TYPESCRIPT.md)。

标准构建恢复后，可运行：

```text
./gradlew.bat test --tests core.MessageProtocolTest --tests core.ProtobufMessageDispatcherTest --tests core.PendingMessageAcksTest --tests core.QuicStreamProtocolTest
```

也可以使用 `scripts/test-message-protocol.ps1`，通过参数提供 Kotlin 编译器、由当前协议生成的 Java 源码目录、protobuf/JUnit/Hamcrest JAR；脚本不下载依赖、不启动服务、不写入仓库生成目录。

## 尚待单独完成

- Android 使用普通 Cronet `BidirectionalStream`，没有实现服务端要求的 WebTransport CONNECT 会话/子流协议。启动也未等待 stream ready/登录响应，尚未启动首次读取；共享协议修正不等于 Android 已能联通当前服务器。需要先确定 Android 传输实现，再做真机联调。
- 原生界面的 `isSent` 仍是布尔值，初始采用乐观显示；完整产品状态还需区分“等待 ACK / 已接受 / 失败”。本轮修正的是传输回调的确认来源。
- 连接就绪与认证就绪的分离、离线队列持久化和历史游标恢复仍需完整可靠性回归；本轮不宣称断网不丢消息或 exactly-once。

本轮改动保存在客户端工作区源码中，没有重新部署网站或发布原生安装包。
