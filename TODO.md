# TODO — CharRoom 全栈项目改进

## 后端 (chatRoom)

### 🔴 高优

#### 1. ChatHandler 单方法过重（283 行）
- 参数校验→消息构建→本地推送→入库→跨网关转发→离线保存→ACK→异常处理，**全部在一个 `handle()` 方法**
- 建议拆分为 `MessagePipeline`：`Validator → LocalRouter → RemoteRouter → Persister → Acker` 链式调用
- 另外 4 个 `catch(Exception)` 应针对具体异常类型处理

#### 2. SessionManager 膨胀（596 行）→ 拆解
- 当前职责：连接管理 + 群组路由 + 在线状态 + Caffeine 缓存 + 清理
- 建议拆分：
  - `ConnectionRegistry` — 连接注册/注销
  - `GroupChannelManager` — 群组通道管理
  - `UserRouteCache` — 路由缓存单独提取
  - `SessionCleanupExecutor` — 清理逻辑

#### 3. fastjson2 与 Jackson 混用
- `KafkaProducer.java` 用 `com.alibaba.fastjson2.JSONObject`，其余部分用 Jackson
- 两套序列化库增加包体积和维护成本，应当统一为 Jackson（Spring Boot 原生支持）

#### 4. KafkaConsumer 异常处理薄弱
- 16 处 `catch(Exception)`，未区分可重试/不可重试异常
- 无 dead letter queue 机制：处理失败的消息直接丢弃或一直重试
- 应引入 Spring 的 `@RetryableTopic` + `@DltHandler` 或手动 DLQ

### 🟡 中优

#### 5. QuicServer 启动逻辑过重（469 行）
- 证书生成、ALPN 协商、多通道监听、`SmartLifecycle` 全在一个文件
- 建议将 `QuicServerInitializer`（Channel 初始化）从 `QuicServer` 中抽离

#### 6. 硬编码字符串散落
- `ServiceConstant` 虽然存在，但 `"登录成功"`、`"参数错误"`、`"未登录或会话无效"` 等字符串直接硬编码在 handler 中
- 建议抽取为 `ResponseMessages` 常量类，便于 i18n

#### 7. Token 校验路径重复
- `JwtRequestFilter`（REST）和 `LoginHandler`（WebSocket）各自做了一套 token 校验
- 公共逻辑应提取到 `JwtService.validateAndParse(token)` 统一调用

#### 8. PendingMessageManager 重试策略单一
- 当前固定 3 次重试，固定间隔
- 应支持指数退避 + 最大延迟上限，避免突发重试打垮下游

### 🔵 低优

#### 9. `pom.xml` 依赖管理
- `<repository>` 声明了 5 个源，阿里云被注释，实际 maven central 已经够用
- 缺少 `versions-maven-plugin` 季度依赖升级配置
- `spring-boot-starter-parent 4.1.0` → 检查是否有更新版本可用

#### 10. 计量埋点缺失
- Agent 成功/失败率、Token 消耗、向量检索延迟、网关推送成功率等无 Micrometer 埋点
- `MetricsConfig.java` + `NettyMetrics.java` 存在但只注册了基础指标

---

## CLI 客户端 (cliMain)

### 🔴 高优

#### 1. 无 `/msg <userId> <text>` 直聊命令
- CLI 只能 `/ai` 对话，没有给人发消息的能力
- 补上私聊和群聊命令是 MVP 级别的缺失

#### 2. 密码输入安全隐患
- `interactiveLogin()` 中 `System.console()?.readPassword()` 有正确实现，但 `?: readLine()` 回退明文读取
- Windows 下 `System.console()` 常返回 null，密码回显在终端上

#### 3. 无日志框架，全靠 `println`
- 0 处使用 timber/slf4j/Logger，全部 `System.out.println`
- 调试问题无从追溯，应引入 slf4j-simple 或 Kermit

### 🟡 中优

#### 4. 无配置文件机制
- 服务器地址、端口、用户名全部硬编码或靠命令行参数 `--server` 传入
- 应支持 `~/.qingliao/config.properties` 持久化配置

#### 5. `runBlocking` 阻塞式 REPL
- 命令行读行期间完全阻塞协程，无法异步处理推送消息
- 建议改用 `kotlinx-coroutines-jdk8` 的 `runBlocking` + 异步行读取，或拆分为双线程

#### 6. 无离线消息拉取
- `transport.start()` 成功后没有调用 `syncMessages` 拉取离线期间的消息
- 登录后直接 `"已加载 N 个联系人"`，但看不到任何历史消息

#### 7. 无自动重连
- `transport.start()` 失败或连接中断后命令行直接退出或静默失败
- 没有任何重试逻辑

### 🔵 低优

#### 8. 无命令历史与自动补全
- 原始 `readLine()`，无上下翻历史、无 Tab 补全（/fri → /friend）
- 可引入 `jline3` 提供行编辑能力

