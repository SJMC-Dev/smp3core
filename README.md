# SMP3 Core

Minecraft 26.3 / Fabric Loader 0.19.5 / Java 25 SMP3 核心模组，提供资源商店、每日签到、每日任务、通宝/咖啡印章经济以及性能相关工具。

## 安装与命令

服务器与玩家客户端安装同一个模组 JAR，并安装 Fabric API、Fuji。服务器上的玩家权限由 LuckPerms 提供，OP 等级 2 及以上仍可管理每日任务。部署前移除旧 Daily Quests（mod ID：`dailyquests`），本模组已声明冲突。

| 命令 | 用途 | 权限 |
| --- | --- | --- |
| `/smp3 dailyquest` (Alias `/smp3 dailyquests`) | 打开每日任务 | 所有玩家 |
| `/smp3 dailyquest <player> reroll [slot]` | 更换在线玩家指定槽位的任务；省略槽位则更换全部任务 | 控制台、OP 等级 2 及以上或 LuckPerms `smp.dailyquest.*` |
| `/smp3 dailyquest <player> reset [slot]` | 重置在线玩家指定槽位的任务状态；省略槽位则重置全部任务 | 控制台、OP 等级 2 及以上或 LuckPerms `smp.dailyquest.*` |
| `/smp3 sign` | 打开每日签到 | 所有玩家 |
| `/smp3 sign reload` | 热重载签到奖励规则（`config/smp3/sign.json`） | OP 等级 2 或控制台 |
| `/smp3 reward reload` | 热重载一些成就/称号等发放规则（`config/smp3/milestone-rewards.json`） | OP 等级 2 或控制台 |
| `/smp3 shop <player>` | 为指定玩家打开商店 | OP 等级 2 或控制台 |
| `/smp3 shop reload` | 热重载商店配置（`config/smp3/shop.json`） | OP 等级 2 或控制台 |
| `/smp3 info` | 打开公告栏 | 所有玩家 |
| `/smp3 info edit [post_id]` | 新建/编辑/删除特定的公告 | OP 等级 2 或控制台 |
| `/smp3 hud` | 拖动以自定义 SMP3 HUD 的位置 | 所有玩家 |
| `/smp3 cleanup [on / off]` | 获取或启用/禁用当前清理状态 | OP 等级 2 或控制台 |
| `/smp3 perf top [seconds] [ms-filter]` | 采样 ` <seconds>` 秒（默认 10），筛选对游戏刻时延贡献不少于 `<ms-filter>` ms/t 的项，打印资源开销前几名，显示位置选取 chunk 左上角的坐标； | OP 等级 2 或控制台 |
| `/smp3 perf reload` | 热重载清理功能相关配置（`config/smp3/watchdog.json`） | OP 等级 2 或控制台 |



注：由于 H 按键与 MiniHUD 冲突，用户可以进入按键绑定搜索 SMP3 字样自行配置按键；



## 文档

参见 Wiki



## TODOs

- [x] 根除了 DailyQuests 第三方 mod

- [x] 代码结构重构
- [x] 商店和时区逻辑修复
- [x] 签到和成就奖励

- [x] 每日任务需求

- [x] 商店可配置
- [x] 每日任务、商店 GUI 重构
- [x] 公告栏和游戏内发布
- [ ] 文档补充

- [ ] Gacha 合入



## 构建与测试

```bash
./gradlew build
./gradlew verifyDailyQuests
./gradlew runGameTest
./gradlew runClientGameTest
```

`build` 包含规则验证与 Fabric 服务端 GameTest。客户端 GameTest 在图形会话中单独运行。产物为 `build/libs/smp3-1.0.0.jar`。

Java 格式配置为 `.clang-format`，使用 clang-format 23；执行 `./gradlew checkJavaFormat` 检查，使用 `clang-format -i <文件>` 格式化编辑过的 Java 文件。

格式检查单独运行，常规构建无需安装格式工具；服务与 UI 的源码依赖检查随 `verifyDailyQuests` 和 `build` 执行。

| 目录 | 内容 |
| --- | --- |
| `src/main` | 服务端功能、公共消息与发布资源 |
| `src/client` | 客户端 HUD、动画状态与显示设置 |
| `src/test` | Java 原生断言：任务规则、经济恢复、HUD 快照及动画状态转换、模块依赖边界 |
| `src/gametest` | Fabric 服务端与客户端集成测试：事件、GUI、存档、HUD 同步与实际截图 |

测试代码和测试模组元数据不进入发布 JAR。

Gradle 为 IDEA/Loom 的运行环境提供 Fuji 14.12.0（26.3 版本）；Fuji 不内嵌进本模组发布包。

