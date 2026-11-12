# SMP3 配置指南

本文对应当前代码和默认配置，适用于 Minecraft 26.3 / Fabric。配置使用 UTF-8 JSON：字段名区分大小写，字符串使用双引号；不要添加注释、尾随逗号或未列出的配置字段。

## 1. 文件位置与生效方式

下表路径相对于游戏运行目录。开发环境通常为仓库中的 `run/`，正式服务器为服务器工作目录。

| 文件 | 用途 | 生成与生效 |
| --- | --- | --- |
| `config/smp3/server.json` | 每日功能共用时区 | 首次服务端启动生成；修改后重启服务器 |
| `config/smp3/dailyquests.json` | 任务、经验档位、奖励与刷新上限 | 文件不存在时从模组内默认配置复制；修改后重启服务器 |
| `config/smp3/client.json` | 每名玩家自己的 HUD 显示偏好 | 客户端按 H 切换时保存；手工编辑后重启客户端 |

仓库中的 `src/main/resources/dailyquests.json` 是打包进 JAR 的默认模板，`docs/stage4/dailyquests.json` 是评审副本。正常运行读取 `config/smp3/dailyquests.json`；已有运行配置不会被新 JAR 或模板自动覆盖。更新默认任务时，将需要的内容合并到运行配置中。

配置只在启动时加载；原版 `/reload` 不重新读取这些 JSON。修改前保存副本，保存后重启并查看日志中的配置路径、任务 ID 和错误信息。

- 当天已经分配的目标、说明、奖励及经验档位使用保存的快照；修改配置后，下一天的新任务使用新配置。
- 当天刷新未完成任务时，从当前配置选择新任务，仍保留当天档位与该槽位奖励。升级、死亡、重新登录不重新选择当天档位。
- 修改刷新上限后，剩余次数按新上限减去当天已用次数计算，不重置个人用量。
- 时区配置无效会阻止服务端启动；任务配置无效会停用每日任务模块并记录错误。

## 2. `server.json`

```json
{
  "timezone": "Asia/Shanghai"
}
```

| 字段 | 类型 / 必填 | 可填值与含义 |
| --- | --- | --- |
| `timezone` | 字符串 / 是 | Java `ZoneId` 支持的时区，例如 `Asia/Shanghai`、`UTC`、`Europe/London` 或 `+08:00`。默认 `Asia/Shanghai`，用于任务、签到和商店每日额度的日期判断。 |

每日边界为该时区的 00:00，在玩家后续操作时处理换日。地区时区会应用自身的夏令时规则，固定偏移时区则始终使用该偏移。

## 3. `dailyquests.json` 顶层字段

| 字段 | 类型 / 必填 | 可填值与含义 |
| --- | --- | --- |
| `dailyRerollLimit` | 整数 / 是 | `0`–`2147483647`。每名玩家每天共用的刷新次数上限，当前默认 `4`；`0` 关闭普通玩家刷新。管理员重置不消耗该额度。 |
| `xpBands` | 非空数组 / 是 | 当前经验等级到任务内部档位的映射，见下节。 |
| `rewards` | 对象 / 是 | 必须包含且只包含 `A`、`B`、`C` 三组奖励。 |
| `tasks` | 数组 / 是 | 任务定义列表，必须满足各难度的候选数量要求。 |

每日四槽固定为 A/B/C/C。A/B/C 决定槽位及奖励，`tier` 决定任务内部数量；A/B/C 不是玩家成长阶段。模板当前包含 71 个任务。

### 3.1 `xpBands[]`

```json
[
  {"minLevel": 0, "tier": 1},
  {"minLevel": 40, "tier": 2},
  {"minLevel": 80, "tier": 3}
]
```

| 字段 | 类型 / 必填 | 可填值与含义 |
| --- | --- | --- |
| `minLevel` | 整数 / 是 | `0`–`2147483647`，该档位生效的最低当前经验等级。第一项必须为 `0`，后续必须严格递增。 |
| `tier` | 正整数 / 是 | `1`–`2147483647`，引用任务 `tiers[].tier`。可以使用 1/2/3 之外的编号。 |

