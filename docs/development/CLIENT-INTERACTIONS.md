# 客户端交互优化（2026-10-25）

本轮覆盖 Web 和共享 Compose UI，沿用现有 CSS 变量、设置控件和 MaterialTheme。

## 修改入口与效果

- `component/user/UserListScreen.kt`、`web/src/components/SidebarUsers.vue`：独立的发现入口、统一图标、搜索清空与空结果恢复；原生联系人立即呈现，去掉按列表序号累积的延迟和在线圆点持续跳动。联系人昵称与状态分离。Web 的计算属性同时跟踪资料变化，避免旧缓存保留过期昵称或头像。
- `component/chat/ChatInputBar.kt`、`web/src/utils/composer.ts`：输入法候选确认期间不触发发送，普通 Enter 发送、Shift+Enter 保留换行；空白输入不能发送，原生附件与表情改为 Material 图标和 44dp 点击区域。聊天页移除人为等待后才显示本地内容的过渡。
- `web/src/composables/useDialog.ts`：设置、退出确认、社交、联系人申请、资料、删除确认、转发和额度弹窗统一管理焦点、Tab 循环、Esc、页面滚动锁定与嵌套关闭；响应式布局替换按钮后仍可返回同名入口。
- `component/social/SocialScreen.kt`、`web/src/styles/social.css`：统一内容宽度、选中导航、头像与封面层级、表单分组、空状态和加载位置；临时聊天使用独立消息滚动区。Web 的保存成功与操作失败使用不同反馈，保存期间禁用资料表单，并可重试资料加载。
- `web/src/components/ChatWindow.vue`、`EncounterRoom.vue`：接近底部时跟随，阅读旧消息时提供新消息入口；历史补页等待完成后保持锚点，补页不计为新到达消息。聊天容器覆盖全局平滑滚动；显式点击跳转保留动画。临时房发送响应晚到时保留用户随后输入的新草稿，重试仍使用原消息 ID。
- 新文案同步中文、英文和日文。

## 回归命令

在 `web/` 执行：

```text
npm test
npm run build
node tests/browser-smoke.cjs <Chromium/Edge executable>
node tests/social-browser.cjs <Chromium/Edge executable>
```

浏览器用两组虚构账号、拦截所有外部 REST 请求和模拟传输；使用真实打包代码、Worker 与 IndexedDB，不连接真实聊天账号。覆盖输入法、键盘联系人选择、空搜索恢复、阅读历史时新消息提示、模态焦点与恢复、晚到发送响应、新旧社交流程，以及 1000/390/320px 浅色和深色布局。

在客户端根目录执行（Windows）：

```text
./scripts/gradle-windows.ps1 :test --tests component.chat.ComposerInteractionTest --tests core.SocialContractTest
./scripts/gradle-windows.ps1 :androidApp:assembleDebug -PquicJniLibsDir=../native/quiche-jni/jniLibs
```

组合 Android 和 JVM 测试任务时使用根路径 `:test`，避免把 `--tests` 应用到 Android 的聚合任务。APK 复用项目已有 JNI 库；不等于重新验证原生 QUIC 构建。

本轮不改后端或 wire 协议；真实服务联调、桌面实际窗口体验和 Android 真机触控/软键盘仍需独立验证。会话草稿持久化、全量屏幕重新设计不在本轮交付范围内。

## 本轮实际验证

- Web：类型检查与生产/PWA 构建通过；26 项隔离测试通过；两个打包后浏览器脚本通过。检查了资料卡浅色/深色、1000/390/320px 布局，并查看实际页面截图。
- Desktop / CLI 共用 JVM 源码：编译通过；3 项输入交互测试、4 项社交契约测试通过。未启动实际桌面窗口或验证 CLI 交互。
- Android：Debug APK 构建通过，复用已有 JNI。未安装真机或模拟器，未做真实服务收发联调。
