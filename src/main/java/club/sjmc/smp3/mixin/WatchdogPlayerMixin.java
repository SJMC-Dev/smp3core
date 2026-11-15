package club.sjmc.smp3.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.sjmc.smp3.watchdog.WatchdogModule;
import club.sjmc.smp3.watchdog.hotspot.HotspotRecorder;

import net.minecraft.server.level.ServerPlayer;

@Mixin(ServerPlayer.class)
public abstract class WatchdogPlayerMixin {
	@Inject(method = "doTick", at = @At("HEAD"))
	private void smp3$player(CallbackInfo ci) {
		ServerPlayer player = (ServerPlayer)(Object)this;
		HotspotRecorder recorder = WatchdogModule.active(player.level());
		if (recorder == null)
			return;
		recorder.enter(player.level().dimension().identifier(), player.chunkPosition().pack(), "minecraft:player",
		               HotspotRecorder.Kind.PLAYER);
	}

	@Inject(method = "doTick", at = @At("RETURN"))
	private void smp3$playerEnd(CallbackInfo ci) {
		ServerPlayer player = (ServerPlayer)(Object)this;
		HotspotRecorder recorder = WatchdogModule.active(player.level());
		if (recorder != null)
			recorder.exit();
	}
}
