package club.sjmc.smp3.item;

import club.sjmc.smp3.item.ui.EnchantBookGui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class AnyEnchantedBookItem extends Item {

	public AnyEnchantedBookItem(Properties properties) { super(properties); }

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}
		// 物品右键界面有意保留在服务端 GUI，不进入 MenuScreen，见 README 的“界面结构”。
		if (player instanceof ServerPlayer serverPlayer) {
			EnchantBookGui.open(serverPlayer, hand);
		}
		return InteractionResult.SUCCESS;
	}
}
