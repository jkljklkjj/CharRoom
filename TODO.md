# TODO — CharRoom 全栈项目

---

## 🔬 协议状态机验证与故障注入

- [x] 定义消息生命周期状态机（SENDING → SENT → ACKED → DELIVERED）— `MessageStateMachine.java`
- [x] Property-based test 随机生成丢包、重复、乱序场景 — `MessageStateMachineTest.java` 11 个测试
- [x] 验证不变量：messageId 最多展示一次、已确认消息不回退、重试次数上限
- [x] 故障注入测试（进程崩溃、网络分区、Redis/Kafka 超时）— `FaultInjectionTest.java` 12 个测试
- [x] 自动输出失败 trace，可复现消息丢失/重复/乱序原因 — `MessageTrace.java` + `MessageTraceTest.java` 8 个测试

---

## 📊 端到端性能画像

- [x] OpenTelemetry trace 串联：客户端发送 → 网络 → Kafka → MySQL → 推送 — `MessagePerformanceMetrics.java`
- [x] "点击发送到对端渲染" 全链路延迟埋点 — 6 个阶段计时器
- [x] P50/P95/P99 延迟 dashboard（Grafana）— `grafana/dashboard-message-performance.json`
- [x] 弱网场景性能对比（WebSocket vs QUIC）— `src/test/perf/ch4-transport/weak-network.sh`

---

## 🚀 QUIC QoS 控制面

- [x] 消息优先级：私聊 > 群聊 > Agent > 文件 — `MessagePriority.java`
- [x] Datagram 通道：typing、在线状态、临时反应 — `QosManager.java`
- [x] Stream 通道：消息、文件、ACK — `QosManager.java`
- [x] 基于 RTT/丢包的自动降级策略 — `NetworkConditionMonitor.java`
- [ ] 可复现实验台：注入丢包/乱序/限速，对比三种模式延迟

---

## 🌐 前端 Web

- [x] WebTransport Datagram — `WebTransport.js` (sendDatagram + typing/presence/reaction)
- [x] WASM 编解码 — `protoWasm.js` + `worker.js` (WASM 加速 5-10x)
- [ ] 可复现实验台：注入丢包/乱序/限速，对比三种模式延迟

---

## 📱 KMP 客户端

### 低优

- [ ] build.gradle Groovy → Kotlin DSL 迁移
- [ ] 静态分析（detekt / ktlint）

---

## 🖥 CLI 客户端

### 中优

- [ ] 日志框架（slf4j-simple 或 Kermit 替代 println）

### 低优

- [ ] 命令历史与自动补全（jline3）
- [ ] 终端彩色输出
- [ ] `/help <command>` 详细帮助
- [ ] 配置文件持久化（`~/.qingliao/config.properties`）
