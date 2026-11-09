package club.sjmc.smp3.gui;

import club.sjmc.smp3.shop.ShopCategory;
import club.sjmc.smp3.shop.ShopSheet;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 类别页：某个工作表下的类别列表。没有条目的类别会被跳过。
 */
public final class CategoryListGui extends ShopGui {

	private final ServerPlayer player;
	private final ShopSheet sheet;
	private final ShopGui parent;

	public CategoryListGui(ServerPlayer player, ShopSheet sheet, ShopGui parent) {
		super(player, Component.literal(sheet.name()));
		this.player = player;
		this.sheet = sheet;
		this.parent = parent;
	}

	@Override
	public boolean open() {
		populate();
		return super.open();
	}

	private void populate() {
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
		return new GuiElementBuilder(category.iconStack())
				.setName(Component.literal(category.name()).withStyle(ChatFormatting.GOLD))
				.addLoreLine(Component.literal("共 " + category.size() + " 个条目").withStyle(ChatFormatting.GRAY))
				.addLoreLine(Component.literal("左键进入").withStyle(ChatFormatting.YELLOW))
				.setCallback((index, type, action, gui) -> {
					if (isLeftClick(type)) {
						new EntryListGui(player, category, this).open();
					}
				})
				.build();
	}
}
