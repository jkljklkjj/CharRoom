# 历史批处理与 PR 检查（2026-10-30）

## 原生历史读写与删除

桌面/CLI 的 `FileLocalChatHistoryStore` 和 Android 的 `AndroidLocalChatHistoryStore` 按会话收集一批历史中的 `clientMessageId → serverId` 映射，先批量持久化别名，再写正文与被替代占位行的 tombstone。别名文件仍是原有 journal 格式，保持 ACK、历史消息和晚到 UI 快照的归并顺序；不改变接收回执或 seq 游标。

`MessageIdAliases.recordAll` 先验证映射，再一次调用 journal 写入；每会话每次历史保存不再逐条调用磁盘同步。`resolveAll` 通过 `IndexedHistoryJournal.readMany` 在一个文件句柄内读取所选 ID，未命中 ID 不读取正文。分页、时间范围和跨会话恢复都按会话批量查询别名，空结果不打开正文句柄。

删除接口现在显式接收账号、目标 ID、消息 ID 和群聊类型。UI 将被删除的消息交给 ViewModel，ViewModel 在异步工作前捕获账号与消息目标，存储只定位该会话文件。未遍历其他会话；同数字私聊/群仍通过类型隔离。Web 已有账号/会话/消息 ID 索引删除和别名事务，本轮无需移植原生文件算法。

同步补齐两个缺口：共享 REST `RecentHistoryMessage` 透传 `clientMessageId`，同时允许旧响应缺省；Android 保存群消息和 JSON 正文时保留该字段，并在恢复、分页和时间范围读取时归并别名。此前 Android 未完整同步桌面的这些行为。新测试从真实 JSON 解码后检查私聊与群聊转换，防止仅存储层测试通过而 REST 入口仍丢字段。

## PR 工作流

`.github/workflows/ci.yml` 对 master 的 PR、push 和手动运行提供三个独立 job，权限为只读，重复运行取消旧任务：

- Web：检出客户端及 Gitee 后端 main（输出后端 commit），`npm ci`、检查两份协议的四处副本、类型检查、全量离线测试、SSR 构建，再检查协议生成文件无差异。后端协议与 nginx 文件是既有跨仓库测试的输入，不启动后端。
- JVM：JDK 21 / Gradle 8.14.5，编译桌面、执行共享/桌面和 CLI 测试、检查 CLI 未引入图形运行时。
- Android：编译 Kotlin 并执行 Debug 单测，跳过 `buildQuicNative`；此 job 不检查 JNI、APK、安装和真实连接，发包工作流仍负责原生构建与打包。

`check-test-results.py` 拒绝缺少报告、零测试、全跳过或有失败的测试报告，避免 `NO-SOURCE` 被当成回归通过；脚本须在 Gradle 成功后执行，不能用旧报告代替本次运行结果。`check-protocol-copies.py` 为只读，默认查相邻后端，支持 `--backend`。两个脚本有 2 项离线回归，覆盖缺失、失败、全跳过、任一副本漂移和不改写文件。

Web 测试显式使用 TAP。本机 Node 24.14.1 的默认报告输出在全量运行中两次出现 `Unable to deserialize cloned data`，单文件通过；显式 TAP 的全量 167 项通过。没有据此断言业务用例失败或确定 Node 内部根因。

工作流文件已经本地语法检查，GitHub 托管执行尚未运行；提交推送后才能产生远端检查结果。若需禁止未通过检查的 PR 合并，还须在仓库分支规则中将这些 job 设为 required checks；新增工作流本身不会修改分支保护。

## 本地验证

- Web：类型检查、167 项离线测试、生产构建与四条路由 SSR 通过；构建后协议及 public 产物没有意外的内容差异。
- Desktop/JVM：130 项通过；CLI：28 项通过，headless runtime 检查通过。
- Android：Kotlin 编译和 5 项既有 Debug 单测通过，跳过 JNI 构建；Android 存储改动通过编译，共享 journal/别名行为由 JVM 测试验证，尚无 Android 真机存储回归。
- 跨仓库双 proto 四处副本检查通过；没有修改协议字段、生成类或本地文件格式。
- 后端缓存 9 项 mock 测试通过；完整应用集成、真实基础设施与故障恢复、性能基准、安装包和真机收发未运行。

Windows 通过 `scripts/gradle-windows.ps1` 运行上述 Gradle 任务，遵循已有的 Java socket 修复。测试使用合成数据，不连接线上或付费模型。

## 继续审查的优先项

1. 初轮后端 SpotBugs 失败共 39 条；后续 [快照列表隔离](../../../chat-room/docs/development/SNAPSHOT-COLLECTIONS.md) 已消除 28 条，当时仍剩 11 条，后续 [静态检查修复](../../../chat-room/docs/development/STATIC-CHECK-FIXES.md) 已完成处理，SpotBugs 复扫为零。应区分真实风险与分析器误报，不扩大排除列表掩盖问题。例如 WebTransport 的 parent 非空由 `wtReady` 的提前返回保证，不能仅凭该告警宣称可复现空指针。
2. 为七天热点缓存补命中率、回源次数/时延、写入开销和内存测量，再决定继续保留、缩减或取消缓存。当前实现存在不等于已有收益证据。
3. 原生单会话深分页仍使用 `drop(skip)`。跨会话深页后续已增加 [有界多路归并](HISTORY-MERGE-FRONTIER.md)，最多 64 个会话时缩小候选空间；更多会话保留原 top-k。跨请求 `(timestamp, id)` 游标和真实大历史基准仍待完成。
4. 别名生命周期仍需设计：清空会话只删正文 journal，别名文件保留；中断 ACK 后残留的 client-ID 行与按 canonical-ID 删除之间也值得补专门故障回归。这些是本轮静态审查发现的后续项，未宣称已经验证修复，不能直接删掉别名来节省空间，否则晚到快照可能恢复重复行。

后续已完成的空操作写入与整批参数校验见 [历史空操作](HISTORY-NOOP-WRITES.md)。
