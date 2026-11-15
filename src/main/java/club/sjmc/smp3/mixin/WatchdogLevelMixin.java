package club.sjmc.smp3.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import club.sjmc.smp3.watchdog.WatchdogModule;
import club.sjmc.smp3.watchdog.hotspot.HotspotRecorder;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

@Mixin(Level.class)
public abstract class WatchdogLevelMixin {
	@WrapOperation(method = "tickBlockEntities",
	               at = @At(value = "INVOKE",
	                        target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
	private void
	smp3$blockEntity(TickingBlockEntity ticker, Operation<Void> original) {
		Level level = (Level)(Object)this;
		HotspotRecorder recorder = WatchdogModule.active(level);
		if (recorder == null) {
			original.call(ticker);
			return;
		}
		recorder.enter(level.dimension().identifier(), ChunkPos.pack(ticker.getPos()), ticker.getType(),
		               HotspotRecorder.Kind.BLOCK_ENTITY);
		try {
			original.call(ticker);
		} finally {
			recorder.exit();
		}
	}
}
