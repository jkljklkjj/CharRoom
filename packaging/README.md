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

`buildInstallers` 只依赖当前系统的发布任务：`packageCompactReleaseMsi`、`packageReleaseDmg` 或 `packageReleaseDeb`，经过 ProGuard 后打包。调试分发供本地排错，不能作为发布产物。

## 免安装压缩包

每个桌面平台同时发布安装器与完整应用镜像的压缩包，均包含裁剪后的 Java、当前平台原生库和应用依赖，无需另装 Java。Android 继续只发布 APK。

| 平台 | 安装器 | 免安装版本 | 解压后的启动入口 |
| --- | --- | --- | --- |
| Windows x64 | `chatlite.msi` | `chatlite-<版本>-windows-x64.zip` | `chatlite/chatlite.exe` |
| macOS 当前构建架构 | `chatlite.dmg` | `chatlite-<版本>-macos-arm64.zip` 或 `macos-x64.zip` | `chatlite.app`，可移至 Applications |
| Linux x64 | `chatlite.deb` | `chatlite-<版本>-linux-x64.tar.gz` | `chatlite/bin/chatlite` |

`packageDesktopArchive` 只构建当前系统/架构的发布应用镜像与压缩包，可单独运行；`stageDesktopReleaseArtifacts` 同时收集安装器和该压缩包。压缩包源自 `createReleaseDistributable` 的 `main-release/app`，不使用调试镜像、不把安装器再套进压缩包。压缩文件位于 `build/compose/binaries/main-release/archive/`，最终收集至 `build/release-artifacts/desktop/`。固定版本与架构名避免跨平台发布时同名覆盖；Sync 只收集当前精确文件名并清除收集目录里的旧文件。

