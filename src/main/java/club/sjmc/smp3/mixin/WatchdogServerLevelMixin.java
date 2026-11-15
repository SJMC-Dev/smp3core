package club.sjmc.smp3.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import club.sjmc.smp3.watchdog.WatchdogModule;
import club.sjmc.smp3.watchdog.hotspot.HotspotRecorder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

@Mixin(ServerLevel.class)
public abstract class WatchdogServerLevelMixin {
	@WrapMethod(method = "tickNonPassenger")
	private void smp3$entity(Entity entity, Operation<Void> original) {
		ServerLevel level = (ServerLevel)(Object)this;
		HotspotRecorder recorder = WatchdogModule.active(level);
		if (recorder == null) {
			original.call(entity);
			return;
		}
		int token = recorder.enter(level.dimension().identifier(), entity.chunkPosition().pack(),
		                           BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), HotspotRecorder.Kind.ENTITY);
		try {
			original.call(entity);
		} finally {
			recorder.exit(token);
		}
	}

	@WrapMethod(method = "tickPassenger")
	private void smp3$passenger(Entity vehicle, Entity entity, Operation<Void> original) {
		ServerLevel level = (ServerLevel)(Object)this;
		HotspotRecorder recorder = WatchdogModule.active(level);
		if (recorder == null) {
			original.call(vehicle, entity);
			return;
		}
		int token = recorder.enter(level.dimension().identifier(), entity.chunkPosition().pack(),
		                           BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), HotspotRecorder.Kind.ENTITY);
		try {
			original.call(vehicle, entity);
		} finally {
			recorder.exit(token);
		}
	}

	@WrapMethod(method = "tickBlock")
	private void smp3$block(BlockPos pos, Block block, Operation<Void> original) {
		ServerLevel level = (ServerLevel)(Object)this;
		HotspotRecorder recorder = WatchdogModule.active(level);
		if (recorder == null) {
			original.call(pos, block);
			return;
		}
		int token = recorder.enter(level.dimension().identifier(), ChunkPos.pack(pos),
		                           BuiltInRegistries.BLOCK.getKey(block), HotspotRecorder.Kind.BLOCK);
		try {
			original.call(pos, block);
		} finally {
			recorder.exit(token);
		}
	}

	@WrapMethod(method = "tickFluid")
	private void smp3$fluid(BlockPos pos, Fluid fluid, Operation<Void> original) {
		ServerLevel level = (ServerLevel)(Object)this;
		HotspotRecorder recorder = WatchdogModule.active(level);
		if (recorder == null) {
			original.call(pos, fluid);
			return;
		}
		int token = recorder.enter(level.dimension().identifier(), ChunkPos.pack(pos),
		                           BuiltInRegistries.FLUID.getKey(fluid), HotspotRecorder.Kind.FLUID);
		try {
			original.call(pos, fluid);
		} finally {
			recorder.exit(token);
		}
	}

	@WrapOperation(method = "doBlockEvent",
	               at = @At(value = "INVOKE",
	                        target = "Lnet/minecraft/world/level/block/state/BlockState;triggerEvent(Lnet/minecraft/"
	                                 + "world/level/Level;Lnet/minecraft/core/BlockPos;II)Z"))
	private boolean
	smp3$blockEvent(BlockState state, Level level, BlockPos pos, int type, int data, Operation<Boolean> original) {
		HotspotRecorder recorder = WatchdogModule.active(level);
		if (recorder == null)
			return original.call(state, level, pos, type, data);
		int token = recorder.enter(level.dimension().identifier(), ChunkPos.pack(pos),
		                           BuiltInRegistries.BLOCK.getKey(state.getBlock()), HotspotRecorder.Kind.BLOCK_EVENT);
		try {
			return original.call(state, level, pos, type, data);
		} finally {
			recorder.exit(token);
		}
	}
}
