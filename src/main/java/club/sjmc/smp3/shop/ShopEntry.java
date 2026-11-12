package club.sjmc.smp3.shop;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record ShopEntry(String id, TradeDirection direction, String displayName, Item icon, Item matchItem,
                        TagKey<Item> matchTag, int amount, int price, int normalLimit, int advancedLimit) {

	public static final int UNLIMITED = -1;

	public ItemStack iconStack() { return new ItemStack(icon); }

	public boolean matches(ItemStack stack) {
		if (matchTag != null) {
			return stack.is(matchTag);
		}
		return matchItem != null && stack.is(matchItem);
	}

	public int limitFor(boolean advanced) { return advanced ? advancedLimit : normalLimit; }

	public boolean isLockedFor(boolean advanced) { return limitFor(advanced) == 0; }

	public int remainingItems(boolean advanced, int usedItems) {
		int limit = limitFor(advanced);
		return limit == UNLIMITED ? UNLIMITED : Math.max(0, limit - usedItems);
	}

	public boolean exhausted(boolean advanced, int usedItems) {
		int limit = limitFor(advanced);
		return limit != UNLIMITED && usedItems + amount > limit;
	}
}
