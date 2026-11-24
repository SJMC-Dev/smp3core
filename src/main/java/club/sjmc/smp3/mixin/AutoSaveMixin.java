package club.sjmc.smp3.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.sjmc.smp3.Smp3Core;

import net.minecraft.server.MinecraftServer;

@Mixin(MinecraftServer.class)
public abstract class AutoSaveMixin {
	@Shadow
	private int ticksUntilAutosave;
	@Shadow
	private int computeNextAutosaveInterval() {
		throw new AssertionError();
	}

	@ModifyConstant(method = "computeNextAutosaveInterval", constant = @Constant(floatValue = 300.0F))
	private float smp3$saveInterval(float original) {
		return Smp3Core.serverConfig().autoSaveIntervalSeconds();
	}

	@Inject(method = "loadLevel", at = @At("TAIL"))
	private void smp3$firstSave(CallbackInfo ci) {
		ticksUntilAutosave = computeNextAutosaveInterval();
	}
}
