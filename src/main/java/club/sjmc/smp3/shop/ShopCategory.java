package club.sjmc.smp3.shop;

import java.util.List;

import net.minecraft.world.item.ItemStack;

public record ShopCategory(String name, List<ShopEntry> entries) {

	public ShopCategory { entries = List.copyOf(entries); }

	public int size() { return entries.size(); }

	public ItemStack iconStack() { return entries.get(0).iconStack(); }
}
