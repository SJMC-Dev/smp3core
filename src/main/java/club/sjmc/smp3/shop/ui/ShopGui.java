package club.sjmc.smp3.shop.ui;

import eu.pb4.sgui.api.gui.SimpleGui;

import club.sjmc.smp3.gui.GuiStyle;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;

public abstract class ShopGui extends SimpleGui {

	protected static final int CONTENT_SLOTS = 26;
	protected static final int BACK_SLOT = 26;

	protected ShopGui(ServerPlayer player, Component title) {
		super(MenuType.GENERIC_9x3, player, false);
		this.setTitle(title.copy().withStyle(GuiStyle.TITLE));
	}

	protected void setBackButton(ShopGui parent) {
		if (parent == null) {
			return;
		}
		setSlot(BACK_SLOT, GuiStyle.back(parent::open));
	}

	@Override
	public boolean open() {
		populate();
		return super.open();
	}

	protected abstract void populate();

	protected void clearContent() { GuiStyle.fill(this, CONTENT_SLOTS); }
}