#### 9. 终端输出无颜色区分
- 所有输出同样格式，无法区分"自己发的消息"和"收到的推送"
- 建议对 `/ai` 输出、私聊、群聊、系统消息分别着色

#### 10. 无 `/help <command>` 详细帮助
- `/help` 只打出一行，对 `--server`、`--quic-port` 等启动参数无说明

---

## KMP 客户端 (commonMain / desktopMain)

### 🔴 高优

#### 1. `catch(Exception)` 不重抛 CancellationException（15 处）
- `ChatViewModel.kt` 中有 15 处 `catch(e: Exception)`，其中多处吞掉了协程取消信号
- 每一处都应加 `if (e is CancellationException) throw e`

#### 2. Android 端 `e.printStackTrace()` 散落（9 处）
- `MainActivity.kt`（2 处）、`ChatApplication.kt`（1 处）、`AvatarCropDialog.kt`（1 处）、`AndroidLocalChatHistoryStore.kt`（3 处）、`ImageLoader.android.kt`（2 处）
- 全部替换为 `timber.log.Timber.e(e, ...)` 或 `logger.error(...)`

### 🟡 中优

#### 3. `Glassmorphism.android.kt` 为死代码
- `glassmorphism()`、`softShadow()`、`DynamicBlurBackground()` 三个函数在任意代码中均**未被调用**
- 无 `expect` 声明，无引用。建议确认后删除（及 desktop 对应文件），或补上 expect/actual

#### 4. Android 端 Netty 与 Ktor OkHttp 双网络栈
- `AndroidWebSocketClient`（Netty）+ `NetworkRepository`（OkHttp/Ktor）
- 两份网络栈都用于连接同一后端，徒增 APK 体积和复杂度
- 应统一：WebSocket 和 REST 都走 Ktor，删除 Netty 依赖（仅桌面端保留）

#### 5. `AndroidChatViewModel` 与 `ChatViewModel`（common）功能大量重复
- `sendMessage`、`connectWebSocket`、`disconnectWebSocket`、`reconnect`、`onNetworkDisconnected` 等逻辑在两者中各实现一遍
- Android 端应尽量复写 common 的 `ChatViewModel` 而非重写

#### 6. `LocalDataSourceImpl` half-baked
- `saveUserProfile/getUserProfile`、`saveFriends/getFriends`、`saveGroups/getGroups`、消息存储方法全是 **TODO 空实现**
- 桌面端自动登录、离线缓存等功能实际不可用

### 🔵 低优

#### 7. `build.gradle`（Groovy）→ 未迁移 Kotlin DSL
- `androidApp/build.gradle` 是项目唯一 Groovy 构建文件，其余均为 `.gradle.kts`

#### 8. 桌面端 fat JAR 打包依赖 `runtimeClasspath`
- `customJar` 使用 `configurations.runtimeClasspath` 将所有依赖解压合并，存在重复 META-INF 文件
- 应改用 Gradle Shadow plugin 或 `com.github.johnrengelman.shadow`

#### 9. 缺少静态分析
- 无 detekt、spotless、ktlint 等 Kotlin 代码格式化/检查工具
- 无用 import、命名不一致等问题无法在 CI 层拦截

---

## 前后端协同

| # | 问题 | 现状 | 建议 |
|---|------|------|------|
| 1 | **消息 ID 生成双标准** | 后端 `CommonUtil.generateMessageId()` UUID，客户端 `MessageIdGenerator` 自算 | 统一由后端生成，客户端只传递，避免冲突 |
| 2 | **群组 ID 符号约定** | Android 用负值表示群组，Web/Desktop 用正值 | 统一为正值，在所有端用同一约定 |
| 3 | **心跳/ACK 重复** | 后端 `HeartbeatHandler` + 客户端心跳双端互发，WebSocket AND QUIC 各有一套 | 协议统一：WebSocket 心跳复用 QUIC 的 IdleStateHandler 模式 |
| 4 | **设备管理协议缺失** | 登录携带 deviceType，但无列表/踢下线/互踢策略 | 已在路线图中但未实现 |
| 5 | **CI 缺失** | 项目无 GitHub Actions / GitLab CI | 至少加 PR check：`mvn compile` + `detekt` + Protobuf breaking change detect |

---

## 代码质量速览

| 指标 | 后端 (Java) | KMP 客户端 (Kotlin) | CLI |
|------|------------|-------------------|-----|
| 文件数 | 145 Java | ~100 Kotlin | 1 Kotlin |
| `printStackTrace()` | 0 ✅ | 9 ❌ | 0 ✅ |
| `catch(Exception)` 超 10 处 | ❌ | ❌（15 处） | ✅ |
| 日志框架 | SLF4J ✅ | Timber + Kermit 混用 | ❌ 纯 println |
| 单测覆盖率 | 约 20%（26 个测试类） | 极少 | 0 |
| 死代码 | 少量 import | Glassmorphism 等 | — |
