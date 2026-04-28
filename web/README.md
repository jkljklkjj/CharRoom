# CharRoom Web

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
