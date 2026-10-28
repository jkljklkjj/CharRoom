# CharRoom 客户端协作约定

先执行 `git status --short`，保留已有修改。后端是相邻独立仓库 `../chat-room/`。修改 Web 时另读 [web/AGENTS.md](web/AGENTS.md)。

## 按实际构建理解项目

- 根构建应用的是 Kotlin **JVM** 插件，不是 `kotlin("multiplatform")`。`commonMain` 是共享源码目录命名，不能据此假定 JS/iOS target 或 expect/actual 源集已经存在。
- [build.gradle.kts](build.gradle.kts) 把 `src/commonMain/kotlin`、`src/desktopMain/kotlin`、`src/cliMain/kotlin` 加入同一 JVM main；目前排除 `**/*.android.kt`、`**/call/**`。
- Android 模块通过 [androidApp/build.gradle](androidApp/build.gradle) 加入共享目录及 `src/androidMain/kotlin`；自身代码在 `androidApp/src/main/kotlin/com/chatlite/charroom/`。
- [settings.gradle.kts](settings.gradle.kts) 仅在任务名称包含 `android` 时 include `androidApp`。需要 Android 时使用 `:androidApp:...`，不能假定 `customApkDebug` 会启用模块。
- 版本看构建文件与 [gradle.properties](gradle.properties)：JDK 21、Kotlin 2.3.20；不要把后端 JDK 25 直接套到客户端。
- Vue Web 是独立 TypeScript 应用，协议与功能需要单独同步。
- 原生更新检查使用 GET `/app/version/check` 与语义版本名，`PublishedAppVersion` 映射到更新提示；Web `/admin` 管理发布，启用与边界见相邻后端 [后台说明](../chat-room/docs/development/ADMIN-CONSOLE.md)。
- 安装包元数据、校验、恢复及安装交接见 [可靠更新](docs/development/RELIABLE-UPDATES.md)。版本统一修改 `gradle.properties` 的 `appVersion/appVersionCode`，不手改生成的 `BuildReleaseVersion`；启动安装器不代表更新成功。
- CLI 的命令、服务器隔离存储与验证见 [CLI 说明](docs/development/CLI.md)；发送复用 QUIC ACK，命令行不接受明文密码，未知斜杠命令不得转为 AI 请求。
- 消息 ID 在 `model/MessageIdGenerator.kt` 生成；ACK 的 `serverMessageId` 用于主键归并，`core/MessageIdAliases.kt` 先持久化账号/会话别名，再完成发送回调。
- `core/IndexedHistoryJournal.kt` 为桌面/Android 提供增量历史与索引分页；仅使用 `.history` 格式，不读取旧 JSON。格式边界见相邻后端的 `docs/development/JVM-AND-INTERACTION.md`。
- `core/GatewayOwner.kt` 负责连接前 owner 发现；`ReceivedMessagePersistence` 在接收 ACK 前持久化服务端消息；共享 `core/AtomicHistoryFile.kt` 供桌面和 Android（API 26+）原子替换历史文件。
- 原生发现页缓存与本人资料/回答草稿由共享 `core/SocialLocalCache.kt` 管理；Android 在 Application 中安装私有文件目录。缓存范围、失效与 Web 对齐情况见 [本地优先加载](docs/development/LOCAL-FIRST-DIRECTORY-AND-HISTORY.md)。

## 功能入口

| 场景 | 首读文件/目录 |
| --- | --- |
| 桌面 / CLI / Android 启动 | `src/desktopMain/kotlin/Main.kt`、`src/cliMain/kotlin/CliMain.kt`、`androidApp/src/main/kotlin/com/chatlite/charroom/MainActivity.kt`、同目录 `ChatApplication.kt` |
| 共享 UI / 状态 | `src/commonMain/kotlin/App.kt`、`component/`、`presentation/viewmodel/`、`core/state/` |
| REST / 登录 / token | `core/ApiEndpoints.kt`、`core/ApiClient.kt`、`data/repository/AuthRepository.kt` |
| 联系人与消息仓库 | `data/repository/ChatRepository.kt`、`data/datasource/local/`、`core/LocalChatHistoryStore.kt` |
| 传输接口与协议 | `core/ChatTransport.kt`、`core/QuicStreamProtocol.kt`、`core/ProtobufBuilders.kt`、`core/ProtobufResponseParser.kt` |
| 消息类型 / 收包 / 发送确认 | `core/MessageProtocol.kt`、`core/ProtobufMessageDispatcher.kt`、`core/PendingMessageAcks.kt`；组织与验证边界见 [消息契约](docs/development/MESSAGE-CONTRACT.md) |
| 桌面 QUIC | `src/desktopMain/kotlin/core/QuicClientImpl.kt`、`QuicNettyClient.kt`、`QuicChannelInitializer.kt` |
| Android 传输 | `src/androidMain/kotlin/com/chatlite/charroom/core/QuicheChatClient.kt`、同目录 `quic/`、`native/quiche-jni/`；构建见 [原生 QUIC](native/README.md) |
| DI / 行为上下文 | `core/di/`、平台 DI、`core/ActionLogger.kt` |

表中未写 `src/` 前缀的路径相对于 `src/commonMain/kotlin/`。`ARCHITECTURE.md` 包含目标分层方案，先确认其目录是否真实存在再应用。

## 构建与验证（工作目录：CharRoom）

先检查 `java -version`、`gradle/wrapper/gradle-wrapper.jar` 和 `.properties`。Windows 使用 `./gradlew.bat`，Unix 使用 `./gradlew`。

