# CLI、离线确认与独立构建

## 构建与运行

CLI 是独立的 `:cli` Gradle 模块，入口仍在 `src/cliMain/kotlin/CliMain.kt`。`cli/build.gradle.kts` 用明确的共享源码清单复用模型、HTTP、QUIC、ACK 与历史日志，不依赖根项目的 GUI JAR，不引入第二份协议实现。根项目不再编译 CLI 入口。

```text
./gradlew.bat :cli:test :cli:verifyHeadlessRuntime cliJar
java -jar build/libs/chatlite-cli-<appVersion>.jar --help
java -jar build/libs/chatlite-cli-<appVersion>.jar --version
java -jar build/libs/chatlite-cli-<appVersion>.jar --server http://127.0.0.1:8089 --quic-host 127.0.0.1 --quic-port 8080 --no-auto-login
```

JDK 为 21，版本来自 `gradle.properties`。`cliJar` 把独立模块的可执行包复制到原来的 `build/libs` 入口；也可使用 `:cli:installDist` 的应用分发。`verifyHeadlessRuntime` 会拒绝 Compose、Skiko、WebRTC 和 Koin 依赖。只打入目标平台的 QUIC native 库；跨平台构建通过 `-PcliNativeClassifier=windows-x86_64|linux-x86_64|osx-x86_64|osx-aarch_64` 指定一个分类值，不把竖线列表照抄成参数。

默认地址仍是项目现有线上地址；联调必须明确本地 REST 与 QUIC 两个入口。切换 REST 环境需同时指定 QUIC 主机和端口，集群 owner 发现仍由共享传输层执行。REST 根地址不带 `/api`，非本机服务要求 HTTPS。帮助、版本和参数校验在读取凭证、初始化传输或连接网络之前执行。

## 凭证和终端

配置只存服务器入口，位于 `~/.qingliao/cli/config.properties`。凭证放在规范服务器根地址 SHA-256 对应的 `cli/servers/<hash>/data`，使用现有加密数据源；历史与待发队列在相同服务器目录下再按账号隔离。不会读取旧 CLI 配置、旧 JSON 历史或桌面凭证。每个服务器目录使用进程文件锁，避免并发追加历史。

`--username` 指定账号后，失败不会回退登录其他账号。交互密码采用 JLine 掩码，不进入历史；自动化使用 `--username` 和 `--password-stdin` 从标准输入第一行读取 UTF-8 密码，不预读后续命令。不支持 `--password` 明文参数。dumb 终端不能可靠隐藏密码时拒绝交互输入，不回退到明文回显。

标准输出、错误输出和 JLine 使用 UTF-8，旧版命令提示符需切换 UTF-8 代码页。异步输出使用 `printAbove`，保留正在编辑的文字；过滤控制字符，远端内容不能执行终端指令。输入历史仅在内存保存最近 100 条，不写磁盘。

## 命令

| 命令 | 行为 |
| --- | --- |
| 普通文本、`/ai <内容>` | 启动一个后台助手任务，立即返回提示符；重复提交提示先取消，不创建第二个计费请求 |
| `/cancel` | 取消当前 HTTP 等待，丢弃迟到回答；不能保证服务端模型同时停止或撤销已发生的消耗 |
| `/status` | 认证连接状态、助手运行状态与待发数量 |
| `/msg <用户ID> <内容>` | 原子写入待发队列；连接认证完成后发送 |
| `/group send <群ID> <内容>` | 同上，群 ID 保持正数，协议与私聊分开 |
| `/pending` | 查看队列 ID、状态和尝试次数，不回显完整消息内容 |
| `/discard <待发ID>` | 用户明确移除该待发项，不撤回已到达服务端的消息 |
| `/sync` | 重新安排七天历史与离线批次同步，多个请求合并处理 |
| `/history user <ID> [条数]`、`/history group <ID> [条数]` | 本地索引查询，默认 20、最多 100 条，不请求全量历史 |
| `/friend list|add|accept|reject`、`/group list|join` | 保留原有管理入口，待审核入群有独立提示 |
| `/help`、`/exit`、`/quit` | 帮助或正常退出；输入时 Ctrl+C 清空当前行，Ctrl+D 退出 |

未知斜杠命令不会请求 AI。网络操作失败不结束整个终端会话。助手任务拥有会话作用域；取消、鉴权失效和退出会使其代次失效，即使底层晚到返回也不能覆盖新任务输出。

## 待发恢复

`CliOutbox` 使用已有的 fsync/提交标记历史日志，在网络写入之前持久化原始 Protobuf 字节、messageId、时间戳与重试信息。队列最多 128 项，同时最多 4 个发送尝试，只有认证连接就绪才发送。调度器按 messageId 记录进行中的任务；任意尝试结束立即释放名额并唤醒调度，新消息无需等待同一批里的慢 ACK。名额全部占用时仍会等待，不突破并发上限；等待确认的同一 ID 不会被重复调度。重连或进程重启后继续发送原载荷，不生成新 ID；ACK 到达后写删除记录，迟到失败回调不能复活已确认条目。

