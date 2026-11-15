package club.sjmc.smp3.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

import club.sjmc.smp3.watchdog.WatchdogModule;
import club.sjmc.smp3.watchdog.hotspot.HotspotRecorder;

import net.minecraft.server.level.ServerPlayer;

@Mixin(ServerPlayer.class)
public abstract class WatchdogPlayerMixin {
	@WrapMethod(method = "doTick")
	private void smp3$player(Operation<Void> original) {
		ServerPlayer player = (ServerPlayer)(Object)this;
		HotspotRecorder recorder = WatchdogModule.active(player.level());
		if (recorder == null) {
			original.call();
			return;
		}
		int token = recorder.enter(player.level().dimension().identifier(), player.chunkPosition().pack(),
		                           "minecraft:player", HotspotRecorder.Kind.PLAYER);
		try {
			original.call();
		} finally {
			recorder.exit(token);
		}
	}
}
