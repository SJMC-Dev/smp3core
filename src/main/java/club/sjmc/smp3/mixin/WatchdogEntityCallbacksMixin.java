package club.sjmc.smp3.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.sjmc.smp3.watchdog.WatchdogModule;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class WatchdogEntityCallbacksMixin {
	@Inject(method = "onSectionChange(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
	private void smp3$itemMoved(Entity entity, CallbackInfo ci) {
		if (entity instanceof ItemEntity item)
			WatchdogModule.moved(item);
	}
}
