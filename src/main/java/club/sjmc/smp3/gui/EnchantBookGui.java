package club.sjmc.smp3.gui;

import java.util.List;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.item.Smp3Items;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * 「任意附魔书」的选择界面：每格一本最高等级的附魔书，点击后关闭界面、
 * 消耗一本任意附魔书并给出对应的附魔书。
 */
public final class EnchantBookGui extends SimpleGui {

	/** 6 行界面能容纳的最大格数。 */
	private static final int MAX_SLOTS = 54;

	private static final MenuType<?>[] ROW_TYPES = {
			MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
			MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6
	};

	private final ServerPlayer player;
	private final InteractionHand hand;

	private EnchantBookGui(ServerPlayer player, InteractionHand hand, int rows) {
		super(ROW_TYPES[rows - 1], player, false);
		this.player = player;
		this.hand = hand;
	}

	/** 为玩家打开界面。 */
	public static void open(ServerPlayer player, InteractionHand hand) {
		List<Holder.Reference<Enchantment>> enchantments = enchantments(player);
		if (enchantments.isEmpty()) {
			player.sendSystemMessage(Component.literal("当前没有可选的附魔"));
			return;
		}
		if (enchantments.size() > MAX_SLOTS) {
			Smp3Core.LOGGER.warn("[SMP3] 附魔数量 {} 超过单页上限 {}，多余部分不显示",
					enchantments.size(), MAX_SLOTS);
			enchantments = enchantments.subList(0, MAX_SLOTS);
		}

		int rows = Math.min(6, (enchantments.size() + 8) / 9);
		EnchantBookGui gui = new EnchantBookGui(player, hand, rows);
		gui.setTitle(Component.literal("任意附魔书"));
		gui.fill(enchantments);
		gui.open();
	}

	private static List<Holder.Reference<Enchantment>> enchantments(ServerPlayer player) {
		Registry<Enchantment> registry = player.level().getServer()
				.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		return registry.listElements().toList();
	}

	private void fill(List<Holder.Reference<Enchantment>> enchantments) {
		for (int slot = 0; slot < enchantments.size(); slot++) {
			setSlot(slot, element(enchantments.get(slot)));
		}
	}

	private GuiElement element(Holder.Reference<Enchantment> holder) {
		int level = holder.value().getMaxLevel();
		ItemStack icon = book(holder, level);

		return new GuiElementBuilder(icon)
				.setName(Enchantment.getFullname(holder, level))
				.setCallback((index, type, action, gui) -> {
					if (type == ClickType.MOUSE_LEFT) {
						select(holder, level);
					}
				})
				.build();
	}

	/** 消耗一本任意附魔书，并给出对应附魔书。 */
	private void select(Holder<Enchantment> holder, int level) {
		close();

		ItemStack held = player.getItemInHand(hand);
		if (held.getItem() != Smp3Items.ANY_ENCHANTED_BOOK) {
			player.sendSystemMessage(Component.literal("手中已没有任意附魔书"));
			return;
		}
		held.shrink(1);
		if (held.isEmpty()) {
			player.setItemInHand(hand, ItemStack.EMPTY);
		}

		ItemStack reward = book(holder, level);
		boolean dropped = !player.getInventory().add(reward) && !reward.isEmpty();
		if (dropped) {
			player.spawnAtLocation(player.level(), reward);
		}

		Smp3Core.LOGGER.info("[SMP3] 任意附魔书使用：player={}({}) enchantment={} level={}{}",
				player.getName().getString(), player.getUUID(),
				Enchantment.getFullname(holder, level).getString(), level,
				dropped ? " (背包已满，已掉落)" : "");
	}

	/** 构造一本指定附魔、指定等级的附魔书。 */
	private static ItemStack book(Holder<Enchantment> holder, int level) {
		ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		enchantments.set(holder, level);
		stack.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
		return stack;
	}
}
