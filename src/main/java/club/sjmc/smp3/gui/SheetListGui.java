package club.sjmc.smp3.gui;

import java.util.List;

import club.sjmc.smp3.shop.ShopCatalog;
import club.sjmc.smp3.shop.ShopSheet;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 根页面：工作表页。没有子项的工作表会被跳过。
 */
public final class SheetListGui extends ShopGui {

	private final ServerPlayer player;

	public SheetListGui(ServerPlayer player) {
		super(player, Component.literal("商店"));
		this.player = player;
	}

	@Override
	public boolean open() {
		populate();
		return super.open();
	}

	private void populate() {
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
		return new GuiElementBuilder(sheet.categories().get(0).iconStack())
				.setName(Component.literal(sheet.name()).withStyle(ChatFormatting.GOLD))
				.addLoreLine(Component.literal("共 " + sheet.entryCount() + " 个条目")
						.withStyle(ChatFormatting.GRAY))
				.addLoreLine(Component.literal("左键进入").withStyle(ChatFormatting.YELLOW))
				.setCallback((index, type, action, gui) -> {
					if (isLeftClick(type)) {
						openSheet(sheet);
					}
				})
				.build();
	}

	/** 只有单个类别的工作表跳过类别页，直接进入条目页。 */
	private void openSheet(ShopSheet sheet) {
		if (sheet.categories().size() == 1) {
			new EntryListGui(player, sheet.categories().get(0), this).open();
		} else {
			new CategoryListGui(player, sheet, this).open();
		}
	}
}
