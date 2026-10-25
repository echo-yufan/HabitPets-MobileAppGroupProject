# 成员 C 当前对外契约（2026-10-23）

验证：2026-10-23 应用和测试包编译通过；Pixel_2_API_33 上 38 项测试通过（原有 32 项、新增 6 项契约测试），0 失败；未提交、未推送。

本节取代下方历史记录中的旧接口表和“尚无 PetService”的说法。接口名字与六人交付说明第 6.3 节对齐；存储不重置，旧方法保留兼容。文档未规定所有 Java 参数类型、返回类型和包名，这里明确实际签名，组员接入仍需引用这些类型。

| 使用方 | Java 签名 | 状态 |
| --- | --- | --- |
| F | PetRepository.getPet(String userId) → Pet | 已实现，当前只接受 local_user；首次会初始化 |
| 数据层/业务流程 | PetRepository.updatePet(Pet pet) → void | 已实现，沿用身份校验与失败恢复；不建议页面直接覆盖整份快照 |
| A | PetService.addExp(int amount) → LevelUpResult | 已实现，拒绝负数，保留余数、连续升级，不负责任务去重 |
| E | PetService.changeMood(int delta) → Pet | 已实现，0–100 |
| E | PetService.changeHunger(int delta) → Pet | 已实现；按原文“喂食后 Hunger 增加”，越高越饱 |
| E | PetService.equipAppearance(String itemId) → Pet | 仅签名预留；合法 ID 也抛 UnsupportedOperationException，不能调用后扣物品或当作装备成功 |
| E 扩展 | PetService.changeStatus(int moodDelta, int hungerDelta) → Pet | 同时影响两项时用此入口，一次业务读取和保存 |

类型位置：
- com.example.pet.feature.pet.domain.service.PetService
- com.example.pet.feature.pet.data.repository.PetRepository
- com.example.pet.feature.pet.domain.model.LevelUpResult
- com.example.pet.core.model.pet.Pet
- 宿主仍用 com.example.pet.feature.pet.di.PetRepositoryOwner 提供统一仓库。

后台调用示例：

~~~java
PetRepository repository =
        ((PetRepositoryOwner) application).getPetRepository();
PetService service = new PetService(repository);
AppExecutors.disk().execute(() -> {
    try {
        Pet pet = repository.getPet("local_user");  // F：读取
        // 以下是分别供 A/E 使用的独立示例，不要在首页查询时一起执行：
        // LevelUpResult result = service.addExp(rewardExp);
        // Pet updated = service.changeMood(5);
        // Pet updated = service.changeHunger(20);
        // Pet updated = service.changeStatus(5, 20);
        // 调用方回主线程向自己的 ViewModel 发布结果。
    } catch (RuntimeException error) {
        // 报告失败；不能无条件重复发奖或消耗物品。
    }
});
~~~

线程与反馈：以上都是同步后台方法，不自动调度也不自动弹窗。现有 PetViewModel 页面方法继续提供异步封装，保存成功后更新状态。升级反馈由调用方消费 LevelUpResult；不提供跨页面广播。其他模块的 Gradle 依赖/宿主注入仍需对齐。

数据兼容：
- hunger 与 satiety 数值相同，不做 100-x 反转。Pet 新增 getHunger()，getSatiety() 和原 satiety 存储键保留。
- 目前只有本地单宠物，getPet 对 null、空串、其他用户明确抛 IllegalArgumentException，不忽略 userId 冒充多用户隔离。local_user 来源于项目已有 Task 默认值；F 若要多用户需另行迁移数据。
- updatePet 继续校验同一宠物身份；修改心情/经验优先通过 PetService，避免调用方拿旧快照覆盖其他改动。
- PetService 内部复用 AddPetExp、ChangePetStatus，不维护新数据。内部用例改用 getPet/updatePet；旧 getOrCreate/save 作为兼容层保留，原初始化和保存失败恢复逻辑不变。
- 装备仍缺 D/E 商品映射及持久化装备模型；此次只对齐名称与参数，不伪造装备实现，Feed/Dress Up 继续禁用。

