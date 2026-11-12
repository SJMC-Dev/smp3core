package club.sjmc.smp3.shop.ui;

import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import club.sjmc.smp3.gui.GuiStyle;
import club.sjmc.smp3.perm.Permissions;
import club.sjmc.smp3.shop.QuotaStore;
import club.sjmc.smp3.shop.ShopCategory;
import club.sjmc.smp3.shop.ShopEntry;
import club.sjmc.smp3.shop.ShopService;
import club.sjmc.smp3.shop.TradeDirection;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class EntryListGui extends ShopGui {

	private final ShopCategory category;
	private final ShopGui parent;

	public EntryListGui(ServerPlayer player, ShopCategory category, ShopGui parent) {
		super(player, Component.literal(category.name()));
		this.category = category;
		this.parent = parent;
	}

	@Override
	protected void populate() {
		clearContent();
		setBackButton(parent);

		boolean advanced = Permissions.has(player, ShopService.ADVANCED_PERMISSION, true);
		int slot = 0;
		for (ShopEntry entry : category.entries()) {
			if (slot >= CONTENT_SLOTS) {
				break;
			}
			setSlot(slot++, element(entry, advanced));
		}
	}

	private GuiElement element(ShopEntry entry, boolean advanced) {
		int used = QuotaStore.used(player, entry);
		boolean locked = entry.isLockedFor(advanced);
		boolean exhausted = !locked && entry.exhausted(advanced, used);

		ItemStack stack = locked ? new ItemStack(Items.BARRIER) : new ItemStack(entry.icon());
		String pricePrefix = entry.direction() == TradeDirection.BUY ? "价格：" : "收购价：";

		GuiElementBuilder builder =
		    new GuiElementBuilder(stack)
		        .setName(
		            ShopMessages.name(entry).copy().withStyle(locked || exhausted ? GuiStyle.MUTED : GuiStyle.ACTIVE))
		        .addLoreLine(Component.literal(pricePrefix + ShopMessages.price(entry)).withStyle(GuiStyle.MUTED))
		        .addLoreLine(
		            Component.literal("今日剩余 " + ShopMessages.remaining(entry, entry.remainingItems(advanced, used)))
		                .withStyle(GuiStyle.MUTED));

		if (locked) {
			builder.addLoreLine(Component.literal("当前身份不可交易").withStyle(GuiStyle.ERROR));
		} else if (exhausted) {
			builder.addLoreLine(ShopMessages.quotaExhausted(entry, entry.remainingItems(advanced, used)));
		} else {
			builder.addLoreLine(Component.literal(entry.direction() == TradeDirection.BUY ? "左键购买" : "左键出售")
			                        .withStyle(GuiStyle.MUTED));
		}

		GuiStyle.onLeftClick(builder, () -> {
			if (entry.direction() == TradeDirection.BUY) {
				ShopMessages.feedback(entry, ShopService.buy(player, entry)).forEach(player::sendSystemMessage);
			} else {
				ShopMessages.feedback(entry, ShopService.sell(player, entry)).forEach(player::sendSystemMessage);
			}
			populate();
		});

		return builder.build();
	}
}
