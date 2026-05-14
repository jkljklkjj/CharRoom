# 项目架构规范

## 分层架构设计

采用经典的五层架构，依赖方向从外层向内层，遵循单一职责原则：

```
┌─────────────────────────────────────────┐
│              UI 层 (Presentation)       │  → Composable组件、UI状态
└─────────────┬───────────────────────────┘
              ↓ 依赖
┌─────────────────────────────────────────┐
│         ViewModel 层 (Presentation)     │  → 状态持有、事件处理、业务协调
└─────────────┬───────────────────────────┘
              ↓ 依赖
┌─────────────────────────────────────────┐
│          领域层 (Domain) [可选]          │  → 用例、业务实体、业务规则
└─────────────┬───────────────────────────┘
              ↓ 依赖
┌─────────────────────────────────────────┐
│          数据层 (Data)                   │  → Repository、数据转换
└─────────────┬───────────────────────────┘
              ↓ 依赖
┌─────────────────────────────────────────┐
│       数据源层 (Data Source)             │  → 远程数据源(API)、本地数据源(数据库、SP)
└─────────────────────────────────────────┘
```

## 各层职责

### 1. UI层 (`presentation/ui`)
**职责**：
- 只负责UI渲染，根据状态展示对应的界面
- 转发用户事件到ViewModel层
- 不包含任何业务逻辑
- 不直接调用API或访问数据库
- 不持有状态，只观察ViewModel暴露的状态

**包结构**：
- `presentation/ui/chat` - 聊天相关UI
- `presentation/ui/auth` - 登录注册相关UI
- `presentation/ui/profile` - 个人资料相关UI
- `presentation/ui/contacts` - 联系人相关UI
- `presentation/ui/common` - 公共UI组件

### 2. ViewModel层 (`presentation/viewmodel`)
**职责**：
- 持有UI状态，通过StateFlow暴露给UI层
- 处理UI层转发过来的用户事件
- 协调业务逻辑，调用Repository层的方法
- 不持有View引用，不包含任何UI相关代码
- 不直接调用API或访问数据库

**包结构**：
- `presentation/viewmodel/chat` - 聊天相关ViewModel
- `presentation/viewmodel/auth` - 登录注册相关ViewModel
- `presentation/viewmodel/profile` - 个人资料相关ViewModel
- `presentation/viewmodel/contacts` - 联系人相关ViewModel

### 3. 领域层 (`domain`) [可选，当前阶段可以先不实现]
**职责**：
- 定义业务实体（纯Kotlin类，不包含任何平台相关代码）
- 定义业务用例(Use Case)，封装单一场景的业务逻辑
- 定义Repository接口，数据层实现这些接口
- 包含核心业务规则，不依赖任何外层的代码

**包结构**：
- `domain/model` - 业务实体
- `domain/usecase` - 业务用例
- `domain/repository` - Repository接口
- `domain/exception` - 业务异常定义

### 4. 数据层 (`data`)
**职责**：
- 实现领域层定义的Repository接口
- 协调远程数据源和本地数据源
- 处理数据转换：DTO → 业务实体 → UI模型
- 处理数据缓存策略
- 不包含任何业务逻辑，只做数据相关操作

**包结构**：
- `data/repository` - Repository实现
- `data/mapper` - 数据转换映射器
- `data/datasource` - 数据源抽象和实现
  - `data/datasource/remote` - 远程数据源(API)
  - `data/datasource/local` - 本地数据源(数据库、SP)

### 5. 核心层 (`core`)
**职责**：
- 公共工具类、扩展函数
- 常量定义
- 公共异常定义
- 核心配置
- 平台无关的通用代码

**包结构**：
- `core/network` - 网络相关公共代码
- `core/state` - 状态相关公共代码
- `core/utils` - 工具类
- `core/extension` - 扩展函数
- `core/constant` - 常量定义

## 依赖规则

1. **内层不依赖外层**：领域层不依赖数据层，数据层不依赖ViewModel层，ViewModel层不依赖UI层
2. **外层只能依赖相邻内层**：UI层只能依赖ViewModel层，ViewModel层只能依赖领域层或数据层
3. **抽象不依赖实现**：内层定义接口，外层实现接口
4. **跨层通信必须通过中间层**：UI层不能直接调用API，必须通过ViewModel → Repository → DataSource

## 现有代码迁移计划

1. 先把现有代码按新的包结构重新组织
2. 逐步重构各层代码，移除跨层依赖
3. 保持API兼容，旧代码可以继续运行，逐步迁移
