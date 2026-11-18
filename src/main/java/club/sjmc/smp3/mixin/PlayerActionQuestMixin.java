package club.sjmc.smp3.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import club.sjmc.smp3.daily.QuestActions;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

@Mixin(ServerPlayerGameMode.class)
public abstract class PlayerActionQuestMixin {
	@Shadow
	@Final
	protected ServerPlayer player;
	@WrapMethod(method = "destroyBlock")
	private boolean smp3$break(BlockPos pos, Operation<Boolean> original) {
		return QuestActions.player(player, () -> original.call(pos));
	}
	@WrapMethod(method = "useItemOn")
	private InteractionResult smp3$use(ServerPlayer player, Level level, ItemStack item, InteractionHand hand,
	                                   BlockHitResult hit, Operation<InteractionResult> original) {
		return QuestActions.player(player,
		                           ()
		                               -> QuestActions.placing(item.getItem() instanceof BlockItem,
		                                                       () -> original.call(player, level, item, hand, hit)));
	}
}
