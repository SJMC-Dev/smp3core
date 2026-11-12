package club.sjmc.smp3.shop.ui;

import java.util.List;

import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import club.sjmc.smp3.gui.GuiStyle;
import club.sjmc.smp3.shop.ShopCatalog;
import club.sjmc.smp3.shop.ShopSheet;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SheetListGui extends ShopGui {

	public SheetListGui(ServerPlayer player) { super(player, Component.literal("商店")); }

	@Override
	protected void populate() {
		clearContent();
		setBackButton(null);

		List<ShopSheet> sheets = ShopCatalog.sheets();
		int slot = 0;
		for (ShopSheet sheet : sheets) {
			if (sheet.isEmpty() || slot >= CONTENT_SLOTS) {
				continue;
			}
			setSlot(slot++, sheetElement(sheet));
		}
	}

	private GuiElement sheetElement(ShopSheet sheet) {
		return GuiStyle
		    .onLeftClick(new GuiElementBuilder(sheet.categories().get(0).iconStack())
		                     .setName(Component.literal(sheet.name()).withStyle(GuiStyle.TITLE))
		                     .addLoreLine(Component.literal(sheet.entryCount() + " 项").withStyle(GuiStyle.MUTED))
		                     .addLoreLine(Component.literal("左键进入").withStyle(GuiStyle.MUTED)),
		                 () -> openSheet(sheet))
		    .build();
	}

	private void openSheet(ShopSheet sheet) {
		if (sheet.categories().size() == 1) {
			new EntryListGui(player, sheet.categories().get(0), this).open();
		} else {
			new CategoryListGui(player, sheet, this).open();
		}
	}
}
