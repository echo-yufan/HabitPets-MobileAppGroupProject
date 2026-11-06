# 开发与验证

## 环境

- Android Studio，Gradle JDK 11。
- Java 源码级别 8；Gradle Wrapper 7.4、Android Gradle Plugin 7.3.1。
- compileSdk / targetSdk / minSdk 均为 33；设备或模拟器需 Android 13 / API 33 及以上。
- 从仓库根目录使用自带 gradlew / gradlew.bat，不依赖全局 Gradle 安装。

## 本机配置

根目录 local.properties 不提交 Git。Android Studio 会维护 sdk.dir；可加入 AI 服务配置：

```properties
sdk.dir=C:/Path/To/Android/Sdk
habitpets.llm.apiKey=YOUR_OWN_API_KEY
```

也可通过环境变量 HABITPETS_LLM_API_KEY 配置，环境变量优先。core:ai 在构建时将该值写入自身 BuildConfig；不要把真实密钥写回 Java、Gradle、README 或示例文件。当前服务地址和模型保持现有 LLMClient 配置。未配置时，纯本地功能仍可使用，AI 请求走既有失败反馈。

联网客户端测试使用本地 MockWebServer 和测试凭据，不应为验证构建而调用真实付费服务。

## 构建与运行

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleDebugAndroidTest
.\gradlew.bat :app:installDebug
```

第一次同步依赖需要网络；依赖已缓存时可加 --offline。默认 APK 在 app/build/outputs/apk/debug，报告和构建中间文件留在各模块 build 下，均不提交。

## 自动验证

当前整合版本已通过 Debug 构建、55 项单元测试和 134 项 API 33 设备测试；pet/shop Lint 为 0 errors，仍有既有依赖版本、未使用资源等 warnings。测试结果保留在本机构建目录，仓库不收录过程报告或截图。

```powershell
# 状态机、提醒时间策略、聊天历史与任务界面模型
.\gradlew.bat :app:testDebugUnitTest :feature:planning:testDebugUnitTest :feature:chat:testDebugUnitTest

# 真实数据库：互动/奖励恢复、宠物仓库、商城交易
.\gradlew.bat :feature:interaction:connectedDebugAndroidTest :feature:pet:connectedDebugAndroidTest :feature:shop:connectedDebugAndroidTest

# 界面、提醒通知、聊天网络异常与跨模块流程
.\gradlew.bat :app:connectedDebugAndroidTest

# 静态检查
.\gradlew.bat :feature:pet:lintDebug :feature:shop:lintDebug
```

使用独立测试模拟器。通知导航用例要求提前允许通知权限；例如在安装后执行 adb shell pm grant com.example.pet.cd android.permission.POST_NOTIFICATIONS。部分测试会临时创建任务、注入数据库故障或切换通知权限，不宜在存有个人数据的正式设备上运行整套测试。

界面用例会把临时验证截图写到测试设备的应用外部目录；这些是测试输出，不属于源码交付，也不应复制进 docs。

## 手动检查重点

1. 完成新任务，确认金币、经验、升级以及取消后重做不重复发奖。
2. 在商城购买食物和服装，回首页操作，确认数量、状态与穿搭即时更新。
3. 同时穿帽子、围巾、身体服装；替换或脱下一部分时保留其他部分。
4. 抽屉打开前后背景、宠物位置不跳动；返回与页面重建正确恢复。
5. 任务编辑、提醒通知、返回栈和聊天失败重试保持正常。

## 提交范围

提交运行资源、Java/XML 源码、测试、Gradle Wrapper 和必要文档。个人 IDE 配置、本地 SDK/密钥、缓存、APK、测试报告、开发截图和过程日志都不进入版本控制。