本轮交付清单新增：
- feature/pet/src/main/java/com/example/pet/feature/pet/domain/service/PetService.java
- feature/pet/src/androidTest/java/com/example/pet/feature/pet/PetContractTest.java
并更新仓库接口、三个内部用例、共享 Pet 的读取别名及本说明。没有更改 A/D/E/F 模块业务、旧数据文件或 UI。

---

以下为原实现说明与历史检查记录；其中旧类名示例仍可兼容运行，但新组员接入优先使用上方 PetService/getPet/updatePet 契约。

# 牛来：本地数据读写

## 当前实现

首次打开宠物页时创建唯一记录：牛来、cow_brown、等级 1、经验 0、心情 80、饱腹度 80。之后从记录读取，不用默认值覆盖已有状态。当前是一台设备上的单宠物模式，已实现经验增加和升级接口，未接入账号隔离、任务奖励触发、喂食或背包。

## 阅读顺序

1. `core/model/.../pet/Pet.java`：宠物信息卡。不可变对象，检查数值范围，经验暂按已有 100 点进度条约定；不执行升级。
2. `feature/pet/.../data/repository/PetRepository.java`：读写约定。`getOrCreate()` 读取或首次初始化，`save(Pet)` 保存同一身份的完整快照。
3. `feature/pet/.../data/local/LocalPetRepository.java`：实际保存。使用应用私有 SharedPreferences 文件 `habitpets_pet`，全部字段一次 commit；失败抛异常。残缺或未知版本记录报错，不静默重建。
4. `app/.../PetApplication.java` 和 `feature/pet/.../di/PetRepositoryOwner.java`：全 App 共用一个宠物仓库。不要在其他页面另建一份默认宠物。
5. `GetPetStatus` → `PetViewModel` → `PetUiState`：用小组现有 `AppExecutors.disk()` 后台队列读取，再回主线程发布加载、成功或失败状态。
6. `PetViewModelFactory`：从 Application 获取同一个仓库，组装页面所需对象。
7. `PetActivity`：每次页面开始显示时读取数据，观察状态并更新名字、等级、文字及进度条。失败时点击提示重试。布局继续使用原有奶牛和样式。

页面调用链：PetActivity.onStart → PetViewModel.load → 后台 GetPetStatus.execute → 仓库 getOrCreate → 主线程 LiveData → PetActivity.render。

## 为什么选择此存储方式

项目任务模块用 SQLite，聊天模块用 SharedPreferences；本阶段只保存一条独立的宠物记录，沿用聊天模块的轻量本地存储和宿主组装方式，不改动任务数据库。清除 App 数据或卸载会清除本地记录，不是云端同步。

## 后续接入约定

所有仓库方法应在后台调用。更新时先取得当前记录，再构造保留原 id 的 Pet 并保存；实际页面目前没有修改数值的测试按钮。调用方必须处理保存异常，不能假报成功。后续的奖励去重、撤销和背包扣减需要另外统一设计；当前保存接口不代表跨金币/物品/宠物的事务已经实现。

## 验证

`feature/pet/src/androidTest/.../LocalPetRepositoryTest.java` 在独立随机命名的测试存储文件中验证：首次默认值、重复读取保留身份和修改后的数值、磁盘 XML 确实写入新值、残缺记录不被覆盖。测试结束清理自己的文件，不触碰真实宠物记录。

运行：`gradlew.bat :app:assembleDebug :feature:pet:connectedDebugAndroidTest`，需连接模拟器或测试设备。

手动查看：运行 App → 宠物展示，确认英文名字 NiuLai、当前数值及“Pet data saved on this device”；返回首页再进入、关闭后重开，确认仍可读取。经验操作由下面的接口提供，目前没有领取经验按钮。
## 经验与升级：从请求到页面

