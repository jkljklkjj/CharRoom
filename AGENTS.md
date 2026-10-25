# CharRoom 客户端协作约定

先执行 `git status --short`，保留已有修改。后端是相邻独立仓库 `../chat-room/`。修改 Web 时另读 [web/AGENTS.md](web/AGENTS.md)。

## 按实际构建理解项目

- 根构建应用的是 Kotlin **JVM** 插件，不是 `kotlin("multiplatform")`。`commonMain` 是共享源码目录命名，不能据此假定 JS/iOS target 或 expect/actual 源集已经存在。
- [build.gradle.kts](build.gradle.kts) 把 `src/commonMain/kotlin`、`src/desktopMain/kotlin`、`src/cliMain/kotlin` 加入同一 JVM main；目前排除 `**/*.android.kt`、`**/call/**`。
- Android 模块通过 [androidApp/build.gradle](androidApp/build.gradle) 加入共享目录及 `src/androidMain/kotlin`；自身代码在 `androidApp/src/main/kotlin/com/chatlite/charroom/`。
- [settings.gradle.kts](settings.gradle.kts) 仅在任务名称包含 `android` 时 include `androidApp`。需要 Android 时使用 `:androidApp:...`，不能假定 `customApkDebug` 会启用模块。
- 版本看构建文件与 [gradle.properties](gradle.properties)：JDK 21、Kotlin 2.3.20；不要把后端 JDK 25 直接套到客户端。
- Vue Web 是独立 TypeScript 应用，协议与功能需要单独同步。

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

## 修改约定

- UI 展示与交互留在组件；业务协调放 ViewModel，数据访问放 Repository。对现有大文件逐步提取，避免一次迁移整个架构。
- 平台实现留在对应目录；共享源码不得新增仅桌面可用的依赖。新增抽象前检查现有平台的实际编译方式。
- 协程明确所有者和取消时机；停止连接时清理心跳、监听器、回调、stream 与执行器。不得吞掉 `CancellationException`。
- `core/ChatTransport.kt` 的全局 `Chat` 由平台入口初始化；修改初始化或 DI 时同时核对桌面、CLI、Android。
- 协议真相在 `../chat-room/src/main/proto/`。本仓库 `:proto` 实际从 `src/main/proto/` 生成，根 `proto/*.proto` 只是副本；不能只改那个副本。不得直接编辑 `build/generated/`。
- Kotlin 消息类型复用生成枚举（`MsgType` 为类型别名）；不新增平行字符串/数字路由表。共享分发只做一次包级去重，再通知全部监听器；解帧器按流持有，不能按 read 回调划消息边界。
- 检查 messageId/seqId、ACK、取消、离线重试、账号切换和 Agent 流式结束/错误事件。发送写入回调不等于业务成功。
- 生产连接必须验证证书和服务端身份；开发证书例外应由显式环境开关约束，不能默认关闭校验。
- 本地联调显式设置 REST/QUIC 地址；`ServerConfig`、`ApiEndpoints` 和 CLI 有线上默认值。不要使用真实用户数据做测试。

## 已知基线

社交发现与资料卡：`component/social/SocialScreen.kt` → `presentation/viewmodel/SocialViewModel.kt` → `data/repository/SocialRepository.kt`；Web 独立实现。入口、权限契约和验证见 [社交发现](docs/development/SOCIAL-DISCOVERY.md)。

2026-10-23 审查时当前工作区的两个 Wrapper 文件处于已删除状态，客户端测试目录未发现测试源码；Web 构建也有既存错误。详见 [审查快照](../docs/architecture/2026-10-23-现状审查.md)。每次任务重新验证，不把这个快照当成永久事实。
