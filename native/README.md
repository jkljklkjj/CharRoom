# Android 原生 QUIC

Android 入口是 `QuicheChatClient`，使用 `NativeQuicChatTransport` → `QuicheDriver` → JNI → Cloudflare quiche。后端继续使用现有 UDP QUIC 服务；浏览器继续使用 WebTransport。

## 协议与生命周期

- TLS 1.3 / QUIC v1，ALPN `custom`；Android 默认 TrustManager 的公开根证书导出到应用私有目录，BoringSSL 校验证书链和连接主机名。没有跳过校验的生产开关。
- stream 0 发送 protobuf LOGIN（MOBILE、稳定 deviceId）。握手成功后仍处于未登录状态，正向登录响应中的用户 ID 必须与 REST 身份一致。
- 会话使用客户端双向流 4、8、12……；首帧为 CHECK，携带会话类型及目标 ID。收到成功 STREAM_INIT_ACK 后发送聊天包；私聊与群聊按“类型 + ID”区分。
- 所有流均为 `[4 字节大端长度][protobuf]`，每流独立增量解码。复用现有 builder、分发器与 ACK 匹配器。
- 发送 payload 限制为 64 KiB（含 protobuf 元数据），对应后端当前 `safeParseProtobuf` 的实际限制；超限立即回调失败。后端拆帧器虽然允许 1 MiB，不能据此发送超过解析上限的包。附件应上传文件后发送 URL。
- 只有匹配且未被拒绝的业务 ACK 才报告聊天发送成功。握手、登录、初始化、心跳、ACK 各有超时；关闭连接时待发与待 ACK 消息各失败一次。
- 每个连接由专用后台线程持有；JNI 不保存 UI/Java 回调，poll 最多等待 50 ms。停止、账号切换释放驱动；网络故障退避重连并重新登录，不自动重放未确认的聊天包。
- Java 待确认数据与 native 待写数据分别限制为 4 MiB。quiche 的部分写入和 UDP 背压保留到下轮轮询。0-RTT 未启用。

当前网络切换通过断线重连恢复，没有实现跨网络主动连接迁移。离线消息补拉与 UI 三态是上层独立功能。

## 构建

依赖固定在 `quiche-jni/Cargo.toml`、`Cargo.lock`、`rust-toolchain.toml`。需要 Rust 1.90.0（包括 rustfmt）、Python 3、CMake、主机 C/C++ 编译工具及 **libclang 共享库和头文件**，以及 Android NDK r27d（27.3.13750724）。必要时设置 `LIBCLANG_PATH` 指向已安装 LLVM 的库目录；NDK 的编译器本身不能替代主机 libclang。boring-sys 生成绑定时需要 rustfmt，缺少它会在 x86_64 Android 上产生错误的匿名枚举别名。

```sh
rustup toolchain install 1.90.0 --profile minimal --component rustfmt --target aarch64-linux-android --target x86_64-linux-android
python scripts/build-android-quic.py --ndk "$ANDROID_NDK_HOME" --output androidApp/build/generated/quic/jniLibs
./gradlew :androidApp:assembleDebug
```

Gradle 的 `buildQuicNative` 在 preBuild 前调用同一脚本。可用 `-PquicNdkDir=<NDK目录>`、`-PquicPython=<Python命令>` 指定工具；脚本不安装工具、不下载 SDK。输出 arm64-v8a 和 x86_64；release APK 只保留 arm64-v8a。同时打包 NDK 的 `libc++_shared.so`，不能只复制 JNI 主库。最终链接设置 16 KiB 页对齐，适配新 Android 设备。

也可以在 Linux CI 生成，再在另一台机器打包：

```sh
./gradlew :androidApp:assembleDebug -PquicJniLibsDir=/absolute/path/to/jniLibs
```

预构建目录必须包含两个 ABI 各自的 `libchatlite_quic.so` 和 `libc++_shared.so`。不提交 `.so`、NDK 或 target 构建目录。用户工作区中的 Wrapper 删除状态仍由用户管理；Wrapper 不可用时可用已安装的匹配 Gradle 执行上述任务。

## 验证

共享会话测试包含在 `scripts/test-message-protocol.ps1`。可加 `-JniProbeOutputDirectory <目录>`，构建使用真实 Kotlin/JNI 适配器的探针；不包含 Android UI、REST 或真实账号。

Linux 上先在 `native/quiche-jni` 执行：

```sh
cargo test --locked --lib
cargo build --locked --lib --bin quic-probe
```

`native/tests/run-interop.py` 使用后端已编译的测试 classpath，启动独立临时端口的真实 QuicServer/QuicStreamRouter/QuicControlHandler/QuicChatStreamHandler。鉴权与消息投递为测试替身，不访问 Redis、数据库或线上账号；退出时关闭自建进程并删除临时证书。

```sh
python native/tests/run-interop.py \
  --backend-report /path/to/chat-room/target/surefire-reports/TEST-example.xml \
  --probe-bundle /path/to/probe \
  --native-directory /path/to/quiche-jni/target/debug
```

需要后端对应的 JDK 25（preview）及 Mockito 测试依赖。探针验证两身份登录、会话初始化、匹配 ACK 与双向单聊；额外验证错误主机名和不可信证书被拒绝。它不是业务持久化集成测试，也不能代替 Android 安装、前后台与断网重连的实机回归。

### 2026-10-24 实际结果

| 验证 | 结果与边界 |
| --- | --- |
| 共享 JVM 协议/会话 | 31 项通过；Kotlin 2.0.20 / Java 21 的隔离测试，ActionLogger 使用替身 |
| Rust 驱动 | 2 项通过，Linux 主机 JNI 共享库与握手探针构建通过 |
| Android 原生库 | arm64-v8a / x86_64 release 交叉编译通过；JNI 主库与 C++ 运行时均为 16 KiB ELF 页对齐 |
| JNI 协议互通 | 两临时身份双向 48 KiB 单聊、业务 ACK、主机名与 CA 校验通过；使用真实后端 QUIC 入口与合成业务处理 |
| Android API 适配器 | 使用 SDK API 36 独立编译通过；全局应用状态使用替身，未包含 Compose 应用 |
| 完整 APK | 未通过：缓存 Gradle 在任务执行前报 `Unable to establish loopback connection`；保留原有 Wrapper 删除状态 |
| 真机/模拟器 | 未运行；没有连接的 Android 设备，未发布安装包或改动线上服务 |
