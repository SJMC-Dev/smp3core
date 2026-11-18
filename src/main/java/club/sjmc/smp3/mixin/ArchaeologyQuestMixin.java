package club.sjmc.smp3.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import club.sjmc.smp3.daily.DailyQuestEvents;
import club.sjmc.smp3.daily.QuestDefinition;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;

@Mixin(BrushableBlockEntity.class)
public abstract class ArchaeologyQuestMixin {
	@WrapOperation(
	    method = "dropContent",
	    at = @At(
	        value = "INVOKE",
	        target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
	private boolean
	smp3$extracted(ServerLevel level, Entity entity, Operation<Boolean> original, ServerLevel ignored,
	               LivingEntity actor, ItemStack tool) {
		boolean spawned = original.call(level, entity);
		if (spawned && actor instanceof ServerPlayer player && entity instanceof ItemEntity drop) {
			var facts = DailyQuestEvents.context(player, level, ((BrushableBlockEntity)(Object)this).getBlockPos());
			DailyQuestEvents.target(facts, "item", BuiltInRegistries.ITEM.wrapAsHolder(drop.getItem().getItem()));
			DailyQuestEvents.emit(player,
			                      new QuestDefinition.Event("archaeology_completed", facts, drop.getItem().getCount()));
		}
		return spawned;
	}
}
