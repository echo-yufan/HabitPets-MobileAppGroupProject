# HabitPets · 牛来

用完成日常任务获得的金币与经验照顾虚拟宠物“牛来”，把任务管理与宠物成长联系起来的 Android 小组项目。采用 Java / XML、Android SDK 33，无注册登录；任务、宠物、钱包与库存保存在本机，AI 聊天和任务拆解需要联网。

## 当前功能

- 备忘录任务的新增、编辑、筛选、完成、删除，以及截止时间和本地通知提醒。
- 任务首次完成发放对应金币与经验；默认 5 金币、10 经验，取消后重做不会重复领奖，每 100 经验升级。
- 首页草原场景、CityU 木牌、宠物点击反馈、等级/经验/心情/饱腹度与钱包展示。
- 紧凑的喂食、换装抽屉，操作后实时更新；头部、颈部、身体各一件，可同时搭配。
- 商城购买、等级限制、背包数量、食物消耗及本地保存。
- 与牛来聊天、失败重试、对话恢复，以及 AI 辅助拆解任务。
- 任务奖励和宠物互动有去重记录与失败恢复，避免重复发奖或扣库存。

## 开始开发

使用 JDK 11，安装 Android SDK Platform 33，在 Android Studio 中打开仓库根目录。配置本机 `local.properties` 的 `sdk.dir`；AI 密钥放在忽略的本地配置中，见 [开发指南](docs/DEVELOPMENT.md)。

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

APK 默认位于 `app/build/outputs/apk/debug/app-debug.apk`。最低 Android 13 / API 33，应用 ID 沿用 `com.example.pet.cd`，保证现有安装和本地数据兼容。

## 项目结构

```text
app/                   应用入口、依赖装配、跨模块导航和集成测试
core/model/            任务、宠物及互动/奖励契约
core/data/             任务 SQLite 存储与奖励待交付收据
core/common/           后台队列、时间与通用回调
core/ai/               LLM 网络客户端
feature/planning/      备忘录、任务拆解、提醒与通知
feature/chat/          牛来聊天、历史与请求状态
feature/pet/           宠物成长、首页、喂食换装界面
feature/shop/          商城、钱包、背包与经济数据库
feature/interaction/   跨任务/宠物/经济存储的协调与恢复
docs/                  当前架构、构建配置与测试指南
```

[当前架构](docs/ARCHITECTURE.md) · [开发与验证指南](docs/DEVELOPMENT.md)

## 当前边界

这是单设备、单宠物的本地版本，不包含账号、云同步、按日期生成的重复习惯记录或连续打卡奖励。历史已完成任务不会在升级时批量补奖；每个任务最多领取一次。AI 需要有效服务配置和网络，失败时保留现有错误/重试流程，任务拆解保留既有本地模板回退。

源码包含运行所需图片与测试代码；不将 APK、缓存、截图、开发日志、测试报告、个人 IDE 配置或密钥作为项目交付内容提交。
