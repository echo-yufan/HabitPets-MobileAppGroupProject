# 文件夹职责

## 项目目录

| 文件夹 | 职责 |
|---|---|
| app | 应用启动、主页和各功能的组装 |
| core | 多个功能共用的模型、数据访问和工具 |
| core/model | 共用的业务数据模型 |
| core/model 下的 task、pet、shop、user | 分别存放任务、宠物、商品与物品、用户资料的数据模型 |
| core/data | 共用数据库和数据访问 |
| core/data 下的 repository | 数据访问入口和仓库实现 |
| core/data 下的 local | 本地数据库管理 |
| core/data 下的 di | 提供共用数据访问所需的依赖 |
| core/common | 通用工具 |
| core/common 下的 time | 日期和时间工具 |
| core/common 下的 concurrent | 后台任务执行工具 |
| core/common 下的 callback | 共用回调定义 |
| core/ai | 共用的 AI 网络请求、超时和取消能力 |
| feature | 按业务功能组织代码 |
| feature/planning | 待办管理、任务拆解和提醒 |
| feature/chat | 聊天和会话管理 |
| feature/pet | 宠物展示、状态管理和喂养互动 |
| feature/shop | 商品展示、购买和物品发放 |
| feature/profile | 个人资料、功能摘要和设置入口 |
| docs | 项目说明文档 |
| gradle/wrapper | Gradle 构建工具的启动和版本管理 |
| .idea | Android Studio 项目配置 |
| .gradle | Gradle 构建缓存 |
| 各模块的 build | 编译生成的文件和构建结果 |

## 源码与资源目录

| 文件夹 | 职责 |
|---|---|
| src | 模块的源码、资源和测试 |
| src/main | 应用运行所需的源码和资源 |
| src/main/java | Java 源码 |
| java 下的 com/example/pet/... | 按 Java 包名组织代码，区分模块归属 |
| src/main/res | Android 界面资源 |
| res/layout | 页面和组件布局 |
| res/drawable | 图片、图标和背景样式 |
| res/mipmap 及 mipmap-* | 应用图标及不同分辨率的图标资源 |
| res/values | 文字、颜色、主题和尺寸等资源定义 |
| res/xml | 备份规则等 XML 配置资源 |
| src/test | 不需要 Android 设备的单元测试 |
| src/androidTest | 需要 Android 设备或模拟器的测试 |

## 功能内部目录

| 文件夹 | 职责 |
|---|---|
| ui | 页面展示和用户交互 |
| ui/renderer | 根据页面状态渲染界面 |
| presentation | 页面状态管理和用户操作协调 |
| domain | 业务模型和业务规则 |
| domain/model | 功能内部使用的业务数据模型 |
| domain/usecase | 查询、购买、喂养、提醒校验等业务操作和规则 |
| data | 数据获取、保存和转换 |
| data/repository | 数据访问入口，协调不同数据来源 |
| data/local | 本地数据存储和初始化 |
| data/ai | 功能所需的 AI 请求和结果处理 |
| di | 提供功能所需的依赖 |
| platform | Android 系统能力的接入 |
| platform/reminder | 闹钟调度、提醒触发和开机恢复 |
| platform/notification | 通知创建和展示 |
| app 源码下的 navigation | 各功能页面的跳转入口 |
