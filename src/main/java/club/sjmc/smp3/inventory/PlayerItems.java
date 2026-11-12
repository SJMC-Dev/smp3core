package club.sjmc.smp3.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class PlayerItems {
	private PlayerItems() {}

	public static boolean give(ServerPlayer player, ItemStack stack) {
		if (player.getInventory().add(stack) || stack.isEmpty()) {
			return false;
		}
		player.spawnAtLocation(player.level(), stack);
		return true;
	}
}
