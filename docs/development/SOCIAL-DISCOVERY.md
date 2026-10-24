# 社交发现客户端首版

2026-10-24。后端入口为相邻 chat-room 仓库的 `/api/social`，完整服务契约见其 `docs/development/SOCIAL-DISCOVERY.md`。

## 使用与代码入口

Web 在联系人标题栏的地球按钮打开「发现与遇见」，包含找人、轻遇、今日一问和我的名片。设置里的编辑资料及聊天里的好友资料也复用新资料卡。

Web：`components/SocialHub.vue`、`SocialProfileCard.vue`、`EncounterRoom.vue`，请求在 `api/social.ts`，随机房轮询在 `composables/useEncounter.ts`。复用原主题变量，并将 WebApp 已有的弹窗、输入框、按钮和标签切换规则提取为 `styles/app-controls.css`；新增 `styles/social.css` 仅承载社交布局。中、英、日语言文件均已补齐。

桌面/Android：联系人标题栏的发现按钮进入 `component/social/SocialScreen.kt`，原联系人详情入口也接入同一资料组件；业务协调在 `presentation/viewmodel/SocialViewModel.kt`，请求在 `data/repository/SocialRepository.kt`，模型在 `model/SocialModels.kt`。沿用 Compose MaterialTheme 和 UserAvatar。Repository 绑定创建时的 token，页面关闭取消协程；未增加 CLI 交互命令。

客户端只把服务端确认后的资料和关系显示为成功。随机房没有本地假匹配或随机测试用户，重试保持 messageId，按 seq 合并消息，关闭页面停止轮询，重新打开可恢复当前房间。准备阶段必须双方确认；延长和交换均需双向同意。

首次使用需要在「我的名片」将社交详情设为公开并允许推荐。未授权旧用户不会自动进入目录；城市为手动填写。好友请求留言在申请箱展示；原生模型同时兼容历史 senderName/message 与当前 username/reason 字段。

## 验证

Web 工作目录 `web/`：

```text
npm test
npm run build
node tests/social-browser.cjs <Chromium可执行文件>
node tests/browser-smoke.cjs <Chromium可执行文件>
```

社交浏览器回归使用两个独立账号夹具，拦截所有远端请求，检查资料编辑、申请、双方准备、丢确认后的消息重试、交换、延长、退出、回答文本转义和手机/深色布局。它验证真实构建产物的 UI，不代表已连接真实后端。可选 `SOCIAL_SCREENSHOT_DIR` 保存布局截图。

本轮结果：23 个 Web 单测、类型检查和生产构建通过；社交回归与原有双账号聊天/ACK/Worker 浏览器回归通过；浅色/深色 1000、390、320px 布局无横向越界，已检查截图。

客户端根目录：

```text
./gradlew.bat compileKotlin
./gradlew.bat test --tests core.SocialContractTest
./gradlew.bat :androidApp:assembleDebug
```

Wrapper 缺失时保留用户删除，使用既有 `scripts/gradle-windows.ps1` 与已安装/缓存的 Gradle，不自动恢复或下载 Wrapper。本轮桌面共享编译、4 个契约模型测试、Android Kotlin 编译通过。

默认 `assembleDebug` 在既有 buildQuicNative 步骤失败，原因是所选 NDK 缺少当前主机工具链。随后确认项目已有 arm64-v8a / x86_64 两套非空 ELF JNI 库，使用现有参数复用后，完整 Debug APK 构建通过：

```text
./scripts/gradle-windows.ps1 :androidApp:assembleDebug -PquicJniLibsDir=../native/quiche-jni/jniLibs --console=plain
```

该路径由 Android 子项目解析，指向项目自有的预编译目录；没有恢复 Wrapper、修改 NDK 配置或重新编译 JNI。输出为 `androidApp/build/outputs/apk/debug/androidApp-debug.apk`。构建通过不代表 JNI 源码重建或真机运行通过。

本轮未进行真机、真实 QUIC 收发或真实后端双账号联调。随机房走现有 REST 地址，普通消息的 QUIC 目标仍需独立核实；测试不要使用默认线上地址。
