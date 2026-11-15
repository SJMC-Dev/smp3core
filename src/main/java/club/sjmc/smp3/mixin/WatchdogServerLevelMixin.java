package club.sjmc.smp3.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
	@Inject(method = "tickNonPassenger", at = @At("HEAD"))
	private void smp3$entity(Entity entity, CallbackInfo ci) {
		ServerLevel level = (ServerLevel)(Object)this;
		HotspotRecorder recorder = WatchdogModule.active(level);
		if (recorder == null)
			return;
		recorder.enter(level.dimension().identifier(), entity.chunkPosition().pack(),
		               BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), HotspotRecorder.Kind.ENTITY);
	}

	@Inject(method = "tickPassenger", at = @At("HEAD"))
	private void smp3$passenger(Entity vehicle, Entity entity, CallbackInfo ci) {
		ServerLevel level = (ServerLevel)(Object)this;
		HotspotRecorder recorder = WatchdogModule.active(level);
		if (recorder == null)
			return;
		recorder.enter(level.dimension().identifier(), entity.chunkPosition().pack(),
		               BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), HotspotRecorder.Kind.ENTITY);
	}

	@Inject(method = "tickBlock", at = @At("HEAD"))
	private void smp3$block(BlockPos pos, Block block, CallbackInfo ci) {
		ServerLevel level = (ServerLevel)(Object)this;
		HotspotRecorder recorder = WatchdogModule.active(level);
		if (recorder == null)
			return;
		recorder.enter(level.dimension().identifier(), ChunkPos.pack(pos), BuiltInRegistries.BLOCK.getKey(block),
		               HotspotRecorder.Kind.BLOCK);
	}

	@Inject(method = "tickFluid", at = @At("HEAD"))
	private void smp3$fluid(BlockPos pos, Fluid fluid, CallbackInfo ci) {
		ServerLevel level = (ServerLevel)(Object)this;
		HotspotRecorder recorder = WatchdogModule.active(level);
		if (recorder == null)
			return;
		recorder.enter(level.dimension().identifier(), ChunkPos.pack(pos), BuiltInRegistries.FLUID.getKey(fluid),
		               HotspotRecorder.Kind.FLUID);
	}

	@Inject(method = {"tickNonPassenger", "tickPassenger", "tickBlock", "tickFluid"}, at = @At("RETURN"))
	private void smp3$end(CallbackInfo ci) {
		HotspotRecorder recorder = WatchdogModule.active((ServerLevel)(Object)this);
		if (recorder != null)
			recorder.exit();
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
		recorder.enter(level.dimension().identifier(), ChunkPos.pack(pos),
		               BuiltInRegistries.BLOCK.getKey(state.getBlock()), HotspotRecorder.Kind.BLOCK_EVENT);
		try {
			return original.call(state, level, pos, type, data);
		} finally {
			recorder.exit();
		}
	}
}