1. 主线程调用 PetViewModel.addExperience(amount)。它把每笔非负奖励交给现有 AppExecutors.disk() 串行后台队列，不在界面线程读写磁盘。
2. AddPetExp.execute(amount) 从统一 PetRepository 读取当前宠物，在同一个仓库锁内计算并保存。升级数 = (原经验 + 奖励) / 100，剩余经验 = (原经验 + 奖励) % 100。例如 Lv.1、90 EXP 加 220，得到 Lv.4、10 EXP。
3. 新 Pet 保留原来的身份、名字、外观、心情和饱腹度。负数在读写之前拒绝，零奖励不写入；使用 long 中间计算并拒绝等级超过 int 上限。
4. 仓库保存成功后，AddPetExp 才返回 LevelUpResult（新宠物和升级数）。PetViewModel 回主线程发布状态，PetActivity 更新原来的英文标签和进度条。
5. 升级时 PetEvent 传递一次性提示，页面显示 “Level up! NiuLai reached Lv.4!”。消费过的提示不会因页面重建再次显示。保存失败发英文错误提示，不发布新的宠物界面状态。
6. PetViewModelFactory 使用 Application 提供的同一个仓库组装读取和增加经验两个用例。以后接入任务时，应复用此数据来源，并另行解决奖励去重；当前每次有效调用都代表一笔新的奖励。

文件阅读顺序：domain/usecase/AddPetExp.java → domain/model/LevelUpResult.java → presentation/PetViewModel.java → presentation/PetEvent.java → ui/PetActivity.java。英文提示在 res/values/strings.xml；布局及其他成员模块本次不需要调整。

## 成长验证

PetGrowthTest 使用替身仓库检查未升级、恰好升级、连续升级、零奖励、负数、超大经验、等级溢出、保存失败，以及受控后台队列完成之前不刷新、成功后刷新、提示只消费一次、加载期间多笔奖励不会被丢弃。受控队列用于确定事件顺序，并非实际磁盘线程性能测试。
LocalPetRepositoryTest 新增真实 SharedPreferences 升级后重读及磁盘 XML 检查。所有仪器测试使用测试进程中的独立数据，不改动用户实际宠物。

运行：gradlew.bat :app:assembleDebug :feature:pet:connectedDebugAndroidTest。当前尚未连接任务或增加操作按钮，因此正常浏览页面不会自动获得经验。
## 异常处理与页面恢复（2026-10-07）

本轮只修改宠物模块和本说明，保留所有已有改动。没有新增奖励入口或其他功能。

- PetUiState：加载及读取错误状态可以携带上一次成功的宠物数据。页面重新开始读取时，等级和进度条不会先消失；读取失败显示原有英文重试提示，点击后重新读取，不重新发奖励。
- PetViewModel：刷新时保留上次成功状态；若首次读到数据之前经验操作失败，结束加载并进入可重试错误状态。已有数据显示时，保存失败仍保留旧数值，并发出原有英文错误提示。
- LocalPetRepository：SharedPreferences 提交失败可能已经改变内存快照。仓库记住提交前的记录，下次读取/保存前先尝试恢复；恢复提交也失败则继续报错，不把未确认的数值作为成功结果。首次初始化失败则恢复到无记录，再重新初始化。
- PetRecoveryTest：新增 7 项仪器测试。用受控队列验证首次读取失败后重试、重复加载不重复入队、刷新/刷新失败保留旧值、首次经验操作失败结束加载、生命周期观察者替换与暂停恢复时提示仅消费一次、页面关闭后忽略旧回调并能重新读取已保存结果；用故障注入验证失败提交后恢复及失败初始化重试。
- 仓库恢复标记属于 Application 持有的同一个仓库实例，所有调用方必须复用它。恢复逻辑不等于跨进程事务；应用进程重启时仍以 SharedPreferences 实际磁盘记录为准。

