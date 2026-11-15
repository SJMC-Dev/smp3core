package club.sjmc.smp3.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

import club.sjmc.smp3.watchdog.WatchdogModule;
import club.sjmc.smp3.watchdog.hotspot.HotspotRecorder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
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
}
