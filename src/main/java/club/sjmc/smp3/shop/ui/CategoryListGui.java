package club.sjmc.smp3.shop.ui;

import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import club.sjmc.smp3.gui.GuiStyle;
import club.sjmc.smp3.shop.ShopCategory;
import club.sjmc.smp3.shop.ShopSheet;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class CategoryListGui extends ShopGui {

	private final ShopSheet sheet;
	private final ShopGui parent;

	public CategoryListGui(ServerPlayer player, ShopSheet sheet, ShopGui parent) {
		super(player, Component.literal(sheet.name()));
		this.sheet = sheet;
		this.parent = parent;
	}

	@Override
	protected void populate() {
		clearContent();
		setBackButton(parent);

		int slot = 0;
		for (ShopCategory category : sheet.categories()) {
			if (category.size() == 0 || slot >= CONTENT_SLOTS) {
				continue;
			}
			setSlot(slot++, categoryElement(category));
		}
	}

	private GuiElement categoryElement(ShopCategory category) {
		return GuiStyle
		    .onLeftClick(new GuiElementBuilder(category.iconStack())
		                     .setName(Component.literal(category.name()).withStyle(GuiStyle.TITLE))
		                     .addLoreLine(Component.literal(category.size() + " 项").withStyle(GuiStyle.MUTED))
		                     .addLoreLine(Component.literal("左键进入").withStyle(GuiStyle.MUTED)),
		                 () -> new EntryListGui(player, category, this).open())
		    .build();
	}
}
