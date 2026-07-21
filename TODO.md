# TODO — CharRoom 全栈项目

---

## 🌐 前端 Web

- [ ] **离线首屏加载策略**
  - 虚拟滚动 + 分页 + seqId 增量同步
  - 现状：消息分页已实现（PAGE_SIZE=50），但无虚拟滚动
  - 评估：分页已足够，虚拟滚动可能不需要

- [ ] **WebTransport Datagram**（长期）
  - 不可靠传输，心跳/状态同步

- [ ] **WASM 编解码**（长期）
  - protobuf.wasm 提速 3-5×

---

## 📱 KMP 客户端

- [ ] build.gradle Groovy → Kotlin DSL 迁移
- [ ] fat JAR 打包改用 Shadow plugin
- [ ] 静态分析（detekt / ktlint）

---

## 🖥 CLI 客户端

### 中优

- [ ] `/msg <userId> <text>` 直聊命令
- [ ] 密码输入安全（Windows 下 `System.console()` 返回 null）
- [ ] 日志框架（slf4j-simple 或 Kermit 替代 println）
- [ ] 离线消息拉取（登录后调用 syncMessages）
- [ ] 自动重连（连接断开后指数退避重试）

### 低优

- [ ] 命令历史与自动补全（jline3）
- [ ] 终端彩色输出
- [ ] `/help <command>` 详细帮助
- [ ] 配置文件持久化（`~/.qingliao/config.properties`）

---

## 前后端协同

| # | 问题 | 建议 |
|---|------|------|
| 1 | 消息 ID 双标准 | 统一由后端生成 |
| 2 | 群组 ID 符号约定 | 统一为正值 |
| 3 | 设备管理协议 | 实现 DeviceMessage |
| 4 | Protobuf 版本演进 | buf breaking CI 检测 |
