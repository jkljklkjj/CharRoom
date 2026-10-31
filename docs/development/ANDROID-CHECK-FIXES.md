# Android 检查修复（2026-10-31）

## 实现

- 更新下载和安装包复制前，通过 StorageManager 查询包含可回收缓存的可分配空间，并请求释放所需空间；卷不支持或分配发生 IOException 时，退回 StatFs 的实际可用空间检查。仍保留 32 MiB 余量，写入失败继续由下载流程处理。新增五个单元测试覆盖缓存回收、空间不足、卷不支持、分配失败和权限异常。API 语义见 [Android StorageManager](https://developer.android.com/reference/android/os/storage/StorageManager)。
- 图片加载器只保存缓存目录，不保存 Context；磁盘缓存采用共享原子文件替换。历史存储明确保存 Application，保持账号隔离和同步提交行为。历史游标持久化继续检查 SharedPreferences.commit() 的布尔结果，仅局部抑制不适用的 UseKtx 建议，不能改成丢弃提交结果的 edit 扩展。
- 通知权限请求直接受 API 33 版本保护；LifecycleOwner 使用 lifecycle-compose 入口。Exif 使用 AndroidX，清理低于 minSdk 26 的不可达分支。
- Compose 数值状态使用原生数值状态及相应访问器，减少装箱；调整 Modifier 参数顺序、启动图标物理尺寸，删除未使用颜色资源。显式启用 BuildConfig，移除弃用的全局配置及 RenderScript 调试配置。
- 桌面 HTTP 更新下载实现移至 desktopMain，Android 不再编译这份桌面实现。调试预览工具仅作为运行时依赖，应用源码不依赖其 API。

## 检查边界

当前源码使用 Material 2。Debug 的 ui-tooling 传递引入 Material 3，Lint 因此对 Material 2 调用报告 UsingMaterialAndMaterial3Libraries。保留调试预览工具及该检查，不全局关闭规则，也不排除工具实际依赖；该组提示仍需记录，不应声称 Android Lint 完全无告警。

跨端评估：Web 使用独立的浏览器存储、权限与下载实现，不适用 Android 平台修复；本轮共享 UI 改动保持业务状态、界面和协议语义。桌面仅移动下载实现位置，需运行 JVM 回归。无协议、接口或版本号修改。

构建与单元测试不能代替真机验证。使用预构建 JNI 的 APK 验证不代表重新编译或验证了 Rust 原生实现；通知授权、图片旋转及低空间安装仍需设备回归。

## 实际验证

- `:androidApp:assembleDebug` 通过，使用已有预构建 JNI；APK 内 arm64-v8a、x86_64 的 QUIC 库和 C++ 运行库均存在且非空。
- `:androidApp:testDebugUnitTest`、`:androidApp:testReleaseUnitTest` 各 10 项通过；JVM 161 项、CLI 28 项通过，无失败或跳过。
- `detekt` 零问题；`:androidApp:lintDebug` 零错误，剩余 82 条均为上述调试工具引出的 Material 提示，其他类别已清除。
- `git diff --check` 通过。未运行设备安装、真机交互或后端联调；未执行 Release APK 打包和 Rust 原生重编译。Gradle 的既有弃用提示及实验性资源裁剪提示仍保留。
