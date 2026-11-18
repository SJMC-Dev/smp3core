package club.sjmc.smp3.shop;

import java.util.List;

import com.google.gson.JsonObject;

import club.sjmc.smp3.economy.Smp3Currency;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record ShopEntry(String id, TradeDirection direction, Component name, List<String> description, Item icon,
                        ItemStack template, TagKey<Item> tag, JsonObject delivery, Smp3Currency currency, int amount,
                        int price, int normalLimit, int advancedLimit) {
	public static final int UNLIMITED = -1;
	public ShopEntry { description = List.copyOf(description); }
	public boolean matches(ItemStack stack) {
		return !stack.isEmpty() && (tag == null ? ItemStack.isSameItemSameComponents(stack, template)
		                                        : stack.is(tag) && stack.getComponentsPatch().isEmpty());
	}
	public int limitFor(boolean advanced) { return advanced ? advancedLimit : normalLimit; }
	public long remainingItems(boolean advanced, long used) {
		int limit = limitFor(advanced);
		return limit == UNLIMITED ? UNLIMITED : Math.max(0, limit - used);
	}
}
