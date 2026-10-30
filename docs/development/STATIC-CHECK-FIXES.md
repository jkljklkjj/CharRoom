# 静态检查修复（2026-10-30）

功能版本为 `c7ae4a5`，第一轮检查修复为 `c902bcf`。首次有效 Detekt 扫描发现 579 项；第一轮剩余 567 项，本轮全部清理，最终为 **0 项**。

## 检查范围与规则

Detekt 1.23.7 扫描 `src/commonMain/kotlin` 和 `src/desktopMain/kotlin`，包含尚未参与编译的桌面通话源码。Android 平台代码使用 Android Lint；CLI 另做编译、单测和无 GUI 依赖检查。

保留配置校验、`maxIssues: 0` 和既有复杂度、长度、参数数量阈值；未新增 baseline、排除目录或全局规则豁免。统一 Kotlin 格式、表达式函数、命名与包目录，并更新 CLI 的共享源码白名单。

`OperationRecovery.kt` 是 API、UI、回调的异常隔离边界，只有这里对 `TooGenericExceptionCaught` 作局部豁免：被调用方可以抛出不同种类的业务异常，需要统一执行原有降级逻辑。取消异常直接重抛，JVM `Error` 不进入降级；4 项测试验证成功、普通异常、取消和 JVM 错误。协议解码、反射等已知异常仍在对应位置捕获具体类型。

## 实现调整

| 位置 | 调整与行为边界 |
| --- | --- |
| `core/state/ChatState.kt` 及同目录组件 | 将联系人、游标、历史变更、会话预览和消息桶分别管理；保留公共状态流、共享消息锁、缓存预算和账号清理行为。 |
| `presentation/viewmodel/Chat*Actions.kt` | 从 ViewModel 提取联系人、消息、历史和发送协调；保留平台可覆盖入口、账号/会话代际检查、发送 ACK 与输入提交的区别。 |
| `core/HistoryJournalFormat.kt`、`IndexedHistoryJournal.kt`、`DurableCommitQueue.kt` | 拆开记录编解码、索引维护、队列准入与实际提交；不改 `.history` 格式、CRC、同步刷盘与错误传播语义。 |
| `core/ProtobufMessageDispatcher.kt`、`ReceivedMessagePersistence.kt`、`ProtobufResponseParser.kt` | 按消息类型和 Agent 事件拆分处理；持久化先于接收确认，保留去重及兼容解析次序。新增解析回归覆盖字段类型、嵌套工具结果、旧响应和 UTF-8 降级。 |
| `core/NativeQuicChatTransport.kt`、桌面 `Quic*.kt` | 拆分连接监督、握手、流初始化、消息路由、监听器和 TLS 身份匹配；保留重试、队列上限、ACK 超时和资源释放。 |
| `core/update/`、桌面图片与安装器 | 拆分断点下载、ETag、长度和哈希验证、图片缓存/解码、安装器校验与交接；测试不执行真实安装。 |
| `AuthRepository.kt`、`AuthRequests.kt` | 修复原先未使用的 `rememberMe`：勾选才保存凭证；不勾选清除旧凭证但保留当前会话。可注入请求用于离线测试，取消不能转成成功登录。 |
| Android `AndroidChatViewModel.kt` | 旧离线加载入口复用共享的批量补拉、持久化和确认流程。 |
| 共享组件、资料及发现 ViewModel | 提取条件和状态合并，聚合导航、头像槽与附件参数，清除无调用的包装和未使用参数；保持用户可见操作。 |

按声明类型重命名 Provider、平台实现及 `UpdateTarget.kt`；国际化文件移动到 `com/chatlite/i18n/`，包名不变；桌面流初始化文件现在是 `QuicStreamInitializer.kt`。协作入口已同步。

## 验证

JDK 21，Windows 使用仓库的 Java socket 目录配置；内存紧张时以 `--max-workers=1` 串行验证，避免测试 JVM 在启动阶段因系统提交内存耗尽退出。

```powershell
./scripts/gradle-windows.ps1 compileKotlin detekt :test :cli:test :cli:verifyHeadlessRuntime --offline --no-daemon '-Pkotlin.compiler.execution.strategy=in-process' --max-workers=1
./scripts/gradle-windows.ps1 :androidApp:compileDebugKotlin :androidApp:testDebugUnitTest :androidApp:lintDebug -x :androidApp:buildQuicNative --offline --no-daemon '-Pkotlin.compiler.execution.strategy=in-process' --max-workers=1
python scripts/check-test-results.py build/test-results/test cli/build/test-results/test androidApp/build/test-results/testDebugUnitTest
python scripts/check-protocol-copies.py
```

- JVM 编译、Detekt、161 项测试通过；CLI 28 项测试及无 GUI 依赖检查通过。
- Android Kotlin 编译、5 项单测和 Lint 通过：0 errors、119 warnings、12 hints。警告与提示仍保留，不把 Detekt 清零等同于所有 Android Lint 提示清零。
- 两套协议在后端与四处客户端副本间完全一致；未修改协议字段。
- Web `npm run typecheck` 通过。Web 使用独立 TypeScript 运行时，无需同步 Kotlin 内部拆分；Web 登录没有原生的“记住我”选项，本轮不改变其凭证策略。
- `git diff --check` 和 CI YAML 解析通过。CI 加入 Detekt 和 Android Lint，并保留其报告；CI 云端运行尚未触发。

测试使用虚构数据、临时目录和注入的下载/请求，不访问真实账号、线上消息或付费模型。跳过 Android JNI 构建；未验证 APK、真机、真实 QUIC 服务联调或安装交接。

运行后报告位于 `build/reports/detekt/`、`build/test-results/test/`、`cli/build/test-results/test/` 和 `androidApp/build/reports/lint-results-debug.*`；构建输出不提交。
