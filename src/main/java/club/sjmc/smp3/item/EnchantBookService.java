package club.sjmc.smp3.item;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.inventory.PlayerItems;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class EnchantBookService {
	public enum Outcome { REDEEMED, MISSING_BOOK }

	private EnchantBookService() {}

	public static Outcome redeem(ServerPlayer player, InteractionHand hand, Holder<Enchantment> holder, int level) {
		ItemStack held = player.getItemInHand(hand);
		if (held.getItem() != Smp3Items.ANY_ENCHANTED_BOOK) {
			return Outcome.MISSING_BOOK;
		}
		held.shrink(1);
		if (held.isEmpty()) {
			player.setItemInHand(hand, ItemStack.EMPTY);
		}

		ItemStack reward = book(holder, level);
		boolean dropped = PlayerItems.give(player, reward);

		Smp3Core.LOGGER.info("[SMP3] 任意附魔书使用：player={}({}) enchantment={} level={}{}",
		                     player.getName().getString(), player.getUUID(),
		                     Enchantment.getFullname(holder, level).getString(), level,
		                     dropped ? " (背包已满，已掉落)" : "");
		return Outcome.REDEEMED;
	}
	public static ItemStack book(Holder<Enchantment> holder, int level) {
		ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		enchantments.set(holder, level);
		stack.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
		return stack;
	}
}