验证结果：修复前新增测试中 4 项失败；修复后 :app:assembleDebug 和 :feature:pet:connectedDebugAndroidTest 成功，Pixel_2_API_33 上全部 22 项测试通过（新增 7 项、原有 15 项）。测试使用独立数据，未修改用户宠物。

验证边界：生命周期测试使用 LifecycleRegistry 模拟观察者销毁、替换、暂停和恢复，没有驱动真实 Activity 屏幕旋转；故障测试模拟 commit 返回失败，不会故意占满设备磁盘。尚未目视验证真实旋转后的布局或 Toast。
手动检查：运行新版本 → 首页进入宠物页 → 返回再进入 → 旋转屏幕，确认 NiuLai、等级和三项数值正确，页面不会因刷新先清空已有数据。当前没有经验操作按钮，日常浏览不应出现升级提示；升级提示的消费规则由自动化测试验证。
## 给成员 E：心情和饱腹度接口（2026-10-08）

范围：只修改宠物状态，不扣食物、不扣金币、不装备服装、不自动衰减。satiety 是饱腹度，越高越饱；两个字段范围均为 0–100。参数是增量，允许正数、负数和零，超界截断，整数极值不会溢出。

### 在现有宠物页面接入

从 PetViewModelFactory 获取该页面的 PetViewModel，主线程调用：

~~~java
// 同一互动同时影响两项：只调用一次，不拆成两笔。
model.changeStatus(5, 20); // 心情 +5，饱腹度 +20
model.changeMood(-10);    // 仅心情 -10
model.changeSatiety(15);  // 仅饱腹度 +15
~~~

这三个方法异步排队，无同步成功返回值。现有 PetActivity 已观察 getState()：成功保存后，两项数字和进度条一起刷新。保存失败保留上次显示值，getEvents() 发 statusUpdateFailed 事件，页面显示英文错误提示。不能在调用方法之后立即显示“成功”。本阶段没有成功 Toast，也不会误发升级提示。

调用链：PetViewModel.changeStatus → AppExecutors.disk 后台队列 → ChangePetStatus.execute → 统一 PetRepository → 保存成功 → 主线程 PetUiState → PetActivity.render。

### E 有独立业务流程时

通过 Application 的 PetRepositoryOwner 获取同一个仓库，再构造 ChangePetStatus。不要新建 LocalPetRepository 或另一套 SharedPreferences。
以下是给 E 的后台业务接入示例（未接入按钮）：

~~~java
PetRepository repository =
        ((PetRepositoryOwner) application).getPetRepository();
ChangePetStatus change = new ChangePetStatus(repository);
AppExecutors.disk().execute(() -> {
    try {
        Pet saved = change.execute(5, 20);
        // 到这里才表示保存成功。
        // 由 E 的 ViewModel 回到主线程发布结果/错误，禁止在此直接操作 View。
    } catch (RuntimeException error) {
        // 失败必须反馈，不可当作互动成功；也不要盲目重复调用增量操作。
    }
});
~~~

ChangePetStatus 也提供同步后台方法 changeMood(delta)、changeSatiety(delta)，返回已保存的 Pet。同步方法不会自行切线程；页面使用上面的 PetViewModel 异步入口最直接。独立 E 页面修改后，宠物页 onStart 会重新读取；本接口不提供跨页面自动广播。

### 一次互动如何保存

业务用例只 getOrCreate 一次，使用同一份 Pet 计算两项，调用 save 一次；不会把两项分开写入。现有 LocalPetRepository.save 内部仍读取旧记录用于身份校验和保存失败恢复，这不是第二笔状态修改；字段一次 commit。首次无记录时仍沿用原初始化流程。
身份、名字、外观、等级、经验全部保留。状态更新与经验升级使用同一个仓库锁，页面请求进入同一串行队列，避免互相覆盖。
这仅保证宠物两项一起保存，不代表背包消费和宠物保存已有跨仓库事务。E/D 后续需要共同设计物品扣减失败与状态更新失败的处理。增量调用本身不去重，每次调用代表一笔新操作。