| 目的 | Windows 命令 |
| --- | --- |
| 配置与任务枚举 | `./gradlew.bat --version`、`./gradlew.bat tasks --all` |
| 协议生成 | `./gradlew.bat :proto:generateProto` |
| JVM 编译 / 启动 | `./gradlew.bat compileKotlin`、`./gradlew.bat run` |
| JVM 检查 | `./gradlew.bat test detekt`（先确认测试源存在；NO-SOURCE 不代表回归通过） |
| CLI 包 | `./gradlew.bat cliJar` |
| Android 编译 | `./gradlew.bat :androidApp:assembleDebug`（需要 SDK/JDK、Rust/NDK/CMake/libclang 或 `-PquicJniLibsDir=<预构建目录>`；见 `native/README.md`） |

安装包、签名与发布另见 [packaging/README.md](packaging/README.md) 和工作流，不以发版任务替代 PR 检查。Wrapper 缺失时报告阻断并确认已有删除意图，不自动还原用户删除或下载不明版本。

橙色纸感 UI 的离线 Compose 预览见 [设计与预览说明](docs/design/ORANGE-PAPER.md)；使用虚构数据，不启动应用连接。

Windows 遇到 `Unable to establish loopback connection` / `UnixDomainSockets.connect0` 时，使用 `scripts/gradle-windows.ps1` 将 Java socket 放在工作区内；不要只改 daemon 参数或全局网络策略。诊断与已安装 Gradle 的选择方式见 [Windows 构建](docs/development/WINDOWS-GRADLE.md)。

## 跨端修改评估

- 会话身份统一为类型与原始 ID；原生用 `ConversationKey`，Web 用 `ConversationId`。禁止恢复负群 ID 分桶或仅用数字 ID 合并混合联系人，边界见 [会话标识](docs/development/CONVERSATION-IDENTITY.md)。
- 修改 Web 前端或原生客户端（桌面、CLI、Android）时，必须检查另一端的对应实现，判断是否需要同步；双向执行，不以本端编译通过代替评估。
- 重点核对交互与视觉规范、消息状态、联系人与历史同步、本地缓存与草稿、账号隔离、权限和接口/协议语义。共同业务行为与缺陷修复应在任务范围内同步；布局、平台能力和运行时实现可保留合理差异，说明理由，不机械照搬。
- 交付时说明受影响端的结论：已同步（含实际验证）、不适用（理由）或待同步（原因与影响）。用户明确限制修改范围时，记录另一端待办，不擅自扩大改造范围。

## 修改约定

- 原生 Agent 回调是累计全文；历史保存只提交脏消息，禁止恢复全会话正文去重映射。摘要、缓存预算、前后台轮询及跨端验证见 [第二轮性能改进](docs/development/CLIENT-PERFORMANCE-SECOND-PASS.md)。
- 本地历史批量读取复用文件句柄，时间范围按索引定位，跨会话候选有界；Web 删除使用主键游标并保持消息/摘要原子清理，见 [历史存储优化](docs/development/HISTORY-STORAGE-PERFORMANCE.md)。
- 列表与图片性能边界见 [跨端性能改进](docs/development/CLIENT-PERFORMANCE-2026-10-28.md)：群消息入口接收正群ID；头像尺寸与对象键共同决定缓存键，平台实现不能重复保留一份解码内存缓存。
- UI 展示与交互留在组件；业务协调放 ViewModel，数据访问放 Repository。对现有大文件逐步提取，避免一次迁移整个架构。
- 平台实现留在对应目录；共享源码不得新增仅桌面可用的依赖。新增抽象前检查现有平台的实际编译方式。
- 协程明确所有者和取消时机；停止连接时清理心跳、监听器、回调、stream 与执行器。不得吞掉 `CancellationException`。
- `core/ChatTransport.kt` 的全局 `Chat` 由平台入口初始化；修改初始化或 DI 时同时核对桌面、CLI、Android。
- 协议真相在 `../chat-room/src/main/proto/`。本仓库 `:proto` 实际从 `src/main/proto/` 生成，根 `proto/*.proto` 只是副本；不能只改那个副本。不得直接编辑 `build/generated/`。
- Kotlin 消息类型复用生成枚举（`MsgType` 为类型别名）；不新增平行字符串/数字路由表。共享分发只做一次包级去重，再通知全部监听器；解帧器按流持有，不能按 read 回调划消息边界。
- 检查 messageId/seqId、ACK、取消、离线重试、账号切换和 Agent 流式结束/错误事件。发送写入回调不等于业务成功。
- 生产连接必须验证证书和服务端身份；开发证书例外应由显式环境开关约束，不能默认关闭校验。
- 本地联调显式设置 REST/QUIC 地址；`ServerConfig`、`ApiEndpoints` 和 CLI 有线上默认值。不要使用真实用户数据做测试。

新设备七天历史恢复与本地落库约定见 [历史恢复](docs/development/RECENT-HISTORY.md)；不要以离线消息接口或服务端提示直接推进本机同步游标。

Agent 结构化卡片的后续设计见 [交互卡片方案](docs/design/AGENT-CARDS.md)，该文档是提案，不代表协议、操作接口或 UI 已实现。

## 已知基线

社交发现与资料卡：`component/social/SocialScreen.kt` → `presentation/viewmodel/SocialViewModel.kt` → `data/repository/SocialRepository.kt`；Web 独立实现。入口、权限契约和验证见 [社交发现](docs/development/SOCIAL-DISCOVERY.md)。

2026-10-23 审查时当前工作区的两个 Wrapper 文件处于已删除状态，客户端测试目录未发现测试源码；Web 构建也有既存错误。详见 [审查快照](../docs/architecture/2026-10-23-现状审查.md)。每次任务重新验证，不把这个快照当成永久事实。