每次实际发送前提交本地待发历史；写入失败不触发网络发送。4 秒未确认才显示发送中，10 秒未确认进入原 ID 退避重试。成功时保持安静，不把服务端接收解释为接收者已读。

自动重试最长 30 分钟，使用指数退避、最大 30 秒间隔。超过时间进入 `REVIEW`，服务端明确拒绝进入 `REJECTED`，都不再自动发送；需先 `/sync` 核实历史，再决定是否 `/discard`。服务端的客户端 ID 预留/过期拒绝规则仍是权威裁决。没有承诺跨 Redis 数据丢失的 exactly-once，也没有自动把过期条目换新 ID 重发。

## 历史与落盘确认

登录和认证重连会触发共享 `restoreRecentHistory` 七天分页补拉，包含收到、发出和群消息；Redis 优先与必要回源仍由后端实现。随后读取所有未确认离线批次，不再只取一批，也不在持续缩小的未读集合上用 offset 分页。历史和接收状态不推进设备 seq 游标。

`GET /messages/offline?pageSize=100` 现在只读，最多 200 条，返回与七天历史一致的精确字符串 ID、毫秒时间戳和附件/回复元数据。每批必须完整落盘后才能 `POST /messages/offline/ack`，请求为 `{"ids":["101","102"]}`，最多 200 项。确认按认证接收者限制，排除群记录，重复确认安全。消息接收不是用户已读。

共享原生历史恢复与 Web 的 IndexedDB 恢复都按“提交 → 确认”执行；账号变更、落盘失败和确认失败不会继续推进分页。后端删除了返回响应时异步标记接收的逻辑与旧 `/offline/persisted` 别名。部署顺序为后端先提供新只读/确认契约，再部署客户端与 Web；无需新增表结构迁移。

## 连接与证书

CLI 启动连接和助手操作均在会话任务中执行，输入不会等到 QUIC start 返回。重连仍由共享 transport 唯一管理。退出取消任务、注销监听、停止传输、关闭终端和 HTTP 客户端并释放文件锁。

桌面/CLI 共用 Netty 连接已移除任意证书信任。证书链交给 JVM 默认信任库验证，并使用目标主机名校验证书 SAN（DNS 单层通配符或精确 IP SAN），缺失或不匹配直接拒绝；本地自签名服务需显式安装受信证书。旧 incubator 引擎不支持 `setSSLParameters`，且原生引擎可能不提供 peerHost，因此用每次连接捕获的固定目标名，在 `X509ExtendedTrustManager` 链校验后显式核对 SAN，不把设置参数成功当作验证已生效。库行为参考 [Netty 官方说明](https://github.com/netty/netty/security/advisories/GHSA-mj35-3qqm-q387)，本实现没有顺带升级协议库。

## 验证边界

测试使用 fake transport、临时目录、本地 HTTP 和内存终端。另用独立 JVM 进程读取、确认磁盘队列，避免把复用同一静态缓存误当作跨重启恢复。真实账号、生产消息与付费模型未参与测试。

Web/桌面/Android 已同步落盘后确认契约；命令行后台任务、提示符、独立依赖和文件锁属于 CLI 特有实现。桌面保留原历史根目录及本地化标签，Android 仍使用自己的存储实现。没有改动 Protobuf 字段或广播范围。

2026-10-28 本轮验证：根 JVM 测试 120 项、独立 CLI 测试 26 项、Web 测试 121 项、后端定向测试 30 项全部通过。Web 类型检查与生产构建、Android `compileDebugKotlin`、CLI fat JAR 构建与无界面依赖检查通过。Edge 浏览器验证 1200 条历史的 IndexedDB 恢复、本地分页、虚拟列表、阅读位置恢复和移动布局通过。实际 JAR 的帮助、版本、错误参数和本地 HTTP 登录失败路径完成冒烟验证。

Windows 单独启动 Java HTTP 冒烟进程时使用短路径 `jdk.net.unixdomain.tmpdir`；Gradle 检查脚本已向测试进程提供该环境前置条件。后端集成测试源码已编译，但未运行真实数据库集成；真实 QUIC/TLS 握手、设备联调和 Android 安装包未验证。证书测试使用实际 Netty 引擎配合测试证书验证目标名约束，不替代服务端联调。

2026-10-28 后续调度回归：CLI 28 项测试、无 GUI 依赖检查及 CLI fat JAR 构建通过。新增场景覆盖慢 ACK 不阻塞空闲名额、进行中 ID 不重复发送、最多四条并发、完成后补位、退出取消保留待发记录，以及认证恢复/进程重启仍重放原载荷。Web 发送队列异步提交不等待整批 ACK，共享原生 ViewModel 也按消息使用异步回调，没有 CLI 原先的整批等待问题，本轮无需复制 CLI 调度器到其他端。
