---
name: charroom-client-check
description: 验证轻聊 CharRoom 的 Web、桌面/CLI、Android 改动，定位构建前置条件并选择最小跨端回归。用于实现后的验证、构建失败或联调准备，不用于自动发布安装包。
---

# 客户端验证

先定位 CharRoom 并读 AGENTS；Web 读 web/AGENTS。若当前是聚合工作区，目录是 `CharRoom`，不是 `chatRoom`。记录 `git status --short`，保护用户已有改动。

## 先检查前置条件

从本 skill 路径定位并运行 `scripts/doctor.py`，不要把脚本相对路径当成项目相对路径：

```text
python <skill-directory>/scripts/doctor.py --client <CharRoom-directory> --target web
python <skill-directory>/scripts/doctor.py --client <CharRoom-directory> --target desktop
python <skill-directory>/scripts/doctor.py --client <CharRoom-directory> --target android --json
```

脚本只读文件并运行有限的版本查询，不安装依赖、不启动应用或容器、不恢复 Wrapper。返回 0 表示所检查前置条件满足，1 表示有阻断，2 表示参数/文件读取异常；WARN 不阻断，版本可见不等于构建或服务可用。

## 按改动选命令

| 涉及改动 | 工作目录与检查 | 需要额外证明 |
| --- | --- | --- |
| Web imports/store/UI | web：`npm ci`（依赖缺失时）、`npm run build` | build 会生成 public 资源，事后审查 diff；登录/消息行为需浏览器验证 |
| 共享 Kotlin / 桌面 / CLI | CharRoom：Wrapper `compileKotlin`；有相应测试才选 `test` | 根插件是 JVM，共享源码目录不代表 JS/iOS target；CLI 行为另测 |
| Android | CharRoom：Wrapper `:androidApp:assembleDebug` | 显式模块任务触发 include；根 customApk 别名可能未 include；设备收发需另测 |
| 协议 | 两仓库协议生成 + 实际编解码夹具 | 文本一致、能编译、能互通分别验收；不要仅改 public 副本 |
| 重连/认证/存储 | fake transport/IndexedDB 测试 + 受影响平台联调 | ACK、outbox、账号 A→B、token 失效、断网恢复 |

使用 Windows 的 `./gradlew.bat` 或 Unix 的 `./gradlew`。Wrapper 已被用户删除时报告阻断，不擅自还原。未声明 test/lint 脚本、Gradle NO-SOURCE 均不算测试通过。

启动前核对 REST 与 QUIC 各自目标，二者不是同一个配置；优先本地测试地址。若已获授权做联调则继续，不要求重复确认，但不因为“验证”自动触发签名、发版、线上消息发送或付费模型调用。

## 交付

按 Web / Desktop-CLI / Android 列出通过、失败、未运行及原因；给出实际命令、首个有效错误和影响范围。不要把某一端通过推广成三端可用。
