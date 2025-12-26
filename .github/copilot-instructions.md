# Copilot Instructions for CharRoom

## 项目概述
CharRoom 是一个跨平台的实时聊天应用程序，支持 Android 和桌面平台。

- **前端技术栈**：
  - Kotlin Multiplatform Mobile (KMM) 用于跨平台开发
  - Jetpack Compose 用于 UI 构建
  - 使用 Netty 作为网络通信框架
- **后端技术栈**：
  - Spring Boot（目录示例 `chatRoom/`）
  - Netty 用于高性能网络通信
- **数据存储**：
  - MySQL、Redis、MongoDB（按需使用）
- **通信协议**：
  - 前端通过 HTTP/JSON 调用后端接口
  - WebSocket 用于实时消息推送
  - 后端按 `type` 字段分发不同类型的消息

## 项目结构
```
src/
├── androidMain/           # Android 特定代码
│   ├── kotlin/            # Android 实现代码
│   └── res/               # Android 资源文件
├── commonMain/            # 跨平台共享代码
│   ├── kotlin/
│   │   ├── component/     # UI 组件
│   │   ├── core/          # 核心逻辑（网络、API 客户端等）
│   │   └── model/         # 数据模型
│   └── output/
├── desktopMain/           # 桌面平台特定代码
│   └── kotlin/            # 桌面平台实现
└── test/                  # 测试资源
```

## 目录约定
- `src/commonMain/kotlin/component/`：共享的 UI 组件，如聊天界面、登录界面等
- `src/commonMain/kotlin/core/`：核心业务逻辑，包括：
  - `ApiClient.kt`：REST API 客户端实现
  - `Chat.kt`：Netty 客户端和消息处理逻辑
  - `ServerConfig.kt`：服务器配置信息
- `src/commonMain/kotlin/model/`：共享的数据模型，包括：
  - `User`：用户信息
  - `Message`：私聊消息
  - `GroupMessage`：群聊消息
  - `Group`：群组信息
- `src/androidMain/kotlin/`：Android 平台特定实现
- `src/desktopMain/kotlin/`：桌面平台特定实现

## 客户端窗口构成
客户端界面由多个可组合的 UI 组件构成，采用分层架构设计：

### 主要UI组件
1. **LoginRegisterApp.kt**：应用入口点，包含登录和注册功能
   - 提供账号密码输入界面
   - 支持记住密码功能
   - 登录成功后跳转到主聊天界面

2. **ChatApp.kt**：主聊天应用窗口
   - 整合用户列表和聊天界面
   - 负责初始化网络连接和拉取离线消息
   - 根据屏幕尺寸自适应布局（横向/纵向）

3. **UserListScreen.kt**：左侧用户和群组列表面板
   - 显示好友和群组列表
   - 支持点击选择聊天对象
   - 自动拉取用户列表数据

4. **ChatScreen.kt**：好友聊天界面
   - 显示与选定好友的聊天记录
   - 提供消息输入框和发送按钮
   - 支持消息重发功能

5. **GroupChatScreen.kt**：群聊界面
   - 显示群组聊天记录
   - 展示发送者名称
   - 提供群聊消息发送功能

6. **AddUserOrGroupDialog.kt**：添加用户或群组对话框
   - 支持通过ID添加好友或加入群组

### 组件关系和交互流程
1. 应用启动时首先显示 `LoginRegisterApp` 组件
2. 登录成功后进入 `ChatApp` 主界面
3. `ChatApp` 包含 `UserList` 组件（左侧）和聊天区域（右侧）
4. 点击用户列表中的项会在右侧显示对应的 `ChatScreen` 或 `GroupChatScreen`
5. 各组件通过状态管理和回调函数进行数据传递和交互

## 鉴权与头部
- 使用 JWT 进行身份验证：
  - 前端在每次请求中添加 Header `Authorization: Bearer <token>`
  - 登录成功后，token 存储在 `ServerConfig.Token` 中
- 必须设置 `Content-Type: application/json; charset=utf-8`
- 所有需要认证的 API 接口都需要携带有效的 JWT token

## HTTP/JSON 消息约定
### 通用字段说明
- `type`：消息类型，字符串（示例值：`chat`、`groupChat`、`login`、`heartbeat`）
- `senderId`：发送者 ID（整数）
- `receiverId`：接收者 ID（私聊时使用）
- `groupId`：群聊时的群组 ID
- `text`：消息正文（字符串）
- `messageId`：客户端或服务端生成的唯一 ID（字符串）
- `timestamp`：时间戳，毫秒级 epoch 时间（长整数）

### 消息类型及示例
#### 登录消息
```json
{
  "type": "login",
  "senderId": 123,
  "timestamp": 1700000000000
}
```

#### 私聊消息
```json
{
  "type": "chat",
  "senderId": 123,
  "receiverId": 456,
  "text": "你好",
  "messageId": "uuid-1234",
  "timestamp": 1700000000000
}
```

#### 群聊消息
```json
{
  "type": "groupChat",
  "senderId": 123,
  "groupId": 789,
  "text": "大家好",
  "messageId": "uuid-5678",
  "timestamp": 1700000000000
}
```

#### 心跳消息
```json
{
  "type": "heartbeat",
  "senderId": 123,
  "timestamp": 1700000000000
}
```

## API 接口约定
所有 API 接口都遵循统一的响应格式：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

其中：
- `code`: 0 表示成功，非 0 表示失败
- `message`: 响应消息
- `data`: 实际数据内容

## 核心类说明
### 数据模型
- `User(id: Int, username: String)`：用户信息
- `Message(...)`：私聊消息实体
- `GroupMessage(...)`：群聊消息实体
- `Group(id: Int, name: String)`：群组信息

### 核心服务
- `ApiClient`：封装了所有 REST API 调用
- `CustomHttpResponseHandler`：处理 Netty 收到的 HTTP 响应
- `MsgType`：定义了所有支持的消息类型枚举

## 开发注意事项
1. 所有网络请求都应该异步执行，避免阻塞 UI 线程
2. 消息的 `messageId` 应该保证唯一性，用于消息确认和去重
3. 时间戳使用毫秒级 epoch 时间
4. 所有涉及用户身份的操作都需要进行 token 验证
5. 在处理消息时要考虑消息的发送状态 (`isSent`)，以便在网络异常时进行重试