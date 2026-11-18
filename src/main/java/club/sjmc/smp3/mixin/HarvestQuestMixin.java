package club.sjmc.smp3.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.sjmc.smp3.daily.QuestActions;
import club.sjmc.smp3.daily.QuestHarvest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(Block.class)
public abstract class HarvestQuestMixin {
	@WrapMethod(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/"
	                     + "Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/"
	                     + "BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V")
	private static void
	smp3$drops(BlockState state, Level level, BlockPos pos, BlockEntity block, Entity actor, ItemStack tool,
	           Operation<Void> original) {
		QuestActions.crop(state, () -> original.call(state, level, pos, block, actor, tool));
	}
	@WrapMethod(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/"
	                     + "Level;Lnet/minecraft/core/BlockPos;)V")
	private static void
	smp3$chain(BlockState state, Level level, BlockPos pos, Operation<Void> original) {
		QuestActions.crop(state, () -> original.call(state, level, pos));
	}
	@WrapMethod(
	    method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/"
	             + "LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V")
	private static void
	smp3$chainEntity(BlockState state, LevelAccessor level, BlockPos pos, BlockEntity block, Operation<Void> original) {
		QuestActions.crop(state, () -> original.call(state, level, pos, block));
	}
	@Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/"
	                 + "item/ItemStack;)V",
	        at = @At("HEAD"))
	private static void
	smp3$harvest(Level level, BlockPos pos, ItemStack stack, CallbackInfo ci) {
		QuestHarvest.harvest(level, pos, stack);
	}
}