### 文件作用与测试

- domain/usecase/ChangePetStatus.java：计算两项并提交，是给 E 的业务入口。
- presentation/PetViewModel.java：后台调度，保存后更新页面状态。
- presentation/PetViewModelFactory.java：把同一个仓库组装到读取、经验、状态三个用例。
- presentation/PetEvent.java、ui/PetActivity.java、res/values/strings.xml：传递并显示专用英文失败提示。
- PetStatusTest.java：正常增减、上下限、极端增量、零增量、单项接口、失败不返回成功、页面保存前后状态、成长与状态连续更新、真实后台读写/主线程通知、保存后重读及磁盘字段验证。

测试使用独立记录，未改变真实牛来数据。Feed、Dress Up 按钮保持禁用，没有增加 UI 操作入口。
本轮验证结果：应用编译通过；Pixel_2_API_33 上全部 32 项仪器测试通过（新增 10 项状态接口测试，原有 22 项回归测试）。git diff --check 通过。

## 集成前检查（2026-10-21）

当前分支 feature/member-c-pet-growth，HEAD 为 5b2350d；暂存区为空。本轮只更新此文档，不改业务代码、不删除、不暂存、不提交、不推送、不 fetch/pull 或合并。结论只适用于本地，其他成员分支进度未验证。

### 建议提交与排除清单

宠物模块实际修改/新增文件如下（相对仓库根目录；不是要求整目录无差别暂存）：

- feature/pet/build.gradle
- feature/pet/src/androidTest/java/com/example/pet/feature/pet/LocalPetRepositoryTest.java
- feature/pet/src/androidTest/java/com/example/pet/feature/pet/PetGrowthTest.java
- feature/pet/src/androidTest/java/com/example/pet/feature/pet/PetRecoveryTest.java
- feature/pet/src/androidTest/java/com/example/pet/feature/pet/PetStatusTest.java
- feature/pet/src/main/AndroidManifest.xml
- feature/pet/src/main/java/com/example/pet/feature/pet/data/local/LocalPetRepository.java
- feature/pet/src/main/java/com/example/pet/feature/pet/data/repository/PetRepository.java
- feature/pet/src/main/java/com/example/pet/feature/pet/di/PetRepositoryOwner.java
- feature/pet/src/main/java/com/example/pet/feature/pet/domain/model/LevelUpResult.java
- feature/pet/src/main/java/com/example/pet/feature/pet/domain/usecase/AddPetExp.java
- feature/pet/src/main/java/com/example/pet/feature/pet/domain/usecase/ChangePetStatus.java
- feature/pet/src/main/java/com/example/pet/feature/pet/domain/usecase/GetPetStatus.java
- feature/pet/src/main/java/com/example/pet/feature/pet/presentation/PetEvent.java
- feature/pet/src/main/java/com/example/pet/feature/pet/presentation/PetUiState.java
- feature/pet/src/main/java/com/example/pet/feature/pet/presentation/PetViewModel.java
- feature/pet/src/main/java/com/example/pet/feature/pet/presentation/PetViewModelFactory.java
- feature/pet/src/main/java/com/example/pet/feature/pet/ui/PetActivity.java
- feature/pet/src/main/res/drawable-nodpi/pet_cow_default.png
- feature/pet/src/main/res/drawable/pet_dress_background.xml
- feature/pet/src/main/res/drawable/pet_feed_background.xml
- feature/pet/src/main/res/drawable/pet_progress.xml
- feature/pet/src/main/res/drawable/pet_scene_background.xml
- feature/pet/src/main/res/drawable/pet_white_background.xml
- feature/pet/src/main/res/layout/activity_pet.xml
- feature/pet/src/main/res/values/strings.xml

