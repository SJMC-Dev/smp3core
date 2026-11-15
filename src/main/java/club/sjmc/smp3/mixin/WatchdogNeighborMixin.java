package club.sjmc.smp3.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.sjmc.smp3.watchdog.WatchdogModule;
import club.sjmc.smp3.watchdog.hotspot.HotspotRecorder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.level.redstone.Orientation;

@Mixin(NeighborUpdater.class)
public interface WatchdogNeighborMixin {
	@Inject(method = "executeUpdate", at = @At("HEAD"))
	private static void smp3$neighbor(Level level, BlockState state, BlockPos pos, Block block, Orientation orientation,
	                                  boolean moved, CallbackInfo ci) {
		HotspotRecorder recorder = WatchdogModule.active(level);
		if (recorder == null)
			return;
		recorder.enter(level.dimension().identifier(), ChunkPos.pack(pos),
		               BuiltInRegistries.BLOCK.getKey(state.getBlock()), HotspotRecorder.Kind.NEIGHBOR);
	}

	@Inject(method = "executeUpdate", at = @At("RETURN"))
	private static void smp3$neighborEnd(Level level, BlockState state, BlockPos pos, Block block,
	                                     Orientation orientation, boolean moved, CallbackInfo ci) {
		HotspotRecorder recorder = WatchdogModule.active(level);
		if (recorder != null)
			recorder.exit();
	}

	@Inject(method = "executeShapeUpdate", at = @At("HEAD"))
	private static void smp3$shape(LevelAccessor access, Direction direction, BlockPos pos, BlockPos neighbor,
	                               BlockState state, int flags, int limit, CallbackInfo ci) {
		HotspotRecorder recorder = access instanceof Level level ? WatchdogModule.active(level) : null;
		if (recorder == null)
			return;
		Level level = (Level)access;
		recorder.enter(level.dimension().identifier(), ChunkPos.pack(pos),
		               BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()), HotspotRecorder.Kind.SHAPE);
	}

	@Inject(method = "executeShapeUpdate", at = @At("RETURN"))
	private static void smp3$shapeEnd(LevelAccessor access, Direction direction, BlockPos pos, BlockPos neighbor,
	                                  BlockState state, int flags, int limit, CallbackInfo ci) {
		HotspotRecorder recorder = access instanceof Level level ? WatchdogModule.active(level) : null;
		if (recorder != null)
			recorder.exit();
	}
}
