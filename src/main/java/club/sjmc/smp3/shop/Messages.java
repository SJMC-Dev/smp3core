package club.sjmc.smp3.shop;


import club.sjmc.smp3.quota.QuotaStore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 需求文档 §3.8 的全部反馈文案。
 */
public final class Messages {

	private Messages() {
	}

	public static Component bought(ServerPlayer player, ShopEntry entry, boolean advanced) {
		int used = QuotaStore.used(player, entry);
		return Component.literal("已购买 " + entry.amount() + " 个 ")
				.append(entry.name())
				.append(Component.literal("，花费 " + entry.price() + " 通宝，今日剩余 "
						+ entry.remainingLabel(advanced, used)))
				.withStyle(ChatFormatting.GREEN);
	}

	public static Component sold(ServerPlayer player, ShopEntry entry, boolean advanced) {
		int used = QuotaStore.used(player, entry);
		return Component.literal("已出售 " + entry.amount() + " 个 ")
				.append(entry.name())
				.append(Component.literal("，获得 " + entry.price() + " 通宝，今日剩余 "
						+ entry.remainingLabel(advanced, used)))
				.withStyle(ChatFormatting.GREEN);
	}

	public static Component notEnoughMoney(int price, int balance) {
		return Component.literal("通宝不足：需要 " + price + "，当前 " + balance)
				.withStyle(ChatFormatting.RED);
	}

	public static Component quotaExhausted() {
		return Component.literal("今日额度已用完").withStyle(ChatFormatting.RED);
	}

	/**
	 * 额度不足时的文案：剩余为 0 时提示「今日额度已用完」，
	 * 仍有剩余但不足一个交易单位（如涂蜡铜灯的 32 个）时提示「今日额度不足 N 个」。
	 */
	public static Component quotaExhausted(ShopEntry entry, boolean advanced, int usedItems) {
		int remaining = entry.remainingItems(advanced, usedItems);
		if (remaining <= 0) {
			return quotaExhausted();
		}
		return Component.literal("今日额度不足 " + entry.amount() + " 个").withStyle(ChatFormatting.RED);
	}

	public static Component locked() {
		return Component.literal("你当前身份今日不可交易该条目").withStyle(ChatFormatting.RED);
	}

	public static Component notEnoughItems(ShopEntry entry) {
		return Component.literal("背包中的 ")
				.append(entry.name())
				.append(Component.literal(" 不足 " + entry.amount() + " 个"))
				.withStyle(ChatFormatting.RED);
	}

	public static Component droppedToGround() {
		return Component.literal("背包已满，物品已掉落在脚下").withStyle(ChatFormatting.YELLOW);
	}

	public static Component noEconomy() {
		return Component.literal("经济系统不可用，请联系管理员").withStyle(ChatFormatting.RED);
	}

	public static Component paymentFailed() {
		return Component.literal("扣款失败").withStyle(ChatFormatting.RED);
	}

	public static Component depositFailed() {
		return Component.literal("入账失败，物品已退回").withStyle(ChatFormatting.RED);
	}
}