共享模型与说明：

- core/model/src/main/java/com/example/pet/core/model/pet/Pet.java：共享宠物类型。
- docs/PET_DATA.md：接口和验证说明。

必要公共入口修改（与维护宿主的同学核对后合并，保留对方新改动）：
- settings.gradle：注册 :feature:pet。
- app/build.gradle：依赖宠物模块。
- app/src/main/java/com/example/pet/app/MainActivity.java：增加宠物首页入口。
- app/src/main/java/com/example/pet/app/PetApplication.java：统一提供宠物仓库，不替换原有任务/聊天仓库。

建议本次不提交，保留本地：
- .idea/gradle.xml：IDE 记录新增模块；真正的模块注册已在 settings.gradle。
- .idea/vcs.xml：移除父目录 Git 映射，属于本地路径配置调整。
- .idea/.name：IDE 项目显示名。
- .gradle/、各模块 build/、local.properties、APK、日志：已有忽略规则，不需要上传。
git ls-files -ci --exclude-standard 未发现已跟踪且命中忽略规则的文件。本轮不改 .gitignore，也不删除或还原任何 IDE 文件。

### 任务完成链路及奖励接入缺口

实际链路：MemoActivity.toggleComplete → MemoViewModel.setCompleted → 后台 TaskRepository.setCompleted → LocalTaskRepository.setCompleted/updateTask → 提醒更新 → 列表刷新。

TaskDbHelper 当前只有 tasks 表，有 taskId、userId、completed、completedAt、rewardExp、rewardCoins。未找到独立 HabitRecord、recordId、CompletionService/CompletionResult 或持久化奖励发放记录。

- 完成记录：setCompleted 返回 void，只覆盖当前状态；重复 true 更新完成时间，false 清空完成时间，没有“本次新完成/以前已完成”结果。
- 重复领奖：页面 busy 只阻止正在执行时重复点击；不等于持久化去重。C 的 AddPetExp 每次正数调用都会累加，没有 recordId/source 参数，无法独立保证同任务同一天只领奖一次。
- 取消完成：当前只改标记和提醒，没有撤销经验。C 拒绝负数 EXP，不能传负奖励充当撤销接口。是否扣回、再次完成是否可领奖须 A/C/D 确认。
- 失败重试：任务 SQLite、宠物 SharedPreferences 是不同存储，没有联合事务或奖励流水。任务成功但宠物失败、宠物成功但金币失败、保存成功但列表刷新失败，都需要可区分，不能盲目重做整条链。
- 特别注意：任务可能已保存，后续提醒更新却失败；这时 MemoViewModel 的 commandCompleted 仍可能为 false，并显示“任务操作失败，请重试”。页面错误提示不能证明任务没有保存，也不能直接作为发奖依据。本轮只记录，不修改。
- 默认任务奖励为 10 EXP、5 Coins，规划生成的任务可以有不同数值；应使用实际 rewardExp，不新定固定奖励值。
- feature/planning/build.gradle 尚不依赖 :feature:pet。直接在任务页面复制 AddPetExp 调用并不能完成集成，还需确定宿主注入奖励协调器或共享契约等依赖安排，避免 core:data 反向依赖功能模块。

### A/E/F 接口核对

统一来源：((PetRepositoryOwner) application).getPetRepository()，由 Application 提供同一个仓库，不另建 LocalPetRepository 或 SharedPreferences。

| 使用方 | 实际业务入口 | 返回与限制 |
| --- | --- | --- |
| A | new AddPetExp(repository).execute(amount) | 后台同步；返回 LevelUpResult，其 getPet()/getLevelsGained() 提供结果；负数拒绝，0 不奖励，不去重 |
| E | new ChangePetStatus(repository).execute(moodDelta, satietyDelta) | 后台同步；两项一起保存后返回 Pet；正负增量，截断至 0–100 |
| E | ChangePetStatus.changeMood(delta)/changeSatiety(delta) | 单项便捷方法；satiety 越高越饱 |
| F | new GetPetStatus(repository).execute() | 后台同步返回 Pet；无记录会初始化，所以不是绝对无写入的查询 |
| 宠物页面 | PetViewModel.load()/addExperience()/changeStatus() | 主线程调用、内部后台执行；观察 getState()/getEvents()，不能把方法返回当成成功 |