Windows 使用 Gradle ZIP；macOS 使用系统 `ditto -c -k --sequesterRsrc --keepParent` 保留 `.app` 的资源和链接；Linux 使用系统 `tar -czf` 保留执行权限和符号链接。参见 [Apple ZIP 与公证指南](https://developer.apple.com/documentation/security/customizing-the-notarization-workflow)、[GNU tar 符号链接说明](https://www.gnu.org/software/tar/manual/html_node/dereference.html)。请完整解压，不从压缩软件中单独打开启动器；Linux 仍需要宿主图形环境及兼容的系统库，不能跨系统/架构运行。

“免安装”只描述分发方式，账号、聊天数据和缓存继续写入现有用户数据目录，不切换为应用目录存储，也不增加压缩包自动覆盖更新机制。Windows 现有签名仍只针对 MSI；不能据此宣称 ZIP 内的 EXE 已签名。macOS 配齐现有签名与公证凭据后，CI 单独签名 ZIP 的 `.app`、提交 ZIP 公证、将票据 staple 到 `.app` 再重新生成 ZIP；缺少凭据时产物未公证，系统可能提示 Gatekeeper 校验。不要在签名/公证后重新执行 Gradle 收集任务覆盖已处理文件。

CI 上传和 GitHub Release 已包含 ZIP、tar.gz，Android 上传规则不变。macOS/Linux 产物必须由对应系统构建，Windows 本地验证不能替代这两端回归。

2026-10-29 Windows 验证：`stageDesktopReleaseArtifacts`、`desktopSizeReport` 通过；ZIP 为 66,541,680 字节（63.46 MiB），MSI 为 59,218,529 字节（56.48 MiB）。ZIP CRC 校验通过，258 个文件与发布镜像逐个 SHA-256 相同，确认包含启动器、Java 模块和 Skiko 原生库。ZIP 是增加免安装选择，当前并不比 MSI 小。工作流 YAML、三个桌面收集入口及 ZIP/tar.gz Release 匹配规则检查通过，Android 工作流没有改动。未在 macOS/Linux 实际构建、解压启动或使用真实证书签名/公证，也未执行 Windows 安装和启动联网测试；CLI、Android、Web 没有业务变更，本轮不运行这些端测试。

## MSI 压缩

Windows 正式发行仍为内置 Java 的 MSI。`packageCompactReleaseMsi` 从 `createReleaseDistributable` 的应用镜像打包，沿用标准 MSI 的版本、安装位置、菜单和升级标识，通过 jpackage 官方 `main.wxs` 资源覆盖将 CAB 压缩设为 `high`。模板从当前 JDK 提取，只修改压缩属性；模板结构变化时构建失败，要求人工检查。Compose 1.10.3 会清理其内部资源目录，因此独立任务把资源覆盖参数追加到插件参数末尾，资源生成在独立目录中。原 `packageReleaseMsi` 保留为默认压缩的排查入口。

2026-10-29 对同一份现有发布镜像做 MSI 对比：默认压缩 67,918,433 字节（64.8 MiB），高压缩 59,218,480 字节（56.5 MiB），缩小约 12.8%。这是压缩方式对照，不代表最新源码产物或已经达到 50 MB；不将 7z 比较结果作为 MSI 大小。应用文件和 Java 模块不因该设置减少，安装后的占用也不会同比减少。压缩可能增加构建时间。尚未执行安装、卸载及升级回归。

同日最新源码的 `stageDesktopReleaseArtifacts`、`desktopSizeReport` 构建通过，正式路径生成的 MSI 为 59,218,529 字节（56.5 MiB，约 59.2 MB），内含应用及 Java。7-Zip 仅用于 MSI 完整性检查（258 个文件，通过），未生成 7z 发行包；收集副本与原 MSI 的 SHA-256 一致。构建仍出现上游 ProGuard Kotlin metadata 警告。此次只改桌面打包，CLI、Android、Web 无业务同步项；没有运行这些端的构建或业务测试。

- `createReleaseDistributable`：仅生成发布版应用镜像，便于先检查启动和原生库加载。
- `verifyDesktopRuntime`：拒绝外平台的 Skiko/QUIC、CLI JLine 和未启用的桌面通话依赖，并要求两类原生库各只有一份。
- `desktopSizeReport`：输出依赖大小和已有镜像/安装器大小到 `build/reports/packaging/desktop-size.txt`；只测量，不隐式构建安装包。
- `stageDesktopReleaseArtifacts`：从 `build/compose/binaries/main-release/<格式>/` 收集当前发布安装器，并从 `archive/` 收集当前版本/架构的压缩包，同步至 `build/release-artifacts/desktop/`。安装器不存在或发现多个版本时报错，应先清理旧安装器；压缩包缺失也报错。不会扫描调试目录并静默覆盖同名文件。目标目录为任务专用，Sync 会移除过期文件。
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

## Android APK 优化

Android APK 由 Android 系统运行应用代码，不包含桌面包的 Java 运行时。Release 已启用 R8、资源裁剪和 `proguard-android-optimize.txt`；AGP 8.13.2 额外开启 `android.r8.optimizedResourceShrinking=true`，联动代码与资源可达性分析，见 [Android 官方说明](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)。没有升级依赖或 AGP。

2026-10-29 将 `androidx.compose.material.icons` 从整个 Compose 包的保留规则中排除，并移除单独的全图标保留规则。实际静态引用的图标仍由 R8 可达性分析保留，其他 Compose、JNI、Jackson、Protobuf 和网络库的保留策略不变。不能写成只有 `!androidx.compose.material.icons.**` 的否定规则，否则会保留应用中其他所有类。删除了 JUnit 通配符 `assumenosideeffects`，避免把继承的 `Object` 方法误判为无副作用；测试库继续使用 `testImplementation`。

移除 Release 强制 `noCompress png/jpg/jpeg`，交由 AGP 默认资源打包策略处理。JNI 库仍按原策略保持可直接加载与 16 KiB 对齐，不为了压缩下载体积而改为安装时解压。默认 ABI 仍为 arm64-v8a、x86_64；没有减少设备支持或修改版本号、应用 ID、签名策略。原 Release 配置的“只保留 arm64”注释与实测双 ABI 结果不符，已删除冗余过滤配置并同步 [原生构建说明](../native/README.md)。

Windows 可用 `scripts/gradle-windows.ps1` 执行 `:androidApp:assembleRelease :androidApp:testReleaseUnitTest`；需要原生工具链，或按原生构建说明提供 `-PquicJniLibsDir=<完整预构建目录>`。本机对比使用同一套预构建 JNI 文件。低内存环境可临时限制 `--max-workers=2` 和 JVM 可见处理器数，不将本机内存限制写成全局构建默认值。

Android CI 同时运行 Release 单元测试，另上传 `android-r8-mapping` artifact 保存 `mapping.txt` 供混淆堆栈还原；它与对应 APK 必须配套保管，不作为 APK 内部资源或 GitHub Release 安装包上传。桌面、CLI、Web 没有对应运行时代码或协议变化需要同步。

同一份当前源码、相同 JNI 预构建库的前后对比：

| 指标 | 优化前 | 优化后 |
| --- | --- | --- |
| APK | 23,411,946 字节（22.33 MiB） | 20,151,850 字节（19.22 MiB），减少 13.92% |
| 压缩 DEX 合计 | 11,257,544 字节 | 8,003,578 字节 |
| R8 mapping 中保留的图标类 | 11,411 | 29 |
| ABI | arm64-v8a、x86_64 | 不变，四个 JNI 库的 SHA-256 全部相同 |

恢复默认压缩策略后，本次 PNG 合计仍为 73,717 字节，不能将其计入压缩收益；没有分别测量联合资源裁剪的独立收益。`assembleRelease`、5 项 `testReleaseUnitTest`、APK ZIP CRC、APK v2 签名校验和 `zipalign -c -P 16 4` 均通过；R8 seeds 中 JNI 入口仍保留。构建沿用现有本地签名策略，签名验证不代表使用了正式发布证书。

首次基线构建因本机 native 内存分配失败退出，限制并发后重试通过；优化后构建通过。仍有 Jackson `java.beans` 可选类缺失警告及既有 Kotlin/Gradle 警告，未将其作为已解决事项。未运行真机/模拟器的图标显示、启动、登录、聊天、前后台、安装/升级回归；没有使用真实账号或线上服务。桌面/CLI/Web 本轮未重新构建，后端未修改。

## CI、签名与发布

工作流构建并收集发布包，签名/公证直接操作 `build/release-artifacts/desktop` 中最终上传的文件，避免上传签名前的旧副本；签名后不再执行依赖安装器构建的收集任务，防止 Gradle 因输出变化重建并覆盖签名。桌面报告作为独立 artifact 上传；MSI/DMG/DEB 已压缩，artifact 使用 `compression-level: 0` 避免重复压缩耗时，缺失文件直接失败。此设置优化 CI 上传时间，不改变安装器本身的压缩方式。

签名与公证要求见 [signing.md](signing.md)。现有 macOS 可选签名流程尚需在 macOS 验证，不以 Windows 构建证明其他系统可发布。修改 Gradle 不会自动创建标签、签名证书或发布 Release。

## 跨端影响

此次只调整桌面依赖和桌面发布路径。CLI 保持独立依赖与构建，Android 仍由 `androidApp` 的依赖和 R8 配置负责，Web 使用 Vite；没有共同业务或视觉变化需要移植到其他端。源集已经排除的桌面通话功能没有因本轮裁剪额外减少。

## 本轮验证记录

2026-10-28：Windows `stageDesktopReleaseArtifacts`、`createReleaseDistributable`、`customJar`、`verifyDesktopRuntime`、`desktopSizeReport` 均实际构建/执行通过。最终 MSI 为 67,918,433 字节（64.8 MiB），未使用证书签名。扩展图标发布 JAR 为 30,301 字节（29.6 KiB）；这与依赖原 JAR 的 36 MiB 是裁剪前后两个阶段，不能重复计入依赖裁剪收益。

JVM 120 项和 CLI 26 项测试通过，CLI 无 GUI 依赖检查通过。对混淆后的发布 JAR 按随包 Java 模块集限制加载，验证了 Swing、Jackson JSON、QUIC 原生库、默认 TLS 信任库/引擎和 Skiko 原生库；没有联网。便携 JAR 检查入口类存在、没有 Kotlin/Java 源码。工作流 YAML 与单次 Gradle 构建、最终副本签名顺序已做结构检查。

ProGuard 构建仍有上游库 Kotlin metadata 警告；构建与上述加载检查通过，但不代表所有反射路径均已验证。未执行 Windows MSI 安装/卸载及真实登录聊天回归，macOS/Linux 安装器、签名/公证和 Android/Web 构建本轮未运行。桌面改动不依赖后端部署，也没有改动 CLI、Android、Web 的业务契约。
