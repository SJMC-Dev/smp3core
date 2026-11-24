package club.sjmc.smp3.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import club.sjmc.smp3.player.PlayerRiding;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;

@Mixin(ServerPlayer.class)
public abstract class PlayerRidingMixin {
	@Inject(method = "doTick", at = @At("TAIL"))
	private void smp3$dismount(CallbackInfo ci) {
		ServerPlayer player = (ServerPlayer)(Object)this;
		if (player.isVehicle() && player.isShiftKeyDown())
			PlayerRiding.detach(player);
	}

	@Inject(method = "die", at = @At("HEAD"))
	private void smp3$death(CallbackInfo ci) {
		PlayerRiding.detach((ServerPlayer)(Object)this);
	}

	@Inject(method = "setGameMode", at = @At("RETURN"))
	private void smp3$spectator(GameType mode, CallbackInfoReturnable<Boolean> cir) {
		if (mode == GameType.SPECTATOR && cir.getReturnValue())
			PlayerRiding.detach((ServerPlayer)(Object)this);
	}

	@Inject(
	    method =
	        "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
	    at = @At("HEAD"))
	private void
	smp3$dimension(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
		ServerPlayer player = (ServerPlayer)(Object)this;
		if (transition.newLevel() != player.level())
			PlayerRiding.detach(player);
	}
}
