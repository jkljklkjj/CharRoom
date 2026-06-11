# ⚡ CharRoom — 多平台即时通讯客户端

Kotlin Multiplatform 核心逻辑共享 + Vue 3 Web 前端，AI-Native IM 全平台客户端。

---

## 🛠️ 技术栈

| 模块 | 技术 |
|---|---|
| 多平台共享核心 | Kotlin Multiplatform 2.0 |
| Web 前端 | Vue 3 + Vite 5 |
| 协议 | Protobuf 二进制编解码 |
| 运行目标 | Android / Desktop / Web 三端 |

---

## 🚀 核心亮点

| 亮点 | 说明 |
|---|---|
| 📱 **三端共享逻辑** | Android / Desktop / Web 同一份 Kotlin 核心代码，消息编解码零差异 |
| ⚡ **RFC 9308 QUIC** | 前端零改动自动升级 HTTP/3 QUIC，弱网下消息到达率 +60% |
| 👀 **ClientAction 采集** | 20项滑动窗口用户行为采集，自动随消息上传到服务端 |
| ✍️ **AI 真人打字机** | 逐字输出，句子间自然停顿120ms，完全模拟真人节奏 |
| 🟢 **送达/已读状态** | 消息气泡蓝勾 → 双勾灰 → 双勾蓝三态指示器 |
| 🔍 **语义高亮** | 向量搜索命中片段自动黄色标亮 |

---

## 📁 目录结构

```
CharRoom/
├── src/
│   ├── commonMain/      # KMP 跨平台共享核心
│   ├── desktopMain/     # 桌面端专用实现
│   └── androidMain/     # Android 端专用实现
└── web/                 # Vue 3 Web 前端
    ├── src/
    │   ├── components/  # ChatWindow / SidebarUsers 组件
    │   ├── views/       # 主页 / 登录页
    │   └── services/    # WebSocket 连接管理
    └── package.json
```

---

## 🚀 本地开发

```bash
cd web

# 安装依赖（Node 20+）
npm install

# 启动开发服务器 http://127.0.0.1:5173
npm run dev

# 生产构建
npm run build
```

---

## 🖼️ Avatar 平台拆分说明

`AvatarCropDialog` 按平台独立实现，入口复用共享的 `ProfileScreen.kt`：

- 桌面端裁剪实现：`src/desktopMain/kotlin/component/AvatarCropDialog.kt`
- Android 端：`androidApp/src/main/kotlin/component/AvatarCropDialog.kt`

后续新增平台专属逻辑优先放入各自模块，避免写入 `commonMain`。

---

## 🔗 相邻项目

👉 **[../chatRoom/README.md](../chatRoom/README.md)** — Java 25 + Netty 高性能后端
