package club.sjmc.smp3.gui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 商店界面基类：固定 3 行 27 格，0–25 为内容槽位，26 为「返回上一级」。
 *
 * <p>所有界面由服务端生成，玩家无法取出物品；点击只响应左键。</p>
 */
public abstract class ShopGui extends SimpleGui {

	/** 内容槽位数（0–25）。 */
	protected static final int CONTENT_SLOTS = 26;
	/** 返回上一级槽位。 */
	protected static final int BACK_SLOT = 26;

	protected ShopGui(ServerPlayer player, Component title) {
		super(MenuType.GENERIC_9x3, player, false);
		this.setTitle(title);
	}

	/** 底部「返回上一级」按钮；根页面传 {@code null} 时该槽位留空。 */
	protected void setBackButton(ShopGui parent) {
		if (parent == null) {
			return;
		}
		GuiElement element = new GuiElementBuilder(new ItemStack(Items.ARROW))
				.setName(Component.literal("返回上一级").withStyle(ChatFormatting.YELLOW))
				.setCallback((index, type, action, gui) -> {
					if (type == ClickType.MOUSE_LEFT) {
						parent.open();
					}
				})
				.build();
		setSlot(BACK_SLOT, element);
	}

	protected static boolean isLeftClick(ClickType type) {
		return type == ClickType.MOUSE_LEFT;
	}

	/** 清空内容槽位（重新生成 lore 时使用）。 */
	protected void clearContent() {
		for (int i = 0; i < CONTENT_SLOTS; i++) {
			clearSlot(i);
		}
	}
}
