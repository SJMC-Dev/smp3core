# SMP3 签到模组需求文档

- 目标服务器：Minecraft 26.3 / Fabric Loader 0.19.5
- 经济系统：Fuji 14.12.0（`smp3:tongbao,smp3:coffee_stamp`）
- 称号系统：Fuji 14.12.0 `title` 模块
- 模组依赖：fuji, luckperms, gacha（这个是自写的，id就是这个）

---

## 1. 项目概述

新增提供 `/sign` 界面，包含三类奖励：

1. **每日签到**：按连续签到天数发放通宝。
2. **月度累计**：每个自然月累计签到达到指定天数后，可领取档位奖励。
3. **开服累计**：从开服起累计签到达到指定天数后，可领取档位奖励（不随月份重置）。

## 2. 奖励规则

### 2.1 每日签到

- 每天只能签到一次。
- 连续签到天数决定当日通宝奖励：

| 连续天数 | 通宝 |
| --- | --- |
| 第 1 天 | 30 |
| 第 2 天 | 35 |
| 第 3 天 | 40 |
| 第 4 天 | 45 |
| 第 5 天及以后 | 50 |

- 断签后连续天数重置为第 1 天（奖励 30）。
- 判定“连续”的依据：上次签到日期是否为昨天。
- 每日签到奖励为通宝，直接发放到 `smp3:tongbao`。
- 签到成功后：
  - `monthTotal` +1
  - `serverTotal` +1
  - 更新 `lastSignDate`

### 2.2 月度累计

- 统计每个自然月内的累计签到天数。
- 每日签到成功后进行检查，达到配置档位后自动领取对应奖励。
- 每月 1 日 00:00（服务器本地时区）重置：
  - `monthTotal` 归零
  - 当月已领取档位清空
- 重置采用惰性方式：玩家打开 `/sign` 或签到时，比较 `monthKey` 与当前月份，不同则重置。
- 档位表由用户填写，本文档只提供结构与占位示例。

### 2.3 开服累计

- 从开服起累计签到天数，不随月份重置。
- 同样地，达到配置档位后自动领取对应奖励。
- 每个档位只能领取一次，永久记录。
- 档位范围：第 1\~7 天（x ≤ 7）。
- 档位奖励由用户填写，可包含通宝、物品、称号。
- 开服累计的起始时间待确认；如果模组上线晚于开服，需要管理员设置初始值或从模组上线起算。

### 2.4 领取方式

- 每日签到槽位：不是自动发放，必须点击 `/sign` 界面中的对应槽位领取。点击后完成当日签到并领取通宝。
- 月度累计档位：点击当日签到槽位后，如果达到天数则自动领取。
- 开服累计档位：点击当日签到槽位后，如果达到天数则自动领取。
- 已领取的槽位显示为已领取，不能重复领取。

## 3. 数据模型

每个玩家一份签到数据：

```text
PlayerSignData
├── playerUuid
├── lastSignDate        // 最后签到日期（LocalDate）
├── streak              // 当前连续签到天数
├── monthKey            // 当前累计所属月份，例如 "2026-11"
├── monthTotal          // 本月累计签到天数
├── serverTotal         // 开服累计签到天数
```

持久化：

- 使用 Fabric `SavedData` 保存在世界数据中。
- 使用独立文件 `world/data/sign-mod.dat`。
- 服务器重启后数据不丢。
- 离线玩家数据保留；下次打开 `/sign` 或签到时惰性处理跨天/跨月。

## 4. GUI 设计

### 4.1 入口

- 玩家执行 `/sign` 打开签到界面。

### 4.2 布局

6 行（54 格）：

| 区域 | 槽位 | 内容 |
| --- | --- | --- |
| 信息栏 | 4 | 当前连续天数、本月累计、开服累计 |
| 每日签到 |  | 今日签到槽位 |
|  |  |  |
|  |  |  |
| 关闭 | 49 | 关闭界面 |

### 4.3 槽位状态

| 状态 | 表现 |
| --- | --- |
| 未达成 | 灰色名称，lore 显示还差多少天 |
| 可领取 | 正常颜色，附带附魔光效或发光 lore |
| 已领取 | lore 显示“已领取” |

每日签到槽位：

- 未签到：显示今日奖励（30/35/40/45/50 中对应档位），点击签到。
- 已签到：显示“今日已签到”，不可再次点击。

### 4.4 图标建议

| 槽位 | 图标 |
| --- | --- |
| 每日签到 | `minecraft:clock` |
| 已签到/未签到/时间没到 | 黄绿色/黄色/淡灰色羊毛 |
|  |  |
| 关闭 | `minecraft:barrier` |

## 5. 领取流程

### 5.1 每日签到

```text
clickDailySign(player):
    today = LocalDate.now(serverZone)
    if data.lastSignDate == today:
        message("今天已经签到过了")
        return
    if data.lastSignDate == today.minusDays(1):
        data.streak += 1
    else:
        data.streak = 1
    reward = dailyReward(data.streak)   // 30/35/40/45/50
    economy.deposit(player, "smp3:tongbao", reward)
    data.lastSignDate = today
    rotateMonthIfNeeded(data, today)
    data.monthTotal += 1
    data.serverTotal += 1
    markDirty()
    message("签到成功，获得 " + reward + " 通宝，连续 " + data.streak + " 天")
```

### 5.2 月度累计档位