上例对应 0–39 级选 tier 1、40–79 级选 tier 2、80 级起选 tier 3。数值直接取该档的 `count`，不使用经验倍率。任务生成时固定当天档位。

档位数量不限于三个。例如，可在 `xpBands` 末尾追加 `{"minLevel": 120, "tier": 4}` 和 `{"minLevel": 160, "tier": 5}`，并在每个配置了 `tiers` 的任务中补齐 tier 4、5 的目标数量。固定数量或省略数量的任务无需修改；A/B/C/C 四槽与奖励分类保持不变。

### 3.2 `rewards.A` / `rewards.B` / `rewards.C`

```json
{
  "A": {"tongbao": 80, "coffeeStamp": 2},
  "B": {"tongbao": 50, "coffeeStamp": 1},
  "C": {"tongbao": 20, "coffeeStamp": 1}
}
```

| 字段 | 类型 / 必填 | 可填值与含义 |
| --- | --- | --- |
| `tongbao` | 非负整数 / 是 | 通宝奖励数量，`0`–`2147483647`；`0` 表示不发此币种。 |
| `coffeeStamp` | 非负整数 / 是 | 咖啡印章奖励数量，范围同上。 |

这里填写完整货币单位，例如 `80` 表示 80 通宝。两种奖励进入经济账户余额；奖励箱是菜单按钮。领取成功后该按钮消失，任务完成后刷新按钮消失。当前任务按行为进度领奖，不在领奖时扣除背包物品。

每个难度共用一组奖励，任务内部 tier 不改变奖励。此配置没有物品、命令或任务独立奖励字段。

## 4. `tasks[]` 任务字段

| 字段 | 类型 / 必填 | 可填值与含义 |
| --- | --- | --- |
| `id` | 字符串 / 是 | 全列表唯一的任务标识，只能使用小写英文字母、数字、`_`、`.`、`/`、`-`，例如 `craft_planks_a`；不能包含冒号或空格。 |
| `name` | 非空字符串 / 是 | 菜单任务名，支持中文。分档任务自动附加所选等级。 |
| `description` | 非空字符串 / 否 | 完成说明，支持占位符及 `\n` 换行；省略时展示条件摘要。提供该字段时不能填空字符串或 `null`。 |
| `category` | 非空字符串 / 是 | 活动分组，可自定义。现有值包括 `combat`、`mining`、`forestry`、`farming`、`building`、`fishing`、`trading`、`exploration`、`crafting`；分配时优先选择尚未出现的分组。它不决定奖励或事件类型。 |
| `difficulty` | 字符串 / 是 | 只能为大写 `A`、`B`、`C`，决定所在槽位与奖励。 |
| `icon` | 字符串 / 是 | 已注册物品 ID，例如 `minecraft:stone`；必须是单个物品 ID，不能填标签、数组或实体 ID。图标不决定完成条件。 |
| `objective` | 对象 / 是 | 包含 `event` 与 `conditions`，见第 5 节。 |
| `count` | 正整数 / 否 | `1`–`2147483647`。固定目标数量，放在任务顶层，不能与 `tiers` 同时出现。 |
| `tiers` | 非空数组 / 否 | 按经验档位选用的明确目标数量，不能与 `count` 同时出现。 |

数量有三种写法，适用于所有事件类型：

- 只设置 `count`：固定数量，不显示 tier 等级。
- 只设置 `tiers`：按当天档位选取数量，并显示等级。
- 两者都省略：目标为 `1`，不显示 tier 等级。探索任务常用这种写法。

### 4.1 `tiers[]`

```json
[
  {"tier": 1, "count": 16},
  {"tier": 2, "count": 32},
  {"tier": 3, "count": 64}
]
```

| 字段 | 类型 / 必填 | 可填值与含义 |
| --- | --- | --- |
| `tier` | 正整数 / 是 | 本任务内部唯一的档位编号。每个分档任务必须覆盖 `xpBands` 引用的全部档位。 |
| `count` | 正整数 / 是 | 此档目标数量，`1`–`2147483647`，不再做乘算。 |

