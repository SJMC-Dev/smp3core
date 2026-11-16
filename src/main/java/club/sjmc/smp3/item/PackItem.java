package club.sjmc.smp3.item;

import java.util.random.RandomGenerator;

import club.sjmc.smp3.item.ui.PackGui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class PackItem extends Item {
	private final boolean choice;
	public PackItem(Properties properties, boolean choice) {
		super(properties);
		this.choice = choice;
	}
	public static boolean open(ServerPlayer player, InteractionHand hand, ItemStack expected, int option,
	                           RandomGenerator random) {
		return ItemDelivery.exchange(player, hand, expected, () -> {
			ItemDefinitions.validate(ItemDefinitions.json(expected, player.registryAccess()), player.registryAccess());
			return ItemDefinitions.contents(expected, option, random);
		});
	}
	public boolean choice() { return choice; }
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer)
			PackGui.open(serverPlayer, hand);
		return InteractionResult.SUCCESS;
	}
}
