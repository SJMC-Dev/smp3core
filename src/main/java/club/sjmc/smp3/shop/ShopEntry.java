package club.sjmc.smp3.shop;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 一个可交易条目（买入或回收）。
 *
 * <p>额度统一换算为物品个数：{@code -1} 表示不限量，{@code 0} 表示该身份今日不可交易。</p>
 *
 * @param id            条目稳定 id（同时作为额度计数的键）
 * @param direction     交易方向
 * @param displayName   自定义显示名；为 {@code null} 时使用物品自身名称
 * @param icon          图标物品（标签条目用一个代表物品）
 * @param matchItem     按物品匹配时的物品；标签条目为 {@code null}
 * @param matchTag      按标签匹配时的标签；物品条目为 {@code null}
 * @param amount        每次交易的单位数量（按组 = 64，按个 = 1，涂蜡铜灯 = 32）
 * @param price         每次交易的价格（通宝）
 * @param normalLimit   普通玩家每日额度（物品个数）
 * @param advancedLimit 进阶玩家每日额度（物品个数）
 */
public record ShopEntry(
		String id,
		TradeDirection direction,
		String displayName,
		Item icon,
		Item matchItem,
		TagKey<Item> matchTag,
		int amount,
		int price,
		int normalLimit,
		int advancedLimit) {

	/** 今日额度不限。 */
	public static final int UNLIMITED = -1;

	/** 给界面与消息使用的条目名称。 */
	public Component name() {
		if (displayName != null) {
			return Component.literal(displayName);
		}
		return new ItemStack(icon).getHoverName();
	}

	/** 界面图标（不带数量）。 */
	public ItemStack iconStack() {
		return new ItemStack(icon);
	}

	/** 物品堆是否属于本条目。 */
	public boolean matches(ItemStack stack) {
		if (matchTag != null) {
			return stack.is(matchTag);
		}
		return matchItem != null && stack.is(matchItem);
	}

	/** 指定身份的每日额度（物品个数，-1 = 不限）。 */
	public int limitFor(boolean advanced) {
		return advanced ? advancedLimit : normalLimit;
	}

	/** 该身份今日是否完全不可交易。 */
	public boolean isLockedFor(boolean advanced) {
		return limitFor(advanced) == 0;
	}

	/** 按组 / 按个 的单位名，用于价格与剩余额度展示。 */
	public String unitLabel() {
		if (amount == 64) {
			return "组";
		}
		if (amount == 1) {
			return "个";
		}
		return amount + " 个";
	}

	/** 例如「2 通宝 / 组」。 */
	public String priceLabel() {
		return price + " 通宝 / " + unitLabel();
	}

	/** 今日剩余可交易物品数；不限时返回 {@link #UNLIMITED}。 */
	public int remainingItems(boolean advanced, int usedItems) {
		int limit = limitFor(advanced);
		return limit == UNLIMITED ? UNLIMITED : Math.max(0, limit - usedItems);
	}

	/** 剩余额度展示：`x 组` / `x 个` / `不限`。 */
	public String remainingLabel(boolean advanced, int usedItems) {
		int limit = limitFor(advanced);
		if (limit == UNLIMITED) {
			return "不限";
		}
		int remaining = remainingItems(advanced, usedItems);
		if (amount == 64) {
			return (remaining / 64) + " 组";
		}
		return remaining + " 个";
	}

	/** 额度是否不足以再交易一个单位。 */
	public boolean exhausted(boolean advanced, int usedItems) {
		int limit = limitFor(advanced);
		return limit != UNLIMITED && usedItems + amount > limit;
	}

	/** 聊天反馈里使用的方向文案。 */
	public String actionHint() {
		return direction == TradeDirection.BUY ? "左键购买" : "左键出售";
	}
}