各档共用任务的事件及条件，仅数量不同。多配置的未映射档位不会被正常分配选中；空数组、重复编号或遗漏映射档位会报错。

### 4.2 `description` 占位符

| 占位符 | 替换内容 | 使用条件 |
| --- | --- | --- |
| `{count}` | 当天已分配目标数量 | 所有任务均可使用 |
| `{item}` | 匹配物品名称 | `conditions` 中有 `item` |
| `{block}` | 匹配方块名称 | `conditions` 中有 `block` |
| `{entity}` | 匹配生物名称 | `conditions` 中有 `entity` |
| `{biome}` | 匹配群系名称 | `conditions` 中有 `biome` |
| `{dimension}` | 匹配维度名称 | `conditions` 中有 `dimension` |

候选数组以 ` / ` 连接名称；标签显示标签标识。也可以直接写“任意木板”等自然语言。不存在 `{tier}`、`{progress}`、`{block_state}` 等占位符；未知占位符或缺少对应条件会在加载时报告任务 ID。`%d`、`%s` 不会进行替换。

说明展示为任务图标的灰色 lore，支持显式换行及自动折行；HUD 保持图标和进度比例。说明与目标条件一起保存到当天快照。修改文字不会修改实际完成条件，因此编辑说明时应核对目标、数量单位及所有筛选条件。

## 5. `objective` 与事件

| 字段 | 类型 / 必填 | 可填值与含义 |
| --- | --- | --- |
| `event` | 字符串 / 是 | 下表九个事件之一，大小写必须一致。 |
| `conditions` | 对象 / 是 | 事件筛选条件；可以为 `{}`，表示接受该事件的全部目标。 |

所有事件都支持 `dimension`、`biome`。下表列出额外可用字段及 `count` 的实际单位。

| `event` | 行为 | 额外条件字段 | 数量口径 |
| --- | --- | --- | --- |
| `entity_killed` | 击杀生物 | `entity` | 玩家直接或其投射物造成最终伤害的击杀，每只记 1；宠物及环境击杀不计 |
| `item_crafted` | 玩家合成 | `item` | 背包 2×2 或工作台 3×3 成功取出产物的个数；一次产出 4 个木板记 4，支持 Shift 批量；预览不计 |
| `block_broken` | 破坏方块 | `block`、`block_state` | 每个直接成功破坏的方块记 1，不按掉落个数计；甘蔗、竹子连锁脱落不额外计数 |
| `crop_harvested` | 收获作物 | `block`、`block_state` | 成熟的 `CropBlock` 作物，或南瓜、西瓜方块被成功破坏时记 1；可可应使用带成熟状态条件的 `block_broken` |
| `block_placed` | 放置方块 | `block`、`block_state` | 每次成功放置记 1；不检测建筑形状，也不记录拆放历史 |
| `fish_caught` | 钓鱼 | `item` | 每次实际收获记 1；空收竿不计，多堆产物仍记 1；item 匹配其中任一产物 |
| `biome_visited` | 探索群系 | 无 | 每秒检测玩家位置，匹配时记 1；通常省略数量，首次身处目标群系即可完成。更大的数量会累计检测次数，不表示不同地点数量 |
| `villager_trade` | 村民交易 | `entity`、`item` | 每次成功交换记 1，批量按交换次数累计；item 是玩家获得的产物，不是付出的材料 |
| `piglin_barter` | 猪灵以物易物 | `entity`、`item` | 玩家直接交付金锭给成年猪灵，实际产出后记 1；地面金锭不计，离线或归属失效不补记；item 匹配任一产物 |

事件统计排除创造与旁观模式。同一事件可以推进多个匹配任务，进度在目标处封顶。物品拾取、持有、提交，及熔炼、酿造、锻造、自动合成没有对应配置事件。

### 5.1 条件字段与取值

