package club.sjmc.smp3.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import club.sjmc.smp3.daily.DailyQuestEvents;
import club.sjmc.smp3.daily.QuestActions;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;

@Mixin(ServerLevel.class)
public abstract class GolemQuestMixin {
	@Inject(method = "addFreshEntity", at = @At("RETURN"))
	private void smp3$built(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		var player = QuestActions.player();
		if (player != null && QuestActions.building() && cir.getReturnValue() &&
		    (entity.getType() == EntityTypes.IRON_GOLEM || entity.getType() == EntityTypes.SNOW_GOLEM ||
		     entity.getType() == EntityTypes.COPPER_GOLEM))
			DailyQuestEvents.entity(player, "entity_built", entity);
	}
}
