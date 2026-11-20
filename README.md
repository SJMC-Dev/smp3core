# SMP3 Core

Minecraft 26.3 / Fabric Loader 0.19.5 / Java 25 SMP3 核心模组，提供商店、每日签到、每日任务、抽奖、通宝/咖啡印章经济以及性能相关工具。

服务器与玩家客户端安装同一个模组 JAR，并安装 Fabric API、Fuji。服务器上的玩家权限由 LuckPerms 提供。

部署前移除旧 Daily Quests（mod ID：`dailyquests`）和 Gacha（mod ID: `gacha`），本模组已声明冲突。


详细信息如命令和配置，请参见 [Wiki](https://github.com/SJMC-Dev/smp3core/wiki)；


## TODOs

- [x] 根除了 DailyQuests 第三方 mod

- [x] 代码结构重构
- [x] 商店和时区逻辑修复
- [x] 签到和成就奖励

- [x] 每日任务需求

- [x] 商店可配置
- [x] 每日任务、商店 GUI 重构
- [x] 公告栏和游戏内发布
- [x] 文档补充

- [x] Gacha 合入



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