除 `block_state` 外，条件值可以是一个字符串，也可以是非空字符串数组。不同字段之间为“全部满足”，同一数组中的候选为“满足任意一个”，可混用 ID 和标签。

| 字段 | 注册表 / 取值 | 示例 |
| --- | --- | --- |
| `item` | 物品 ID 或物品标签 | `"minecraft:bread"`、`"#minecraft:planks"` |
| `block` | 方块 ID 或方块标签 | `"minecraft:stone"`、`"#minecraft:iron_ores"` |
| `entity` | 实体类型 ID 或实体类型标签 | `"minecraft:drowned"`、`"#minecraft:raiders"` |
| `biome` | 群系 ID 或群系标签 | `"minecraft:forest"`、`"#minecraft:is_taiga"` |
| `dimension` | 已存在维度 ID，支持候选数组，不支持标签 | `"minecraft:overworld"`、`"minecraft:the_nether"`、`"minecraft:the_end"` |
| `block_state` | 属性名到字符串值的对象，同时必须设置 `block` | `{"age":"2"}`、`{"axis":"y"}`；即使属性是数字或布尔值，也写为字符串 |

ID 使用 `namespace:path`，例如 `minecraft:oak_log` 或已安装模组的物品 ID；省略命名空间时解析为 `minecraft`。标签加 `#` 前缀，必须在相应注册表存在且非空，数据包中的自定义标签也可使用。

物品和方块属于不同注册表，例如收获胡萝卜筛选方块 `minecraft:carrots`，匹配胡萝卜物品使用 `minecraft:carrot`。标签按所在字段选择注册表，不能将实体标签填入 `item`。

生物条件使用实体类型，不支持自定义名字、NBT 或实体的记分板标签。物品条件使用物品类型，不区分附魔、自定义名称或其他组件。`block_state` 应使用目标方块实际拥有的属性和值；多个属性都须匹配。

下面是可可成熟收获的 `objective`：

```json
{
  "event": "block_broken",
  "conditions": {
    "block": "minecraft:cocoa",
    "block_state": {"age": "2"}
  }
}
```

### 5.2 候选池约束

| `dailyRerollLimit` | A 最少任务数 | B 最少任务数 | C 最少任务数 |
| --- | --- | --- | --- |
| `0` | 1 | 1 | 2 |
| 大于 `0` | 2 | 2 | 3 |

四槽不会同时使用同一个任务 ID，刷新仅在同难度中选择其他任务。已完成任务不可刷新；管理员 `/dailyquest <player> reset [slot]` 同样只刷新未完成任务，省略 slot 时处理全部未完成槽位，slot 可填 1–4。

`/dailyquests` 是 `/dailyquest` 的别名，两者行为相同。OP 等级 2 及以上、有 `smp.dailyquest.reset` 权限的普通玩家及控制台可使用管理分支。OP 无需安装或配置 LuckPerms，优先按原版权限放行；输入命令后按 Tab 可补全在线玩家名、`reset` 和槽位 `1`–`4`，玩家名支持前缀匹配，并沿用原版玩家选择器补全。

## 6. 可直接使用的精简任务配置

下面是一份完整的七任务配置，满足开启刷新的最低候选池要求。日常使用可从随模组提供的 71 项配置继续编辑。

