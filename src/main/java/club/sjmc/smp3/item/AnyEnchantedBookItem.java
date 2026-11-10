package club.sjmc.smp3.item;

import club.sjmc.smp3.gui.EnchantBookGui;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * 「任意附魔书」：手持右键打开附魔选择界面，选中后消耗一本并给出对应的附魔书。
 */
public final class AnyEnchantedBookItem extends Item {

	public AnyEnchantedBookItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			EnchantBookGui.open(serverPlayer, hand);
		}
		return InteractionResult.SUCCESS;
	}
}
