# Copilot Instructions for CharRoom

## 作用范围
- 本文件约束 `CharRoom` 客户端（Kotlin Multiplatform + Compose）。
- 后端代码在同级目录 `chatRoom`，除非用户明确要求，否则不要在客户端任务中修改后端。

## 技术与目录事实（以当前代码为准）
- UI 与业务共享层：`src/commonMain/kotlin/`
  - `component/`：界面与交互（如 `ChatApp.kt`、`UserListScreen.kt`）
  - `core/`：网络与协议核心（`ApiClient.kt`、`Chat.kt`、`ActionLogger.kt`、`ProtobufBuilders.kt`）
  - `model/`：用户、消息、群组模型
- 平台实现层：
  - `src/desktopMain/kotlin/`：桌面 actual 实现（含 protobuf builder/response parser 的 actual）
  - `src/androidMain/kotlin/`：Android actual 实现

## 通信与序列化约定
- REST：HTTP + JSON（`ApiClient`）。
- 实时：Netty WebSocket + Protobuf 二进制帧（`Chat.kt` 中 `BinaryWebSocketFrame`）。
- Protobuf payload 由 `core/ProtobufBuilders.kt` 定义 `expect`，各平台 `actual` 实现负责序列化。
- JWT：
  - HTTP 请求头使用 `Authorization: Bearer <token>`。
  - WebSocket 握手也携带同一 Authorization 头。

## Agent 与操作序列（当前实现）
- 客户端操作序列由 `core/ActionLogger.kt` 维护：
  - `Action(id, timestamp, type, targetId, metadata)`。
  - 固定容量滑动窗口，当前容量为 20。
- 发送路径：`ApiClient.callAgent()`。
  - 从 `ActionLogger.getSnapshot()` 读取序列。
  - 转为 `AgentActionDto`。
  - 作为 JSON 字段 `clientActions` 与 `input` 一起 POST 到 `/agent/nl`。
- 注意：当前实现不是通过 `X-Client-Actions` 请求头上传操作序列。

## 代码修改优先级
- 新需求优先落在 `commonMain`；仅在必须时补 `androidMain/desktopMain` 的 `actual`。
- 通信协议改动优先走现有路径：
  - REST 走 `ApiClient`。
  - WebSocket Protobuf 走 `Chat` + `ProtobufBuilders`。
- 保持既有响应解包容错逻辑（`code/data` 包裹与直接 JSON 兼容）。

## 新增消息类型时的同步清单
1. 在客户端 `MsgType` 增加枚举值（`core/Chat.kt`）。
2. 在 `ProtobufBuilders` 中补充 `expect/actual` 构造函数。
3. 在接收处理逻辑中补充解析与 UI 更新（`CustomWebSocketHandler` 与对应 UI 组件）。
4. 与后端同步更新 `message.proto`、`ServiceConstant`、`MessageHandlerFactory` 与处理器实现。

## 开发与安全注意事项
- 不在 UI 主线程执行阻塞网络操作。
- 不记录 token、密码等敏感信息；消息正文日志建议截断（现有代码常用 `take(64)`）。
- 不修改构建产物与生成目录（如 `build/`、`proto/build/`、`bin/`）。

## 提交前自检
1. 协议字段是否与后端一致（`type`、payload 字段名/类型）。
2. 认证头是否完整（HTTP + WS）。
3. `ActionLogger -> callAgent` 链路是否仍可序列化并发送 `clientActions`。
4. 桌面与 Android 的 `actual` 是否保持行为一致。
