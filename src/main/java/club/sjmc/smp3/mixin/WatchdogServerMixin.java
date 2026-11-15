package club.sjmc.smp3.mixin;

import java.util.function.BooleanSupplier;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

import club.sjmc.smp3.watchdog.WatchdogModule;

import net.minecraft.server.MinecraftServer;

@Mixin(MinecraftServer.class)
public abstract class WatchdogServerMixin {
	@WrapMethod(method = "tickServer")
	private void smp3$recordTick(BooleanSupplier haveTime, Operation<Void> original) {
		MinecraftServer server = (MinecraftServer)(Object)this;
		WatchdogModule.beginTick(server);
		try {
			original.call(haveTime);
		} finally {
			WatchdogModule.endTick(server);
		}
	}
}
