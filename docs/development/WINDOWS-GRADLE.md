# Windows Gradle 回环连接错误

## 2026-10-24 定位结果

JDK 21 在创建 NIO Selector 时，会先用 Unix 域套接字建立内部唤醒通道。当前 Windows 启动环境允许默认 AppData/Temp 目录中的 socket bind，但随后 connect 报 `Invalid argument: connect`，于是 Gradle 在任务执行前抛出 `Unable to establish loopback connection`。

对同一个 JDK 做了独立复现：

| 检查 | 默认临时目录 | 工作区内的 socket 目录 |
| --- | --- | --- |
| TCP 127.0.0.1 回环 | 通过 | 无需切换 |
| Unix 域套接字 | bind 后 connect 失败 | 通过 |
| `Selector.open()` | 失败 | 通过 |
| Gradle 客户端与 daemon 建立通信 | 失败 | 通过，进入 settings 与依赖解析 |

修复后使用项目启动脚本执行 `:androidApp:compileDebugKotlin` 和 `:androidApp:assembleDebug`，完整 Android 编译及 Debug 打包通过（Gradle 8.14.5 / JDK 21）。APK v2 签名、16 KiB ZIP 对齐及两个 ABI 的 JNI 库内容检查通过。同时修复了预构建 JNI 目录被错误声明为输出的问题，两个 ABI 的四个输入库在构建后保持完整。

把默认短路径换成长路径的 AppData/Temp 仍失败，因此不是用户名空格或路径长度的简单问题。已定位到默认目录下的本地套接字行为；尚未进一步证明是 Windows 文件重定向还是具体宿主环境引起，不据此修改系统网络策略。

相关实现：[OpenJDK Windows PipeImpl](https://github.com/openjdk/jdk21u/blob/master/src/java.base/windows/classes/sun/nio/ch/PipeImpl.java)、[UnixDomainSocketsUtil 的临时目录选择](https://github.com/openjdk/jdk21u/blob/master/src/java.base/windows/classes/sun/nio/ch/UnixDomainSocketsUtil.java)。这属于本机 Java 构建进程的问题，与远端 QUIC 握手无关。

## 启动方式

使用项目中的 Windows 启动脚本：

```powershell
./scripts/gradle-windows.ps1 :androidApp:assembleDebug --no-daemon
```

脚本依次选择项目 Wrapper、PATH 中的 Gradle，或本机唯一已缓存的 Gradle 发行版，不下载或恢复 Wrapper。缓存存在多个版本时，显式选择匹配版本：

```powershell
./scripts/gradle-windows.ps1 -GradleExecutable '<Gradle目录>/bin/gradle.bat' :androidApp:compileDebugKotlin --no-daemon
```

原生库已预构建的机器可以继续传入 `-PquicJniLibsDir=<目录>`，见 [原生 QUIC](../../native/README.md)。这只改变 native 构建来源，不影响 Java 回环修复。

脚本将 `jdk.net.unixdomain.tmpdir` 指向项目的 `.gradle/java-sockets`，先运行独立的 Java NIO/HttpClient 初始化检查，然后执行 Gradle。通过进程级 `JAVA_TOOL_OPTIONS` 使设置同时作用于客户端和派生 JVM，结束时恢复调用环境。[Gradle 官方说明](https://docs.gradle.org/current/userguide/config_gradle.html) 区分了客户端与构建 JVM 的参数，单独修改 `org.gradle.jvmargs` 不能覆盖两者。

不需要更改 JDK 全局配置、防火墙、代理或 Windows 安全策略，也不恢复用户删除的 Wrapper。项目位于 AppData 或路径过长时，可用 `-SocketDirectory '<本地短目录>'` 指定其他位置。

初始化、工程编译、Debug 打包已分别验证。APK 安装及真机收发、release/R8 未运行，不能从本地 Debug 构建推断这些结果。
