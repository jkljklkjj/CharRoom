# Web 与原生客户端性能改进（2026-10-28）

本轮针对已有调用链减少无关更新、重复计算和解码内存。未改变协议、ACK 语义或部署方式，也未进行真实账号联调。

| 项目 | 实现 | 跨端评估 |
| --- | --- | --- |
| 按群订阅 | `GroupChatScreen` 使用 `ChatState.groupMessagesFor(正群ID)`。后续已移除内部负 ID 转换，联系人与预览使用 [显式会话类型](CONVERSATION-IDENTITY.md)。其他群消息不更新当前群的列表快照。 | 桌面、Android 共用；Web 已按所选会话维护页面消息。 |
| 内存与磁盘隔离 | `SocialLocalCache` 的 UI 内存操作使用短锁，文件读写单独串行。退出立即使会话失效，后台清理文件；后续磁盘操作先完成待清理任务，防止重新登录读到残留。持久化序列化与 fsync 不持有 UI 内存锁。 | 桌面、Android 共用；Web 浏览器存储实现没有这把 JVM 锁，保留既有账号/修订隔离。 |
| 头像加载 | 共享 `WeightedLoadCache` 合并同键并发加载，限制最多 4 个并发加载，按估算解码字节维护 24 MiB LRU。头像按实际像素尺寸分桶；桌面使用 ImageIO 子采样，Android 使用 BitmapFactory 子采样及缩放。统一读取/写入缓存键，取消单行加载不取消其他等待者；清空后旧任务不能回填内存。 | 两个原生平台同步实现；Android 移除重复的解码内存缓存，磁盘保留大小上限。Web 联系人头像使用固定尺寸、lazy 加载与 async 解码，继续复用浏览器图片缓存。 |
| 联系人虚拟化 | Web 分组标题与联系人展平成固定高度行，按视口加预加载范围挂载，保留稳定联系人对象；Home/End/方向键先滚动再聚焦未挂载目标。消息排序不再复制每个联系人对象。 | 原生现有列表使用 Compose LazyColumn；本轮没有再引入平行虚拟列表。CLI 没有联系人图形列表。 |
| 消息缓存开销 | Web 会话缓存记录各页条数/字符量及总量，只重算改变的页面；批量更新结束后统一淘汰。原生 MessageLruCache 对消息ID建立索引，内容更新不再扫描ID；直接复用已发布快照，避免读取时复制列表，并移除重复的相等性检查。 | 两端各自减少缓存维护成本，保留原有容量和淘汰语义。 |
| 消息高度索引 | Web 使用 Fenwick 索引维护行高，单次测量更新与偏移查询为 O(log n)，插入/删除时才重建。捕获阅读锚点复用当前索引；普通滚动与流式内容高度变化无需重算整段偏移。 | Compose 使用自己的 LazyColumn 测量机制，不复制 Web 高度索引。 |

缓存预算约束的是保留的解码图片，不是整个进程内存；正在解码的图片、UI 持有的图片及平台图形资源不包含在 24 MiB 中。
尺寸分桶区分原图和缩略图，查看原图仍可按原尺寸加载。Android 的磁盘缓存保留原有 100 MB 上限。
并发头像加载和取消行为由通用缓存隔离测试验证；Android 的解码/图形内存尚未做设备实测。

## 实际验证

- Web：`npm run build` 与 `npm test` 通过，共 100 项自动化测试。新增测试覆盖增量预算、不重读无关缓存、5000 联系人范围和 10000 行高度索引。
- 浏览器：`contacts-browser.cjs` 通过，5000 个联系人挂载少于 35 行，覆盖分组尾部、跨虚拟范围键盘导航、搜索与选择；已有账号缓存、移动布局与延迟 ACK 回归继续通过。
- 浏览器：`history-browser.cjs` 通过，覆盖 1200 条分页消息、DOM 上限、长消息高度锚点、会话重开及移动布局切换。
- 桌面/CLI：`:compileKotlin` 通过；`SocialLocalCacheTest` 10 项、`WeightedLoadCacheTest` 3 项、`MessageCachePerformanceTest` 3 项、`AvatarDecodeTest` 2 项，共 18 项通过。包括阻塞磁盘写入时的 UI 暂存/退出、群订阅隔离、LRU 索引与去重、并发加载合并/取消/字节淘汰、2048×1024 图片缩小到 128×64。
- Android：`:androidApp:compileDebugKotlin -x buildQuicNative` 通过。跳过 JNI 构建，未生成 APK、未装机验证。
- `git diff --check` 通过。没有提交或推送；保留工作区此前修改。

以上是行为和算法开销的验证，不能据此推算真实设备的帧率、峰值堆内存或速度提升百分比。


## 联系人排序与搜索分离（2026-10-28 后续）

Web `contactDirectory.ts` 和共享原生 `ContactDirectory.kt` 将姓名排序与搜索筛选拆开。组件按联系人数据、账号及排序 locale 缓存分组目录；查询词变化只过滤已排序结果，不重新创建 Collator 或再次执行 O(n log n) 排序。空搜索直接复用目录/联系人列表，搜索仍即时更新，没有增加人为输入延迟。姓名或 ID、群组/好友分组、排除本人及助手的规则保持原样。

会话列表使用两端对应的 `sortConversationUsers`：先对每个联系人计算一次类型化会话键、活动时间和未读数，再比较投影后的数值。活动状态读取从比较器内的重复调用降为每个联系人一次；排序仍按最近活动、未读数降序，相同时保留输入顺序，不把同数字的私聊和群聊合并。排序投影为本次计算的 O(n) 临时空间，没有增加跨账号全局缓存。

原生 `UserListScreen` 的会话排序不再依赖搜索词，在线人数按用户快照缓存。联系人标签页不执行隐藏会话列表的活动排序；会话标签页不筛选隐藏的联系人分组。Web computed 本身按实际读取的标签页惰性求值；搜索关键词归一化每次过滤只做一次，避免每个联系人重复 trim 和转换查询词。

验证：Web 123 项隔离测试、类型检查和生产构建通过；JVM 122 项测试、Android `compileDebugKotlin -x buildQuicNative` 通过。新增测试覆盖缓存目录过滤、空搜索复用、排序不修改源列表、每个联系人只读取一次活动状态、稳定并列顺序及同 ID 私聊/群聊隔离。先增加失败测试复现排序/筛选未分离，再完成实现。

Edge 的 `contacts-browser.cjs` 使用本地虚构数据运行，通过 5000 联系人的真实 Vue 页面验证：搜索输入前后 Intl.Collator 比较次数不增加，继续满足少于 35 行联系人 DOM 的限制；键盘导航、移动布局、缓存恢复及延迟 ACK 回归通过。计数断言证明搜索没有重新排序，不代表真实设备帧率或耗时提升百分比。

命令（工作目录 CharRoom；Web 命令在 web）：

```text
./scripts/gradle-windows.ps1 -GradleArguments @(':test', ':androidApp:compileDebugKotlin', '-x', 'buildQuicNative')
node --experimental-vm-modules --test --test-concurrency=1 tests/*.test.cjs
npm run build
node tests/contacts-browser.cjs <Chromium executable>
```

本轮原生共享 UI 同时覆盖桌面和 Android，Web 同步实现；CLI 无图形联系人列表，无需修改。离线 Compose 预览的函数调用已更新。未运行 Android 真机、桌面实际交互性能分析或真实服务器联调；没有修改协议、本地数据库格式、发送确认时序或后端接口。工作区前一轮的后端、CLI 和签名改动均保留，本轮未提交或推送。
