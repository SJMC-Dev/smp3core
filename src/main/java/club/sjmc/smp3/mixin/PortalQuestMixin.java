package club.sjmc.smp3.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.sjmc.smp3.daily.DailyQuestEvents;
import club.sjmc.smp3.daily.QuestActions;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.portal.PortalShape;

@Mixin(PortalShape.class)
public abstract class PortalQuestMixin {
	@Inject(method = "createPortalBlocks", at = @At("TAIL"))
	private void smp3$portal(LevelAccessor level, CallbackInfo ci) {
		var player = QuestActions.player();
		if (player != null)
			DailyQuestEvents.emit(player, "portal_activated",
			                      DailyQuestEvents.context(player, player.level(), player.blockPosition()));
	}
}
