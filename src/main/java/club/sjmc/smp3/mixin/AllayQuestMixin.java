package club.sjmc.smp3.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import club.sjmc.smp3.daily.DailyQuestEvents;
import club.sjmc.smp3.daily.QuestActions;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.level.Level;

@Mixin(Allay.class)
public abstract class AllayQuestMixin {
	@WrapOperation(
	    method = "duplicateAllay",
	    at = @At(value = "INVOKE",
		         target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
	private boolean
	smp3$duplicate(Level level, Entity child, Operation<Boolean> original) {
		boolean spawned = original.call(level, child);
		var player = QuestActions.player();
		if (spawned && player != null)
			DailyQuestEvents.entity(player, "entity_bred", (Allay)(Object)this);
		return spawned;
	}
}
