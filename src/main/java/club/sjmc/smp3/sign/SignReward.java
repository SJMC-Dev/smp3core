package club.sjmc.smp3.sign;

import club.sjmc.smp3.economy.Smp3Currency;

/**
 * 单个奖励项，支持货币、物品、称号、命令四类。
 */
public sealed interface SignReward {

	/** 发放货币，{@code amount} 为单位数量（通宝/咖啡印章）。 */
	record Currency(Smp3Currency currency, int amount) implements SignReward {
	}

	/** 发放物品，背包满则掉在脚下。 */
	record Item(net.minecraft.world.item.Item item, int count) implements SignReward {
	}

	/** 通过 LuckPerms 授予称号：fuji.title.obtain.&lt;titleId&gt; = true。 */
	record Title(String titleId) implements SignReward {
	}

	/** 执行命令，{@code <player>} 会被替换成玩家名。 */
	record Command(String template) implements SignReward {
	}
}
