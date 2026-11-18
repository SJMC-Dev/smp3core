package club.sjmc.smp3.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.sjmc.smp3.daily.DailyQuestEvents;

import net.minecraft.advancements.triggers.EntityHurtPlayerTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Projectile;

@Mixin(EntityHurtPlayerTrigger.class)
public abstract class ShieldQuestMixin {
	@Inject(method = "trigger", at = @At("TAIL"))
	private void smp3$blocked(ServerPlayer player, DamageSource source, float dealt, float taken, boolean blocked,
	                          CallbackInfo ci) {
		if (blocked && source.getDirectEntity() instanceof Projectile projectile)
			DailyQuestEvents.entity(player, "projectile_blocked", projectile);
	}
}
