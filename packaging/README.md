# 桌面安装包构建与体积管理

桌面使用 Compose Desktop + JVM，安装包包含应用依赖、当前平台的 Skiko/QUIC 原生库和 jlink 裁剪后的 Java 运行时。用户无需安装 Java；安装包大小和安装后占用空间是两项不同指标。

## 构建

使用完整 JDK 21（含 `jmods`、`jlink`、`jpackage`）。Windows MSI 需要 WiX；Compose 插件可下载 WiX，本地离线环境需提前准备插件缓存。macOS DMG 和 Linux DEB 分别在对应系统构建，当前 QUIC 支持 Windows/Linux x64 和 macOS x64/arm64；其他组合明确报错，不混装其他架构的库。

```powershell
# Windows，工作目录 CharRoom
./scripts/gradle-windows.ps1 -GradleArguments @('stageDesktopReleaseArtifacts', 'desktopSizeReport')
```

```bash
# macOS / Linux
./gradlew stageDesktopReleaseArtifacts desktopSizeReport
```

`buildInstallers` 只依赖当前系统的发布任务：`packageReleaseMsi`、`packageReleaseDmg` 或 `packageReleaseDeb`，经过 ProGuard 后打包。调试分发供本地排错，不能作为发布产物。

- `createReleaseDistributable`：仅生成发布版应用镜像，便于先检查启动和原生库加载。
- `verifyDesktopRuntime`：拒绝外平台的 Skiko/QUIC、CLI JLine 和未启用的桌面通话依赖，并要求两类原生库各只有一份。
- `desktopSizeReport`：输出依赖大小和已有镜像/安装器大小到 `build/reports/packaging/desktop-size.txt`；只测量，不隐式构建安装包。
- `stageDesktopReleaseArtifacts`：仅从 `build/compose/binaries/main-release/<格式>/` 收集当前发布安装器，同步至 `build/release-artifacts/desktop/chatlite.<格式>`。不存在或发现多个版本时报错，应先清理旧安装器；不会扫描调试目录并静默覆盖同名文件。目标目录为任务专用，Sync 会移除过期文件。
- `customJar`：保留便携 fat JAR 入口，只收集编译输出与依赖，不再包含 Kotlin/Java 源码；需要用户自行安装 Java。正式桌面发布优先使用安装器。

## 本轮裁剪

2026-10-28 Windows x64 测量：裁剪前运行依赖共 111,737,398 字节（106.6 MiB），清除无用依赖后 89,949,964 字节（85.8 MiB），减少约 20.8 MiB。这是压缩 JAR 的合计，不代表最终 MSI 或安装目录大小。

主要问题包括：

- 所有平台的 QUIC 原生库都被引入，现按宿主平台选择唯一 runtimeOnly 依赖。
- 富文本的传递依赖引入额外 Linux Skiko；现排除非目标平台运行库，并在打包前检查。
- 桌面源集已排除 `call`，仍保留 WebRTC；JLine 已迁入独立 CLI 模块，JBoss Marshalling 也无桌面源码引用，`kotlin-test` 原先因依赖作用域写错而进入运行时。现删除根项目中的这几项依赖，将测试库改为 `testImplementation`，保留 Android 自身配置和 CLI 依赖。
- 完整扩展图标库约 36 MiB，原规则保留整个 Compose 包，阻止未使用图标被裁剪。现仅将图标包排除出全量保留规则，实际引用的图标仍由 ProGuard 可达性分析保留；其他框架反射规则不作激进修改。
- 安装器聚合任务曾匹配所有 package/jpackage 任务，且收集时扫描整个输出树；现固定当前系统的发布任务与目录。

运行时继续由 Compose 插件执行 jlink 裁剪，不捆绑完整 JDK。`java.desktop` 是 Swing/AWT 必需模块，不能为减小体积而删除；显式保留 `jdk.crypto.ec` 支持证书加密。新增依赖后可运行 `suggestRuntimeModules` 辅助检查，但静态推断不能替代安装后回归。参见 [Compose 官方打包说明](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html)。

## CI、签名与发布

工作流构建并收集发布包，签名/公证直接操作 `build/release-artifacts/desktop` 中最终上传的文件，避免上传签名前的旧副本；签名后不再执行依赖安装器构建的收集任务，防止 Gradle 因输出变化重建并覆盖签名。桌面报告作为独立 artifact 上传；MSI/DMG/DEB 已压缩，artifact 使用 `compression-level: 0` 避免重复压缩耗时，缺失文件直接失败。此设置优化 CI 上传时间，不改变安装器本身的压缩方式。

签名与公证要求见 [signing.md](signing.md)。现有 macOS 可选签名流程尚需在 macOS 验证，不以 Windows 构建证明其他系统可发布。修改 Gradle 不会自动创建标签、签名证书或发布 Release。

## 跨端影响

此次只调整桌面依赖和桌面发布路径。CLI 保持独立依赖与构建，Android 仍由 `androidApp` 的依赖和 R8 配置负责，Web 使用 Vite；没有共同业务或视觉变化需要移植到其他端。源集已经排除的桌面通话功能没有因本轮裁剪额外减少。

## 本轮验证记录

2026-10-28：Windows `stageDesktopReleaseArtifacts`、`createReleaseDistributable`、`customJar`、`verifyDesktopRuntime`、`desktopSizeReport` 均实际构建/执行通过。最终 MSI 为 67,918,433 字节（64.8 MiB），未使用证书签名。扩展图标发布 JAR 为 30,301 字节（29.6 KiB）；这与依赖原 JAR 的 36 MiB 是裁剪前后两个阶段，不能重复计入依赖裁剪收益。

JVM 120 项和 CLI 26 项测试通过，CLI 无 GUI 依赖检查通过。对混淆后的发布 JAR 按随包 Java 模块集限制加载，验证了 Swing、Jackson JSON、QUIC 原生库、默认 TLS 信任库/引擎和 Skiko 原生库；没有联网。便携 JAR 检查入口类存在、没有 Kotlin/Java 源码。工作流 YAML 与单次 Gradle 构建、最终副本签名顺序已做结构检查。

ProGuard 构建仍有上游库 Kotlin metadata 警告；构建与上述加载检查通过，但不代表所有反射路径均已验证。未执行 Windows MSI 安装/卸载及真实登录聊天回归，macOS/Linux 安装器、签名/公证和 Android/Web 构建本轮未运行。桌面改动不依赖后端部署，也没有改动 CLI、Android、Web 的业务契约。
