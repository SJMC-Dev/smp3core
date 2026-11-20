package club.sjmc.smp3.shop;

import java.util.List;

import net.minecraft.world.item.Item;

public record ShopNode(String id, String name, Item icon, List<ShopNode> children, List<ShopEntry> entries,
                       ShopRotation.Definition rotation) {
	public ShopNode {
		children = List.copyOf(children);
		entries = List.copyOf(entries);
	}
}
