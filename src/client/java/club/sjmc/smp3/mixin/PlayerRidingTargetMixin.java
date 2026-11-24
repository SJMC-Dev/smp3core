package club.sjmc.smp3.mixin;

import java.util.function.Predicate;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;

@Mixin(ProjectileUtil.class)
public abstract class PlayerRidingTargetMixin {
	@ModifyArg(method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/"
	                    +
	                    "minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/"
	                    + "minecraft/world/phys/EntityHitResult;",
	           at = @At(value = "INVOKE",
	                    target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/"
	                             + "minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
	           index = 2)
	private static Predicate<Entity>
	smp3$ignorePlayerStack(Predicate<Entity> predicate, @Local(argsOnly = true) Entity source) {
		if (!(source instanceof Player) || !source.isPassenger() && !source.isVehicle())
			return predicate;
		return predicate.and(target
		                     -> !(target instanceof Player) ||
		                            !source.hasIndirectPassenger(target) && !target.hasIndirectPassenger(source));
	}
}
