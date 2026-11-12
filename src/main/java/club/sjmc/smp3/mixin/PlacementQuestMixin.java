package club.sjmc.smp3.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.sjmc.smp3.daily.DailyQuestEvents;
import club.sjmc.smp3.daily.QuestEventType;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.ItemUsedOnLocationTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemInstance;

@Mixin(ItemUsedOnLocationTrigger.class)
public abstract class PlacementQuestMixin {
	@Inject(method = "trigger", at = @At("TAIL"))
	private void smp3$placed(ServerPlayer player, BlockPos pos, ItemInstance tool, CallbackInfo ci) {
		if ((Object)this == CriteriaTriggers.PLACED_BLOCK) {
			DailyQuestEvents.block(player, QuestEventType.BLOCK_PLACED.id(), pos, player.level().getBlockState(pos));
		}
	}
}
