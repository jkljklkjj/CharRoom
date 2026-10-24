# CharRoom Web

> 开发与 AI 协作先读 [AGENTS.md](AGENTS.md)。2026-10-23 检查发现现有源码的生产构建被存储模块缺失导出阻断，Web proto 与后端也存在差异；详见 [现状审查](../../docs/architecture/2026-10-23-现状审查.md) 与 [改造计划](../../docs/architecture/AI友好化与改造计划.md)。下列命令描述预期流程，不表示当前构建已经通过。

基于 Vue 3 + Vite 的轻量 Web 原型，包含项目介绍页和在线聊天原型视图。

快速开始（Node 20）：

```bash
cd CharRoom/web
npm install
npm run dev
```

说明：
- `npm run dev` 在本地启动开发服务器（默认端口 5173）。
- 下载链接指向仓库生成产物 `../target/`，可替换为 CI release 地址。

## 生产环境部署
前端代码更新后，需要执行以下步骤完成部署：

1. **构建前端静态资源**：
   ```bash
   npm run build
   ```
   构建产物将生成在 `dist/` 目录下。

2. **重新构建并启动 Nginx 容器**：
   ```bash
   cd ../../chat-room/docker/nginx
   docker compose down
   docker compose up -d --build
   ```
   Nginx 容器会自动加载 `dist/` 目录下的最新静态资源。