```text
clickMonthMilestone(player, milestone):
    rotateMonthIfNeeded(data, today)
    if data.monthTotal != milestone.days:
        return
    grantRewards(player, milestone.rewards)
    markDirty()
    message("领取成功")
```

### 5.3 开服累计档位

```text
clickServerMilestone(player, milestone):
    if data.serverTotal != milestone.days:
        return
    grantRewards(player, milestone.rewards)
    markDirty()
    message("领取成功")
```

### 5.4 月份切换

```text
rotateMonthIfNeeded(data, today):
    key = today.format("yyyy-MM")
    if data.monthKey != key:
        data.monthKey = key
        data.monthTotal = 0
```

## 6. 奖励类型

每个档位可以配置多个奖励：

| 类型 | 字段 | 说明 |
| --- | --- | --- |
| 通宝 | `currency` + `amount` | 发放 `smp3:tongbao` |
| 物品 | `item` + `item_id` + `count` | 发放物品，背包满则掉落 |
| 称号 | `title` + `title_id` | 通过 LuckPerms 设置 `fuji.title.obtain.<title_id>`为true |

### 6.1 称号集成

Fuji 的称号通过 LuckPerms 权限节点解锁：

```text
/lp user <player> permission set fuji.title.obtain.<title_id> true
```

实现方式：

- 模组调用 LuckPerms API 设置权限节点，或执行上述命令。
- `title_id` 必须同时出现在 `config/fuji/modules/title/config.json` 的 `title_descriptors` 中。
- 称号发放后，玩家可以在 Fuji 的 `/title!` 界面选择生效称号。

## 7. 配置文件

`不需要配置文件，全部硬编码。`

## 8. 补充

1. 月度累计奖励：

<table style="min-width: 75px;">
<colgroup><col style="min-width: 25px;"><col style="min-width: 25px;"><col style="min-width: 25px;"></colgroup><tbody><tr><td colspan="1" rowspan="1"><p>当月累计签到</p></td><td colspan="1" rowspan="1" style="text-align: right;"><p>铁镐通宝(smp3:tongbao)</p></td><td colspan="1" rowspan="1" style="text-align: right;"><p>咖啡印章(smp3:coffee_stamp)</p></td></tr><tr><td colspan="1" rowspan="1"><p>3天</p></td><td colspan="1" rowspan="1" style="text-align: right;"><p style="text-align: right;">50</p></td><td colspan="1" rowspan="1" style="text-align: right;"><p style="text-align: right;">3</p></td></tr><tr><td colspan="1" rowspan="1"><p>7天</p></td><td colspan="1" rowspan="1" style="text-align: right;"><p style="text-align: right;">100</p></td><td colspan="1" rowspan="1" style="text-align: right;"><p style="text-align: right;">5</p></td></tr><tr><td colspan="1" rowspan="1"><p>15天</p></td><td colspan="1" rowspan="1" style="text-align: right;"><p style="text-align: right;">150</p></td><td colspan="1" rowspan="1" style="text-align: right;"><p style="text-align: right;">10</p></td></tr><tr><td colspan="1" rowspan="1"><p>25天</p></td><td colspan="1" rowspan="1" style="text-align: right;"><p style="text-align: right;">200</p></td><td colspan="1" rowspan="1" style="text-align: right;"><p style="text-align: right;">15</p></td></tr></tbody>
</table>

1. 开服累计奖励

<table style="min-width: 75px;">
<colgroup><col style="min-width: 25px;"><col style="min-width: 25px;"><col style="min-width: 25px;"></colgroup><tbody><tr><td colspan="1" rowspan="1"><p><strong>天数</strong></p></td><td colspan="1" rowspan="1"><p><strong>铁镐通宝</strong></p></td><td colspan="1" rowspan="1"><p><strong>特殊奖励</strong></p></td></tr><tr><td colspan="1" rowspan="1"><p>1</p></td><td colspan="1" rowspan="1"><p>200</p></td><td colspan="1" rowspan="1"><p>-</p></td></tr><tr><td colspan="1" rowspan="1"><p>2</p></td><td colspan="1" rowspan="1"><p>200</p></td><td colspan="1" rowspan="1"><p>-</p></td></tr><tr><td colspan="1" rowspan="1"><p>3</p></td><td colspan="1" rowspan="1"><p>100</p></td><td colspan="1" rowspan="1"><p>任选种类附魔书*1</p><p>/give &lt;player&gt; smp3:any_enchanted_book</p></td></tr><tr><td colspan="1" rowspan="1"><p>4</p></td><td colspan="1" rowspan="1"><p>200</p></td><td colspan="1" rowspan="1"><p>-</p></td></tr><tr><td colspan="1" rowspan="1"><p>5</p></td><td colspan="1" rowspan="1"><p>100</p></td><td colspan="1" rowspan="1"><p>十连抽奖券*1</p><p>/gacha give &lt;player&gt; ten</p></td></tr><tr><td colspan="1" rowspan="1"><p>6</p></td><td colspan="1" rowspan="1"><p>200</p></td><td colspan="1" rowspan="1"><p>-</p></td></tr><tr><td colspan="1" rowspan="1"><p>7</p></td><td colspan="1" rowspan="1"><p>200</p></td><td colspan="1" rowspan="1"><p>称号「开服玩家」（永久）</p></td></tr></tbody>
</table>

1. 开服累计从11月11日开始。
2. 每日重置时区使用 `Asia/Shanghai`。
3. 不需要补签/漏签补偿。
4. 不需要注册其他指令。
5. 时刻注意避免过度设计。