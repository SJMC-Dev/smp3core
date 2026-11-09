# SMP3 Core

SMP3 服务器核心模组，入口为 `club.sjmc.smp3.Smp3Core`，后续可在此继续扩展其它功能。

当前提供**商店**功能：用模组自有的 `smp3:tongbao`（1 通宝 = 100 原始单位）结算，支持**买入建筑材料**与**回收资源**，
带身份额度（`smp.shop.advanced`）与每日限量。

- 模组 id：`smp3`
- 包名：`club.sjmc.smp3`
- 目标环境：Minecraft 26.3 / Fabric Loader 0.19.5 / Java 25
- 依赖：Fabric API、[SGUI](https://github.com/Patbox/sgui)（已内嵌）、Fuji（仅提供 common-economy-api）、LuckPerms（身份判定）
- 账本：按玩家 UUID 记账，落盘到 `config/smp3/economy.json`；可用 `/economy give|take|set|pay <玩家> smp3:tongbao <数值>` 管理

需求来源：`shop-mod-requirements.md`；条目数值来源：`【数值】商店列表.xlsx`。

## 使用方式

```
/shop <玩家>     # op 等级 4 或控制台，为指定玩家打开商店
```

界面固定 3 行 27 格：0–25 为内容槽位，26 为「返回上一级」（根页面留空）。
只响应左键；右键 / Shift / 拖拽一律忽略。按 `Esc` 关闭不会产生任何交易。

## 交易规则

- 每次左键交易一个单位：按组条目 = 64 个，按个条目 = 1 个（涂蜡铜灯为 32 个）。
- 买入：校验 → 扣款 → 发货（优先背包，溢出掉落脚下）→ 记账 → 反馈；任一项校验失败都不扣款。
- 回收：校验 → 扣物品 → 入账 → 记账 → 反馈。
- 每日限量按「读取即重置」：每次读取额度时比较记录日期与服务器本地日期，跨天自动清零。
- 计数只存内存，服务器重启后清零。

## 构建

```bash
./gradlew build
```

产物：`build/libs/smp3-<version>.jar`。

构建依赖说明：

- SGUI 通过 `include` 内嵌进产物，无需额外安装。
- `common-economy-api` 是 `compileOnly`，运行时由 Fuji 内嵌提供。
- LuckPerms API 坐标 `net.luckperms:api:5.5` 发布在 **Maven Central**（见 [LuckPerms Developer API](https://luckperms.net/wiki/Developer-API)），
  以 `compileOnly` 引用；运行时使用服务端安装的 LuckPerms。

## 源码结构

```
club.sjmc.smp3
├── Smp3Core               模组入口（后续功能扩展点）
├── command                 /shop 命令
├── economy                 common-economy-api 桥接（fuji:tongbao）
├── perm                    身份判定（smp.shop.advanced）
├── quota                   每日额度计数（内存，读取即重置）
├── shop                    条目数据与交易服务
└── gui                     SGUI 三级界面
```
