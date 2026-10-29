# 手机顶部边界与页面滚动（2026-10-29）

## 问题与最终行为

首页与 `/app` 刷新后出现顶部空白，来源是全局 `body` 的 `padding-top: env(safe-area-inset-top)`，这段padding会直接成为首帧可见页面内容。原来的body滚动锁还要等WebApp挂载，初始HTML阶段没有锁住根文档；聊天视图另用fixed定位加顶部inset，与侧栏不是同一个内容边界。

- 普通手机浏览器：`--page-safe-top` 为0，不额外为地址栏保留页面高度，刷新即从实际内容顶部开始。根文档 `overscroll-behavior-y:none` 限制顶部拖动边界；首页仍正常上下滚动，不把首页整体锁死。
- `/app`：构建HTML的html元素带 `data-app-viewport`，CSS在脚本/认证恢复之前就锁住文档。SSR与SPA一致；联系人、消息和登录表单保留各自的内部滚动。移动聊天视图绝对定位在与侧栏相同的内容盒中，不再重复添加顶部inset。
- SPA进入/离开：`services/pageViewport.ts` 由App路由监听统一维护html标记和body兼容类。进入app清除外层旧滚动位置一次；语言切换不会反复归零，离开app会释放根滚动锁。
- 手机社交弹层采用同一个顶部变量，不重新增加浏览器栏留白；可见视口与软键盘调整仍由useVisualViewport处理。
- 独立安装、全屏及minimal-ui模式保留真实屏幕刘海/状态区的 `safe-area-inset-top`。左右与底部安全区继续保留。此改动不会强制隐藏浏览器自己的地址栏。

入口：[全局样式](../../web/src/styles.css)、[路由同步](../../web/src/services/pageViewport.ts)、[构建HTML](../../web/scripts/site-seo.cjs)、[聊天布局](../../web/src/views/WebApp.vue)、[弹层样式](../../web/src/styles/social.css)。根锁由App统一管理，不能再由WebApp挂载后单独添加/删除。

## 验证

- `npm run build`：严格类型检查、打包、初始HTML/4入口SSR通过。
- 串行Web单测147项通过，包括初始HTML锁、SPA释放和内部滚动边界。
- `node tests/viewport-browser.cjs <browser>`：真实Chromium `Emulation.setSafeAreaInsetsOverride` 模拟64/48像素顶部安全区，修复前复现body顶部64像素留白；修复后禁JS首帧、首页下拉边界、窗口变化、SPA进入/返回全部通过。
- `contacts-browser.cjs`：64像素安全区下侧栏与聊天顶部栏同为0，联系人/搜索/5000人列表、私聊群聊、草稿/ACK等既有回归通过。
- `app-ssr-browser.cjs`、`seo-browser.cjs`、`pwa-navigation-browser.cjs`：首帧/水合、公开内容及私有noindex、真实Worker升级/离线路由回归通过。
- `social-browser.cjs`：手机弹层顶部边界、可见视口缩小/偏移、资料编辑/隐私与浅深主题回归。

这里只模拟CSS安全区和触摸滚动，不代表实际手机地址栏动画或iOS橡皮筋已验证。未运行真实手机、独立安装窗口或旧版Safari；其真实刘海保留由display-mode样式规则处理。`overscroll-behavior` 的浏览器能力见 [MDN](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/overscroll-behavior)，测试入口来自 [Chrome DevTools Protocol](https://chromedevtools.github.io/devtools-protocol/tot/Emulation/#method-setSafeAreaInsetsOverride)。

## 跨端

只涉及Web的DOM、CSS、SSR与浏览器滚动容器。桌面/CLI/Android原生有独立系统窗口与安全区布局，不读取这些样式，无需同步Kotlin代码，未运行原生构建。没有更改后端、账号认证、搜索/握手限流或消息协议。