A/F 示例（两种独立操作，不是要求先发奖励再查询）：

~~~java
PetRepository repository =
        ((PetRepositoryOwner) application).getPetRepository();
// A 的后台奖励流程：仅在已确认本次应发奖后执行。
LevelUpResult result = new AddPetExp(repository).execute(rewardExp);
// F 的后台查询流程：
Pet summary = new GetPetStatus(repository).execute();
~~~

同步业务方法不会自行切线程。调用方使用 AppExecutors.disk()，捕获 RuntimeException，并回主线程向自己的 ViewModel/页面发布成功或错误。E 的完整示例见前文。
类位于 feature:pet，Pet 位于 core:model，AppExecutors 位于 core:common；接入模块需要相应编译依赖或经宿主组装的契约，本轮不改变依赖关系。

需要明确的边界：
- 直接调用 AddPetExp 不自动显示 Toast；当前升级提示来自 PetViewModel.addExperience 的事件。A 在自己的流程发奖时应处理 LevelUpResult 显示反馈，或另行约定通知方式。
- 宠物页 onStart 重新读取；没有跨页面实时广播。F 应在其刷新时机读取，不维护第二份等级/经验。
- 存储名字为“牛来”，宠物页用英文资源显示 NiuLai。F 如需一致英文展示，应明确显示映射，不通过覆盖宠物存储来改名。
- Pet 暂无 userId，Task 默认 local_user；是否保持本地单用户需 F 确认。
- 实际代码没有 PetService、getPet(userId)、updatePet 或 equipAppearance 方法；以本表真实接口为准。
- FeedPet 只有注释，Feed/Dress Up 仍禁用；未实现服装装备，也没有背包扣减与宠物更新的跨仓库事务。

### 需要组员提供的内容

- A：任务功能的实际分支/提交、完成事件或返回结果、唯一完成记录标识及业务日期口径；奖励数值来源、重复领奖、取消完成和失败重试约定。
- A/C/D：奖励协调入口、去重记录归属、EXP/Coins 部分成功如何恢复。上述事项均待协作决定，本轮不制定规则。
- E：互动页面与文件修改分工、心情/饱腹度增量含义、状态已满时如何展示，避免重复调用增量接口。
- D/E：商品 ID、食物效果、背包消费和装备契约、服装素材与装备数据；扣物品和改状态的失败处理。
- F：宿主入口修改、用户约定、首页刷新方式和英文名字显示。

### 可以独立继续的工作

人工检查返回、重新进入、重启、旋转及大字号显示；整理宠物素材说明和短 Demo 操作稿；熟悉接口和测试结果。可以准备功能提交，但不能把本次检查理解为提交授权。装备契约未确定前不自行设计商城数据，去重未明确前不直接连接任务完成和经验累加。

### 本轮验证结果

2026-10-21：使用 Android Studio 自带 JDK 运行 :app:assembleDebug 和 :feature:pet:assembleDebugAndroidTest，均编译成功。启动本机已有 Pixel_2_API_33 模拟器后，运行 :feature:pet:connectedDebugAndroidTest，32 项全部通过，0 失败、0 错误；git diff --check 通过（仅有换行符提示）。暂存区仍为空。

本次未进行真实屏幕旋转、布局和 Toast 的目视检查，也未验证 A→C/D 奖励、E→D/C 喂养或 F 首页汇总的完整用户流程，因为相应接入尚未实现。不能把 32 项宠物模块测试通过视为全组集成已通过。所有测试使用独立测试数据。