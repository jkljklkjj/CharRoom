# CLAUDE.md - CharRoom 客户端项目

## 项目概览

CharRoom — 跨平台即时通讯客户端（Kotlin Multiplatform + JetBrains Compose Desktop + Vue 3 Web）

## 项目结构

```
CharRoom/
├── src/
│   ├── commonMain/kotlin/        # 跨平台共享代码
│   │   ├── App.kt                应用入口
│   │   ├── Util.kt               通用工具
│   │   ├── component/            UI 组件（ChatScreen, LoginRegister, dialog 等）
│   │   ├── core/                 核心层（ApiClient, WebSocket, AppConfig 等）
│   │   ├── data/                 数据层（datasource, repository）
│   │   ├── model/                数据模型
│   │   └── presentation/         ViewModel
│   ├── desktopMain/kotlin/       # 桌面平台代码
│   │   ├── Main.kt               JVM 入口
│   │   ├── component/            平台相关 UI
│   │   └── core/                 平台核心实现
│   └── main/                     Android 资源
├── web/                          # Web 前端（Vue 3 + Vite + PWA）
│   ├── src/                      
│   ├── public/                   
│   ├── vite.config.js            
│   └── package.json              
├── androidApp/                   # Android 原生应用
├── proto/                        # Protobuf 定义（依赖后端 proto）
├── packaging/icons/              安装包图标
├── build.gradle.kts              根构建配置
├── settings.gradle.kts           Gradle 设置
└── gradle.properties             Gradle 属性
```

## 构建与运行

### 桌面端 (Compose Desktop)

| 命令 | 说明 |
|------|------|
| `./gradlew run` | 运行桌面应用 |
| `./gradlew customJar` | 打包 fat JAR |
| `./gradlew packageReleaseMsi` | 打包 Windows 安装包 |
| `./gradlew packageReleaseDmg` | 打包 macOS 安装包 |
| `./gradlew packageReleaseDeb` | 打包 Linux 安装包 |
| `./gradlew buildInstallers` | 构建所有安装包 |
| `./gradlew tasks` | 查看所有可用任务 |

### Web 前端

```bash
cd web
npm install        # 安装依赖
npm run dev        # 开发模式 (localhost:5173)
npm run build      # 生产构建
npm run preview    # 预览生产构建
```

#### 重构前端后部署

```bash
cd web
npm run build
# 重新打包并重启 Nginx 容器（dist 挂载为 volume）
docker compose -f ../chat-room/docker/nginx/docker-compose.yml up -d --build nginx-lb
```

- 构建产物输出到 `web/dist/`
- Nginx 容器通过 volume 挂载 `web/dist` 到 `/usr/share/nginx/html`
- `--build` 会重新构建镜像（只更新 volume 挂载时其实不需要，但加上了更保险）
- 如果只想重启 Nginx 而不重构建：`docker compose -f ../chat-room/docker/nginx/docker-compose.yml restart nginx-lb`

### Android

```bash
./gradlew customApkDebug     # Debug APK
./gradlew customApkRelease   # Release APK
./gradlew customApk          # Release APK (默认)
```

### 测试

```bash
./gradlew test
```

## 关键依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| Kotlin | 2.3.20 | 语言 |
| Kotlin Compose Plugin | 2.3.20 | Compose 编译器 |
| JetBrains Compose | 1.10.3 | 桌面 UI 框架 |
| Ktor Client | 3.4.3 | HTTP/WebSocket 客户端 |
| kotlinx-serialization | 1.11.0 | JSON 序列化 |
| kotlinx-coroutines | 1.10.2 | 协程 |
| Protobuf Java/Kotlin | 4.34.1 | Protobuf 编解码 |
| Netty | 4.2.5 | 网络底层（桌面端） |
| Koin | 4.2.0-RC1 | 依赖注入 |
| Kermit | 2.1.0 | 日志 |
| RichText | 1.0.0-alpha04 | Markdown 渲染 |

## 代码规范

- **DI** — 使用 Koin（`koin-core` + `koin-compose`）
- **日志** — 使用 Kermit 日志库
- **网络** — Ktor Client（CIO 引擎 + ContentNegotiation）
- **JSON** — kotlinx-serialization
- **UI** — JetBrains Compose Multiplatform（Material Design）

## Proto 文件同步

Compose Desktop 端从后端 `message.proto` 复制后编译：

```bash
# 复制 protobuf 定义到 proto 模块
cp ../chat-room/src/main/proto/message.proto proto/

# 重新生成 protobuf 代码
./gradlew :proto:generateProto
```

Web 端使用的 proto 文件位于 `web/public/proto/message.proto`（前端单独维护）。

## Java 版本

项目要求 **Java 21**。已配置：
- `jvmToolchain(21)` — Gradle 自动发现 JDK 21
- `jvmTarget = JVM_21` — 编译目标
- VS Code 配置指向 `/usr/lib/jvm/java-21-openjdk-amd64`
