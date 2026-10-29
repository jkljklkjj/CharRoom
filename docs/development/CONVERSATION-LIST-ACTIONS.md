# 会话列表置顶与删除（2026-10-29）

Web 端消息页会话行支持右键（鼠标）或长按 500ms（触屏/手写笔）打开菜单：

- **置顶 / 取消置顶**：置顶会话始终排在消息页最前，再按最后消息时间、未读数排序；行内显示小图钉。联系人页仍按昵称排序。
- **删除会话**：只从消息页隐藏该行，**不删除 IndexedDB 中的历史消息**，也不影响联系人页。下一次发送或收到该会话的消息时自动恢复展示；删除同时清除该会话的置顶状态。
- 删除当前正在查看的会话会关闭聊天窗口，但历史仍可通过联系人页重新打开。

## 存储与恢复

偏好按登录账号保存在 `charroom_chat_history_<accountId>_conversation_prefs`，与聊天历史共用账号前缀，退出登录时由 `auth.clearAll` 清理。账号切换时由 `setAccountId` 重新加载，损坏或非法键会忽略。

恢复展示的触发点是 `store.updateConversationState` 收到 `lastMessagePreview` 的更新：私聊/群聊的发送分支和接收分支都会带上该字段，因此不需要在消息组件里重复判断。其他历史同步、未读数清理不改变隐藏状态。

## 交互与可访问性

- 鼠标右键通过 `contextmenu.prevent` 打开；触屏/手写笔只在指针移动小于 12px 且持续 500ms 后打开，长按后抑制这一次点击，避免顺带进入会话。
- 键盘聚焦会话行后，`ContextMenu` 键或 `Shift+F10` 打开同一菜单；菜单是 `role="menu"`，打开后聚焦第一项，Escape 关闭并把焦点还给原会话行。
- 菜单使用 `position: fixed` 并按视口夹取位置；点击菜单外或滚动列表会关闭。
- 联系人页和搜索结果行不提供该菜单；它们不是会话。

## 验证

```text
cd web
npm run typecheck
npm test                       # 153 项通过
npm run build
npx --yes node@22 tests/contacts-browser.cjs /usr/bin/chromium
```

浏览器回归覆盖：右键置顶后置顶标记与首位排序、右键删除后行消失、收到新消息后恢复且置顶已清除、触屏长按打开菜单、Escape 关闭。Node 26 下 puppeteer-core 的 yargs 依赖会报 ESM 错误，本地使用 Node 22；以 root 运行 Chromium 时需要额外的 `--no-sandbox`，该参数未写入仓库测试。

## 跨端状态

当前只改 Web。桌面/Android/CLI 的会话列表没有同类菜单，也未接入本偏好；原生保存/删除语义需要单独实现和验证，本次未运行原生构建。置顶和隐藏是本地展示状态，不同步到其他设备，也不改变服务端会话或消息数据。
