package club.sjmc.smp3.shop;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/**
 * 商店类别页（例如「石类」「矿石」）。
 *
 * @param name    类别显示名
 * @param entries 该类别下的条目
 */
public record ShopCategory(String name, List<ShopEntry> entries) {

	public ShopCategory {
		entries = List.copyOf(entries);
	}

	public int size() {
		return entries.size();
	}

	/** 类别页图标：取第一个条目物品，作为分类代表物（空类别不会出现在界面里）。 */
	public ItemStack iconStack() {
		return entries.get(0).iconStack();
	}
}
