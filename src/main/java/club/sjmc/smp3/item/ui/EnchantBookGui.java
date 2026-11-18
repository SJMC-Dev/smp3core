package club.sjmc.smp3.item.ui;

import java.util.List;

import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.item.EnchantBookService;
import club.sjmc.smp3.ui.GuiStyle;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

public final class EnchantBookGui extends SimpleGui {

	private static final int MAX_SLOTS = 54;

	private static final MenuType<?>[] ROW_TYPES = {MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
	                                                MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6};

	private final InteractionHand hand;

	private EnchantBookGui(ServerPlayer player, InteractionHand hand, int rows) {
		super(ROW_TYPES[rows - 1], player, false);
		this.hand = hand;
	}

	public static void open(ServerPlayer player, InteractionHand hand) {
		List<Holder.Reference<Enchantment>> enchantments = enchantments(player);
		if (enchantments.isEmpty()) {
			player.sendSystemMessage(Component.literal("当前没有可选的附魔"));
			return;
		}
		if (enchantments.size() > MAX_SLOTS) {
			Smp3Core.LOGGER.warn("[SMP3] 附魔数量 {} 超过单页上限 {}，多余部分不显示", enchantments.size(), MAX_SLOTS);
			enchantments = enchantments.subList(0, MAX_SLOTS);
		}

		int rows = Math.min(6, (enchantments.size() + 8) / 9);
		EnchantBookGui gui = new EnchantBookGui(player, hand, rows);
		gui.setTitle(Component.literal("任意附魔书"));
		gui.fill(enchantments);
		gui.open();
	}

	private static List<Holder.Reference<Enchantment>> enchantments(ServerPlayer player) {
		Registry<Enchantment> registry =
		    player.level().getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		return registry.listElements().toList();
	}

	private void fill(List<Holder.Reference<Enchantment>> enchantments) {
		for (int slot = 0; slot < enchantments.size(); slot++) {
			setSlot(slot, element(enchantments.get(slot)));
		}
	}

	private GuiElement element(Holder.Reference<Enchantment> holder) {
		int level = holder.value().getMaxLevel();
		ItemStack icon = EnchantBookService.book(holder, level);

		return GuiStyle
		    .onLeftClick(new GuiElementBuilder(icon).setName(Enchantment.getFullname(holder, level)),
		                 () -> select(holder, level))
		    .build();
	}

	private void select(Holder<Enchantment> holder, int level) {
		close();
		switch (EnchantBookService.redeem(player, hand, holder, level)) {
		case MISSING_BOOK -> player.sendSystemMessage(Component.literal("手中已没有任意附魔书"));
		case REDEEMED -> {
		}
		}
	}
}
