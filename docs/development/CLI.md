# CLI 使用与本轮优化

## 启动与配置

CLI 入口是 `src/cliMain/kotlin/CliMain.kt`，命令解析、终端、配置和发送分别在同目录的 `cli/`。依赖 JDK 21；构建：

```text
./gradlew.bat cliJar
java -jar build/libs/chatlite-cli-<appVersion>.jar --help
java -jar build/libs/chatlite-cli-<appVersion>.jar --version
java -jar build/libs/chatlite-cli-<appVersion>.jar --server http://127.0.0.1:8089 --quic-host 127.0.0.1 --quic-port 8080 --no-auto-login
```

端口以本地测试环境为准。默认仍为项目现有线上地址，执行联调必须明确本地 REST 和 QUIC 两个目标。切换 REST 环境需同时给出 QUIC 主机和端口，防止沿用旧环境实时入口；集群运行时仍由共享 transport 查询 owner。REST 根地址不带 `/api`；非本机服务要求 HTTPS。错误参数、缺失值、非法端口、未知命令会明确报错。

`--help` 和 `--version` 在加载配置、DI、凭证或网络前返回，版本来自统一构建版本。`--username` 表示显式登录指定账号，失败不会自动切换到缓存账号。交互密码使用 JLine 掩码且不进入输入历史；移除了 `--password` 明文命令行参数，自动化可配合 `--username` 和 `--password-stdin` 从标准输入第一行读取 UTF-8 密码。读取不预取后续命令。不要把真实密码写进 shell 命令或文档。

CLI 配置在 `~/.qingliao/cli/config.properties`，只存服务器地址。凭证与联系人文件使用现有加密数据源，CLI 以规范服务器根地址 SHA-256 作为 `cli/servers/<hash>/` 子目录，历史再按账号隔离，不覆盖桌面端数据。不读取旧 CLI 全局配置、历史文件或桌面凭证；首次需重新指定环境并登录。每个服务器目录有进程文件锁，避免两个 CLI 同时追加同一历史日志。

标准输出、错误输出和 JLine 使用 UTF-8，避免现代 PowerShell/管道按 UTF-8 解码时出现中文乱码。旧版命令提示符需先切换 UTF-8 代码页。无法可靠掩码的 dumb 终端拒绝交互密码，必须使用 `--password-stdin`，不回退到明文回显。

## 交互

- 输入普通文本或 `/ai <内容>`：调用助手。HTTP JSON 使用后端 `AgentRequest.input`，按 `ApiResponse<String>` 解析，只输出回答正文。显式失败不会变成空字符串。
- `/msg <用户ID> <内容>`、`/group send <群ID> <内容>`：使用现有 QUIC Protobuf 协议，不再请求不存在的 HTTP 发送接口。ID 均为正数。
- `/friend list|add|accept|reject`、`/group list|join`：保留管理入口。入群申请待审核有独立提示；每次 REST 请求读取最新全局 token，不捕获启动时 token。
- `/history user <ID> [条数]`、`/history group <ID> [条数]`：按本地索引读取最新记录，默认 20、最多 100 条。消息正文不进入磁盘命令历史；进程内输入历史限制 100 条。
- `/status`：显示实际认证后的实时连接状态。`/help`：显示命令与参数。未知斜杠命令不会请求 AI，避免误操作和额度消耗。
- 输入时 Ctrl+C 清空当前行；Ctrl+D、`/exit`、`/quit` 退出。执行中的网络操作由超时管理，不承诺 Ctrl+C 等同于取消服务端处理。

异步消息通过 JLine `printAbove` 重绘提示符，避免覆盖正在输入的文字；正文截取上限 8192 字符，过滤终端控制字符，远端内容不能作为 ANSI/OSC 指令执行。不强制输出彩色控制码。命令异常只结束本次操作，取消异常继续传播。

## 发送、历史与生命周期

客户端先将待发消息写入同一服务器/账号本地日志，再发送一个具有稳定 messageId 的请求。4 秒内不突出状态；仍无确认才显示发送中；10 秒未确认提示结果不确定，不自动重新生成 ID 重发。成功保持安静。ACK 由 transport 匹配，接收确认与接收者送达、已读不同；超时可能只是 ACK 丢失，不表示服务端一定没有接收。迟到 ACK 仍由共享接收持久化处理，别名归并避免晚到的失败快照覆盖已确认状态。

实时接收和服务端 ACK 使用现有 `ReceivedMessagePersistence`；离线消息在展示前写入本地，再有限展示最近 20 条。`/history` 只查询本地，不声称这是完整七天历史。没有新增完整七天补拉、断点同步、CLI 持久 outbox 或跨重启自动重发；不能据此宣称离线恢复已与图形客户端完全等价。未确认发送需用户先核实服务端历史，再决定是否重发。

重连由 QUIC transport 唯一管理；CLI 只观察认证状态，不再重复定时 start，也不把 start() 返回当连接成功。退出时取消并等待会话任务、注销消息与鉴权监听器、登出/停止传输、关闭终端与 HTTP 客户端并释放目录锁，JVM 退出 hook 负责停止传输。认证失效后禁止继续业务操作，不回退到别的账号。

CLI JAR 不再包含 `allSource` 源码；仍包含当前 JVM 主工程运行时依赖，尚未单独裁剪 Compose/桌面依赖。不要把本次打包改进描述为独立轻量 CLI 模块。

## 跨端评估与验证

Web 和图形客户端已使用各自的实时发送和 ACK 实现，本轮 CLI 对齐这些契约，不改协议、后端投递与广播策略。共享 `agentChat` 只有 CLI 调用，Web/图形端助手通道不受影响；文件历史提取成可指定根目录的实现，桌面入口保留原目录与原行为，Android 使用自身存储实现。

自动化使用 fake transport、临时目录、内存终端和本机伪造 HTTP，不连接线上账户或付费模型。覆盖非法输入、未知命令、ID/正文解析、UTF-8 密码、掩码输入、终端控制字符过滤、配置与文件锁、服务器/账号隔离、待发持久化、ACK 等待、超时无自动重发、发送异常、取消及助手 HTTP 契约。完整真实 REST/QUIC 双账号收发、断网恢复和真实终端差异仍需在隔离环境联调。

2026-10-28 实际结果：`:compileKotlin :test :cliJar` 通过，134 项 JVM 测试全部通过（新增 CLI 测试 17 项）；`:androidApp:compileDebugKotlin -x buildQuicNative` 通过，只验证共享代码编译，没有生成 JNI/APK。打包后的 JAR 已验证帮助、版本、中文 UTF-8、未知参数退出码 2，且帮助/版本不初始化配置或认证；未运行真实登录与收发。Web 与后端本轮未改动、未重复运行其构建测试。
