# CharRoom API 文档

## 1. 当前客户端 API 定义

### 1.1 用户相关

| 常量 | 方法 | 请求路径 | 请求方式 | 请求体 | 返回类型 |
|---|---|---|---|---|---|
| `LOGIN` | `login` | `/user/login` | POST | `{account, password}` | `ApiResponse<String>` (token) |
| `REGISTER` | `register` | `/user/register` | POST | `User` JSON, 主要包含 `username`, `password`, `email` | `ApiResponse<Integer>` (userId) |
| `USER_PROFILE` | `getUserInfo` | `/user/profile` | GET | Bearer token | `ApiResponse<User>` |
| `USER_PROFILE_UPDATE` | `updateUserProfile` | `/user/profile/update` | POST | `{username, phone, signature, password?}` | `ApiResponse<Boolean>` |
| `USER_PROFILE_UPDATE_EMAIL` | `updateEmail` | `/user/profile/updateEmail` | POST | `{email, verifyCode}` | `ApiResponse<Boolean>` |
| `SEND_EMAIL_UPDATE_VERIFY_CODE` | `sendEmailUpdateVerifyCode` | `/user/sendEmailUpdateVerifyCode` | POST | `{email}` | `ApiResponse<Boolean>` |
| `USER_DETAIL` | `getUserDetail` | `/user/get` | GET | `?userId=...` | `ApiResponse<User>`? |

### 1.2 好友 & 群组

| 常量 | 方法 | 请求路径 | 请求方式 | 请求体/参数 | 返回类型 |
|---|---|---|---|---|---|
| `FRIEND_GET` | `getFriendList` | `/friend/get` | POST | Bearer token | `ApiResponse<List<User>>` |
| `FRIEND_ADD` | `addFriend` | `/friend/add` | POST | `{account}` | `ApiResponse<Boolean>` |
| `FRIEND_ACCEPT` | `acceptFriend` | `/friend/accept` | POST | `{requestId}` | `ApiResponse<Boolean>` |
| `FRIEND_REJECT` | `rejectFriend` | `/friend/reject` | POST | `{requestId}` | `ApiResponse<Boolean>` |
| `FRIEND_REQUESTS` | `getFriendRequests` | `/friend/requests` | GET | Bearer token | `ApiResponse<List<User>>` |
| `GROUP_GET` | `getGroupList` | `/group/get` | GET | Bearer token | `List<Group>` / `ApiResponse<List<Group>>` |
| `GROUP_DETAIL` | `getGroupDetail` | `/group/get/detail` | GET | `?id=...` | `Group` / `ApiResponse<Group>` |
| `GROUP_ADD` | `addGroup` | `/user/addgroup` | POST | `{groupId}` | `ApiResponse<Boolean>` |

### 1.3 文件与头像

| 常量 | 方法 | 请求路径 | 请求方式 | 请求体 | 返回类型 |
|---|---|---|---|---|---|
| `FILE_UPLOAD` | `uploadFile` | `/file/upload` | POST | Multipart `file` | `ApiResponse<String>` |
| `USER_AVATAR_UPLOAD` | `uploadAvatar` | `/user/avatar/upload` | POST | Multipart `file` | `ApiResponse<String>` |

### 1.4 AI 聊天

| 常量 | 方法 | 请求路径 | 请求方式 | 请求体 | 返回类型 |
|---|---|---|---|---|---|
| `AGENT_NL` | `agentChat` | `/agent/nl` | POST | `{message, stream}` | `ApiResponse<String>` |
| `AGENT_NL_STREAM` | 暂未在 `ApiClient` 明确使用 | `/agent/nl/stream` | POST | `text/event-stream` / JSON | SSE / 流式响应 |

## 2. 后端实际路由对照

### 2.1 UserController

- `GET /user/get` – 通过 `@RequestAttribute("UserId")` 返回当前登录用户信息
- `GET /user/mongo/{id}` – 返回 `MongoUser`
- `POST /user/login` – 接收 `LoginRequest(account, password)`，返回 `ApiResponse<String>`
- `POST /user/loginByEmail` – 另一种登录路径
- `POST /user/validate` – 验证 token
- `POST /user/logout` – 退出登录
- `POST /user/register` – 接收 `User` 对象，返回 `ApiResponse<Integer>`
- `POST /user/sendVerifyCode` – 发送注册验证码
- `POST /user/sendEmailUpdateVerifyCode` – 发送邮箱修改验证码
- `POST /user/verifyRegister` – 验证注册验证码并注册
- `POST /user/addgroup` – 申请加入群组
- `GET /user/profile` – 获取当前用户信息
- `POST /user/profile/update` – 更新用户信息
- `POST /user/profile/updateEmail` – 更新邮箱

### 2.2 FriendController

- `POST /friend/add` – 添加好友
- `POST /friend/accept` – 接受好友请求
- `POST /friend/reject` – 拒绝好友请求
- `GET /friend/requests` – 获取好友请求列表
- `POST /friend/del` – 删除好友
- `POST /friend/get` – 获取好友列表

### 2.3 GroupController

- `REQUEST /group/register` – 注册群聊
- `REQUEST /group/get/detail` – 获取群聊详情，参数 `id`
- `GET /group/get` – 获取当前用户群聊列表
- `REQUEST /group/getUsers` – 获取群聊成员
- `POST /group/addMember` – 添加群成员
- `POST /group/del` – 删除群聊
- `GET /group/requests` – 获取群聊申请列表
- `POST /group/accept` – 同意群聊申请
- `POST /group/reject` – 拒绝群聊申请

### 2.4 AvatarController / FileController

- `POST /user/avatar/upload` – 上传/更新头像
- `GET /user/avatar/get` – 获取当前头像链接
- `DELETE /user/avatar/delete` – 删除头像
- `POST /file/upload` – 上传文件

### 2.5 MessageController

- `REQUEST /message/getPersistedOfflineMessage` – 获取持久化离线消息
- `REQUEST /message/getOfflineMessage` – 获取离线消息

## 3. 发现的不一致点

1. `FRIEND_GET` 实际是 `POST /friend/get`，客户端已修正。
2. `GROUP_DETAIL` 客户端原来使用 `/group/getDetail`，后端实际是 `/group/get/detail`，已同步修复。
3. `getGroupDetail` 客户端使用 `?groupId=...`，后端实际需要 `?id=...`，已修正。
4. `USER_DETAIL` 客户端目前以 `/user/get?userId=...` 调用，但后端 `/user/get` 实际只返回当前登录用户信息，不接受 `userId` 查询参数。
5. `GROUP_GET` 目前返回的是 raw `List<Group>`，而客户端统一解析为 `ApiResponse<T>`；如果没有全局响应包装器，这里会存在格式不一致风险。
6. `createGroup` / `inviteToGroup` / `leaveGroup` 在客户端仍然复用旧接口路径，语义上是“暂用现有接口”，需要后端明确提供专门接口才能完全匹配。

## 4. 结论

- 核心登录、注册、头像、文件、好友添加/申请、群组列表等主流程接口基本一致。
- 当前最重要的两个不一致点是：`USER_DETAIL` 语义与后端不符，以及 `GROUP_DETAIL` 路径拼写/参数错误。
- 如果你要继续，我建议先修正 `USER_DETAIL` 的调用逻辑：要么改成获取当前用户 `GET /user/profile`，要么后端新增“按 ID 获取用户详情”的接口。
