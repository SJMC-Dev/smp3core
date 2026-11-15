package club.sjmc.smp3.reward;

import club.sjmc.smp3.reward.ui.PackGui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class PackItem extends Item {
	private final boolean choice;
	public PackItem(Properties properties, boolean choice) {
		super(properties);
		this.choice = choice;
	}
	public boolean choice() { return choice; }
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer)
			PackGui.open(serverPlayer, hand);
		return InteractionResult.SUCCESS;
	}
}
