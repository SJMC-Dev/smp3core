package club.sjmc.smp3.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

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
	@WrapMethod(method = "executeUpdate")
	private static void smp3$neighbor(Level level, BlockState state, BlockPos pos, Block block, Orientation orientation,
	                                  boolean moved, Operation<Void> original) {
		HotspotRecorder recorder = WatchdogModule.active(level);
		if (recorder == null) {
			original.call(level, state, pos, block, orientation, moved);
			return;
		}
		int token = recorder.enter(level.dimension().identifier(), ChunkPos.pack(pos),
		                           BuiltInRegistries.BLOCK.getKey(state.getBlock()), HotspotRecorder.Kind.NEIGHBOR);
		try {
			original.call(level, state, pos, block, orientation, moved);
		} finally {
			recorder.exit(token);
		}
	}

	@WrapMethod(method = "executeShapeUpdate")
	private static void smp3$shape(LevelAccessor access, Direction direction, BlockPos pos, BlockPos neighbor,
	                               BlockState state, int flags, int limit, Operation<Void> original) {
		HotspotRecorder recorder = access instanceof Level level ? WatchdogModule.active(level) : null;
		if (recorder == null) {
			original.call(access, direction, pos, neighbor, state, flags, limit);
			return;
		}
		Level level = (Level)access;
		int token = recorder.enter(level.dimension().identifier(), ChunkPos.pack(pos),
		                           BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()),
		                           HotspotRecorder.Kind.SHAPE);
		try {
			original.call(access, direction, pos, neighbor, state, flags, limit);
		} finally {
			recorder.exit(token);
		}
	}
}
