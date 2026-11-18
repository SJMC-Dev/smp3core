package club.sjmc.smp3.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.sjmc.smp3.daily.DailyQuestEvents;

import net.minecraft.advancements.triggers.TameAnimalTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;

@Mixin(TameAnimalTrigger.class)
public abstract class TamingQuestMixin {
	@Inject(method = "trigger", at = @At("TAIL"))
	private void smp3$tamed(ServerPlayer player, Animal animal, CallbackInfo ci) {
		DailyQuestEvents.entity(player, "entity_tamed", animal);
	}
}
