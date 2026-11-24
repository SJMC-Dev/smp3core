package club.sjmc.smp3.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

@Mixin(EntityRenderDispatcher.class)
public abstract class PlayerRidingRenderMixin {
	@Shadow
	public Camera camera;

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void smp3$clearCarrierView(Entity entity, Frustum frustum, double x, double y, double z, float tickDelta,
	                                   CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Player && !camera.isDetached() && camera.entity() instanceof Player viewer &&
		    viewer.hasIndirectPassenger(entity))
			cir.setReturnValue(false);
	}
}
