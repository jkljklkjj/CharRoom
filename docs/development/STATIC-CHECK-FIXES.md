# 静态检查修复（2026-10-30）

功能版本为 `c7ae4a5`。本轮修复检查配置、Android Lint 错误，以及扫描中确认的协程取消问题。

## 代码与配置

- `config/detekt/detekt.yml`：按已缓存的 detekt 1.23.7 默认配置修正重复 ruleset、无效规则/属性名；移除未安装 formatting 插件的虚假配置；规则归入实际所属 ruleset。保留 `maxIssues: 0`，启用配置校验并将配置警告视为错误。
- 原来的 `TooGenericExceptionCaught.exceptionNames` 只列了 CancellationException，与“允许取消异常”的注释相反；现使用默认通用异常列表。此前重复 YAML 导致分析根本未执行，不能把此前状态记为通过。
- 联系人排序、注册日期和配额金额格式化使用 Compose 当前区域设置，语言/区域变化后重新计算。Android 的 2 项 `NonObservableLocale` 错误已消除。
- HTTP 上传、私聊/群聊发送及在线状态查询明确重新抛出 CancellationException，避免页面离开、退出账号或取消任务被转换成正常返回的 null/false/空结果。补充原先静默失败的上传、群聊请求日志。
- 移除历史游标读写中未使用的 `userHome` 变量；两个 FileUploader 取消路径改为明确的 catch 分支。

## 验证

Windows 使用仓库 `scripts/gradle-windows.ps1`，JDK 21：

```powershell
./scripts/gradle-windows.ps1 :test :cli:test :cli:verifyHeadlessRuntime :androidApp:lintDebug :androidApp:testDebugUnitTest -x :androidApp:buildQuicNative --offline --no-daemon '-Pkotlin.compiler.execution.strategy=in-process' --max-workers=2
./scripts/gradle-windows.ps1 detekt --offline --no-daemon '-Pkotlin.compiler.execution.strategy=in-process' --max-workers=2
```

`ApiCancellationTest` 使用已取消的协程与本机回环地址，不访问线上服务。将修复前的两个 API 文件代入时，4 个用例均以“取消后不应正常返回”的行为断言失败；修复后 4 项通过。JVM 合计 148 项、CLI 28 项、Android 5 项测试通过；CLI 无界面依赖检查通过。Web 没有对应协程运行时，区域显示也使用独立实现，无需同步代码；`web` 中 `npm run typecheck` 通过。

Android Lint 通过，0 errors、119 warnings、12 hints（原 124 warnings 中 5 项区域格式化警告已消除）。该命令跳过本机 QUIC Rust 构建，不代表 JNI、APK 或设备联调通过。

## 尚未通过的门禁

Detekt 现在能执行实际扫描，从首次 579 项降为 567 项，**仍失败**。未添加 baseline、全局抑制、扩大排除目录或提高阈值来制造通过结果。主要存量如下：

| 规则 | 数量 |
| --- | ---: |
| MaxLineLength | 221 |
| TooGenericExceptionCaught | 57 |
| ExpressionBodySyntax | 55 |
| ComplexCondition | 34 |
| InstanceOfCheckForException | 31 |
| CyclomaticComplexMethod | 28 |
| ReturnCount | 22 |
| UnusedParameter | 19 |
| 其他规则 | 100 |

广泛异常捕获既有网络/回调边界隔离，也可能掩盖取消或损坏数据，需要逐场景验证；不能笼统认为剩余项目全是格式问题。复杂度项需要拆分既有大型 UI/ViewModel/传输逻辑，本轮未批量重写。完整可定位报告在运行后生成的 `build/reports/detekt/`，不提交构建输出。
