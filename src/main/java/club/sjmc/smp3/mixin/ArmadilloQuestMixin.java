package club.sjmc.smp3.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import club.sjmc.smp3.daily.DailyQuestEvents;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.item.ItemStack;

@Mixin(Armadillo.class)
public abstract class ArmadilloQuestMixin {
	@Inject(method = "brushOffScute", at = @At("RETURN"))
	private void smp3$brush(Entity actor, ItemStack tool, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() || !(actor instanceof ServerPlayer player))
			return;
		var armadillo = (Armadillo)(Object)this;
		var facts = DailyQuestEvents.context(player, player.level(), armadillo.blockPosition());
		DailyQuestEvents.target(facts, "entity", BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(armadillo.getType()));
		facts.put("action", Set.of("brush"));
		DailyQuestEvents.emit(player, "entity_interacted", facts);
	}
}
