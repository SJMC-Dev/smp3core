package club.sjmc.smp3.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.sjmc.smp3.daily.DailyQuestEvents;

import net.minecraft.advancements.triggers.BredAnimalsTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;

@Mixin(BredAnimalsTrigger.class)
public abstract class BreedingQuestMixin {
	@Inject(method = "trigger", at = @At("TAIL"))
	private void smp3$bred(ServerPlayer player, Animal first, Animal second, AgeableMob child, CallbackInfo ci) {
		DailyQuestEvents.entity(player, "entity_bred", first);
		if (child != null && child.getType() == EntityTypes.FOX)
			DailyQuestEvents.entity(player, "entity_trusted", child);
	}
}