```json
{
  "dailyRerollLimit": 4,
  "xpBands": [
    {"minLevel": 0, "tier": 1},
    {"minLevel": 40, "tier": 2},
    {"minLevel": 80, "tier": 3}
  ],
  "rewards": {
    "A": {"tongbao": 80, "coffeeStamp": 2},
    "B": {"tongbao": 50, "coffeeStamp": 1},
    "C": {"tongbao": 20, "coffeeStamp": 1}
  },
  "tasks": [
    {
      "id": "planks_a", "name": "木板加工", "description": "合成 {count} 个木板，任意木材均可。",
      "category": "crafting", "difficulty": "A", "icon": "minecraft:oak_planks",
      "objective": {"event": "item_crafted", "conditions": {"item": "#minecraft:planks"}},
      "tiers": [{"tier": 1, "count": 8}, {"tier": 2, "count": 16}, {"tier": 3, "count": 32}]
    },
    {
      "id": "zombie_a", "name": "清理僵尸", "description": "击败 {count} 个{entity}。",
      "category": "combat", "difficulty": "A", "icon": "minecraft:rotten_flesh",
      "objective": {"event": "entity_killed", "conditions": {"entity": "minecraft:zombie"}},
      "tiers": [{"tier": 1, "count": 3}, {"tier": 2, "count": 6}, {"tier": 3, "count": 10}]
    },
    {
      "id": "wheat_b", "name": "麦田收获", "description": "收获 {count} 株成熟小麦。",
      "category": "farming", "difficulty": "B", "icon": "minecraft:wheat",
      "objective": {"event": "crop_harvested", "conditions": {"block": "minecraft:wheat"}},
      "count": 8
    },
    {
      "id": "stone_b", "name": "石材备料", "description": "挖掘 {count} 个{block}。",
      "category": "mining", "difficulty": "B", "icon": "minecraft:stone",
      "objective": {"event": "block_broken", "conditions": {"block": "minecraft:stone"}},
      "count": 16
    },
    {
      "id": "forest_c", "name": "森林漫步", "description": "前往{biome}。",
      "category": "exploration", "difficulty": "C", "icon": "minecraft:oak_sapling",
      "objective": {"event": "biome_visited", "conditions": {"biome": "minecraft:forest"}}
    },
    {
      "id": "torch_c", "name": "点亮住处", "description": "放置 {count} 个火把，地面或墙上均可。",
      "category": "building", "difficulty": "C", "icon": "minecraft:torch",
      "objective": {"event": "block_placed", "conditions": {"block": ["minecraft:torch", "minecraft:wall_torch"]}},
      "count": 4
    },
    {
      "id": "food_c", "name": "采购口粮", "description": "与村民完成 {count} 次交易，购买 {item}，任选一种。",
      "category": "trading", "difficulty": "C", "icon": "minecraft:bread",
      "objective": {"event": "villager_trade", "conditions": {"item": ["minecraft:bread", "minecraft:apple"]}},
      "count": 2
    }
  ]
}
```

## 7. `client.json`

```json
{
  "questHudVisible": true
}
```

| 字段 | 类型 | 可填值与含义 |
| --- | --- | --- |
| `questHudVisible` | 布尔值 | `true` 显示任务 HUD，`false` 隐藏；文件不存在时默认显示。请填写 JSON 布尔值，不加引号。 |

正常游戏中按 H 切换并保存。隐藏时所有条目同时沿用约 200 毫秒的向右滑出动画；再次显示时以相同时长从右侧滑入最新未领取条目，快速切换会从当前位置反向播放。按键可在原版“选项 → 控制 → 按键绑定 → SMP3”中修改；按键、缩放比例、位置、配色和动画时长没有本模组的 JSON 配置字段。F1 沿用原版立即隐藏整个 HUD，聊天输入 H 不触发切换。

## 8. 其他数据与功能

| 项目 | 当前配置方式 |
| --- | --- |
| 商店商品、价格、限额 | 定义于 `ShopCatalog`，当前没有 `shop.json`；仅每日日期使用 `server.json` 时区 |
| 签到奖励及里程碑 | 定义于 `SignRewards`，当前没有 `sign.json`；日期使用共同时区 |
| `config/smp3/economy.json` | 自动保存的经济账本，包含余额、任务领取回执和签到奖励状态；它不是任务配置文件 |
| 世界 `SavedData` | 自动保存任务进度、签到和商店额度等运行状态，不属于上述 JSON 的可配置字段 |
| Fuji / LuckPerms / 自定义 gacha | 各模组自行管理配置，SMP3 的 JSON 不提供它们的通用配置入口 |

备份服务器时将经济账本与世界数据一起保存。调整任务请编辑任务配置；不要把任务模板写入经济账本或世界运行数据。
