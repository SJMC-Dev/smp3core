package club.sjmc.smp3.shop.ui;

import java.util.List;

import club.sjmc.smp3.gui.GuiStyle;
import club.sjmc.smp3.shop.ShopEntry;
import club.sjmc.smp3.shop.ShopService;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

final class ShopMessages {
	private ShopMessages() {}

	static List<Component> feedback(ShopEntry entry, ShopService.Outcome result) {
		Component message = switch (result.status()) {
			case BOUGHT ->
				Component.literal("已购买 " + entry.amount() + " 个 ")
				    .append(name(entry))
				    .append("，花费 " + entry.price() + " 通宝，今日剩余 " + remaining(entry, result.remaining()))
				    .withStyle(GuiStyle.READY);
			case SOLD ->
				Component.literal("已出售 " + entry.amount() + " 个 ")
				    .append(name(entry))
				    .append("，获得 " + entry.price() + " 通宝，今日剩余 " + remaining(entry, result.remaining()))
				    .withStyle(GuiStyle.READY);
			case NO_MONEY ->
				GuiStyle.text("通宝不足：需要 " + entry.price() + "，当前 " + result.balance(), GuiStyle.ERROR);
			case QUOTA -> quotaExhausted(entry, result.remaining());
			case LOCKED -> GuiStyle.text("你当前身份今日不可交易该条目", GuiStyle.ERROR);
			case NOT_ENOUGH_ITEMS ->
				Component.literal("背包中的 ")
				    .append(name(entry))
				    .append(" 不足 " + entry.amount() + " 个")
				    .withStyle(GuiStyle.ERROR);
			case NO_ECONOMY -> GuiStyle.text("经济系统不可用，请联系管理员", GuiStyle.ERROR);
			case PAYMENT_FAILED -> GuiStyle.text("扣款失败", GuiStyle.ERROR);
			case DEPOSIT_FAILED -> GuiStyle.text("入账失败，物品已退回", GuiStyle.ERROR);
		};
		return result.dropped() ? List.of(message, GuiStyle.text("背包已满，物品已掉落在脚下", GuiStyle.ACTIVE))
		                        : List.of(message);
	}

	static Component name(ShopEntry entry) {
		return entry.displayName() == null ? new ItemStack(entry.icon()).getHoverName()
		                                   : Component.literal(entry.displayName());
	}

	static Component quotaExhausted(ShopEntry entry, int remaining) {
		return GuiStyle.text(remaining <= 0 ? "今日额度已用完" : "今日额度不足 " + entry.amount() + " 个",
		                     GuiStyle.ERROR);
	}

	static String price(ShopEntry entry) {
		String unit = entry.amount() == 64 ? "组" : entry.amount() == 1 ? "个" : entry.amount() + " 个";
		return entry.price() + " 通宝 / " + unit;
	}

	static String remaining(ShopEntry entry, int remaining) {
		return remaining == ShopEntry.UNLIMITED ? "不限"
		: entry.amount() == 64                  ? (remaining / 64) + " 组"
		                                        : remaining + " 个";
	}
}
