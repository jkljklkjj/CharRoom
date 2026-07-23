# 设备同步修复设计

## 背景

后端已有完善的 per-device sync cursor 机制：
- `DeviceSyncService` 存储每个设备的 sync cursor
- `SyncController` 的 `POST /sync/messages` 支持 `deviceType` 参数
- 每个设备独立追踪已读位置

但前端 sync API 没有正确传递 `deviceType`，导致 per-device cursor 不工作。

## 问题

1. `api/index.js` 的 sync API 读取 `charroom_deviceType`，但这个 key 从未被写入
2. `getDeviceType()` 硬编码返回 `'web'`，没有区分移动端/桌面端浏览器
3. sync 请求没有传递 `deviceType` 参数

## 修复方案

### 1. 统一 deviceType 来源

在 `utils/device.js` 中：
- `getDeviceType()` 根据 User-Agent 检测移动端/桌面端浏览器
- 返回 `'web'` 作为基础类型（web 前端统一用 web）

### 2. 修复 sync API

在 `api/index.js` 中：
- sync 请求从 `getDeviceType()` 获取 deviceType
- 传递给后端 `POST /sync/messages` 的 `deviceType` 参数

### 3. 登录时存储 deviceType

在 `chatSocket.js` 中：
- 登录成功后将 deviceType 存入 localStorage

## 影响范围

- `utils/device.js` - 添加设备类型检测
- `api/index.js` - 修复 sync API
- `chatSocket.js` - 登录时存储 deviceType

## 验证方式

1. 登录后检查 localStorage 中的 deviceType
2. 调用 sync API 检查请求体中是否包含正确的 deviceType
3. 在两个浏览器标签页登录，验证 sync cursor 独立工作
