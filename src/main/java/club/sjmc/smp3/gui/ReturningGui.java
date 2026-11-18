package club.sjmc.smp3.gui;

import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;

public abstract class ReturningGui extends SimpleGui {
	private final Runnable parent;
	private boolean returnRequested;

	protected ReturningGui(MenuType<?> type, ServerPlayer player, Runnable parent) {
		super(type, player, false);
		this.parent = parent;
	}

	protected void back() {
		if (parent == null)
			close();
		else
			parent.run();
	}

	@Override
	public void onPlayerClose(boolean accepted) {
		returnRequested = accepted;
	}

	@Override
	public void afterRemoval() {
		boolean requested = returnRequested;
		returnRequested = false;
		if (requested && player.isAlive() && !player.hasDisconnected() && player.containerMenu == player.inventoryMenu)
			back();
	}
}
