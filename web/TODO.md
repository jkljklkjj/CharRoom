# TODO — CharRoom (Frontend)

## 🔴 High Priority

### 1. WebTransportTransport `onclose` 从未被调用
- **文件**: `WebTransportTransport.js:116-138`
- **问题**: `_startReading` 读取循环退出时（`done === true` 或异常），未调用 `this._onclose`。
- **影响**: WebTransport 断开后不触发重连，页面永久断线。
- **修复**: 在循环退出/异常时调用 `this._onclose()`。

### 2. `isReconnecting` 在重连失败后永久 locked
- **文件**: `chatSocket.js:303`
- **问题**: `isReconnecting = true` 设好后调用的 `connect()` 返回的 Promise 未被 catch，异常时 `isReconnecting` 永不重置。
- **影响**: 后续所有断线事件都因 `!isReconnecting` 检查不通过而跳过重连。
- **修复**: `.catch(() => { isReconnecting = false })`。

### 3. transport.ready / createBidirectionalStream 无 timeout
- **文件**: `WebTransportTransport.js:46, 50`
- **问题**: `await this._transport.ready` 和 `await this._transport.createBidirectionalStream()` 无超时机制。
- **影响**: QUIC 握手 hang 住时前端永久卡在连接状态。
- **修复**: 用 `Promise.race` 加超时（如 15s）。

## 🟡 Medium Priority

### 4. `flushQueue` 可能在 login 前执行
- **文件**: `chatSocket.js:347-348`
- **问题**: `flushQueue()` 在任何 `success` 响应时触发，可能发生在 login 响应之前。
- **修复**: 增加 `flushed` 标记，确保 login 成功后只 flush 一次。

### 5. `transport.send()` 返回的 Promise 未处理
- **文件**: `WebTransportTransport.js:81`, `chatSocket.js:191`
- **问题**: `streamWriter.write(data)` 返回的 Promise 未被 await/catch，写失败静默丢弃。
- **修复**: 捕获并调用 `_onerror`。

### 6. 多 catch(() => {}) 吞掉所有错误
- **文件**: `chatSocket.js:408`, `ChatWindow.vue:326,411,440`
- **问题**: `.catch(() => {})` 忽略所有异常，调试困难。
- **修复**: 至少 `console.warn` 记录错误。

### 7. Vue reactive 状态直接 splice
- **文件**: `ChatWindow.vue:506-508`
- **问题**: `store.state.messages.splice(idx, 1)` 绕过 `readonly` 包装，Vue 不追踪。
- **修复**: 通过 store 的 mutation 方法删除消息。

## 🔵 Low Priority

- `loadHistory` 死函数清理
- 去重 `initials()` 函数
- 生产环境 console.log 剥离
- `formatText` XSS 防护增强（DOMPurify）
- protobuf 加载失败重试机制
