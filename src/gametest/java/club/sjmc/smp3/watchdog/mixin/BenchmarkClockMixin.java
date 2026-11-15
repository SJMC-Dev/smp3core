package club.sjmc.smp3.watchdog.mixin;

import java.util.concurrent.locks.LockSupport;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.gametest.framework.GameTestServer;

@Mixin(GameTestServer.class)
abstract class BenchmarkClockMixin {
	@Unique
	private long smp3$previous;

	// GameTest normally runs without pacing; opt-in benchmarks need normal 20 TPS sampling.
	@WrapMethod(method = "waitUntilNextTick")
	private void pace(Operation<Void> original) {
		original.call();
		if (!Boolean.getBoolean("smp3.watchdog.benchmark"))
			return;
		long remaining = 50_000_000L - (System.nanoTime() - smp3$previous);
		while (remaining > 0) {
			LockSupport.parkNanos(remaining);
			remaining = 50_000_000L - (System.nanoTime() - smp3$previous);
		}
		smp3$previous = System.nanoTime();
	}
}
