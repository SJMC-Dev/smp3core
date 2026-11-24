package club.sjmc.smp3.mixin;

import java.util.Optional;
import java.util.UUID;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import mod.fuji.core.document.descriptor.MetaDescriptor;
import mod.fuji.module.initializer.home.HomeInitializer;
import mod.fuji.module.initializer.home.command.argument.wrapper.HomeName;
import mod.fuji.module.initializer.home.service.HomeService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.server.level.ServerPlayer;

@Mixin(HomeInitializer.class)
public abstract class HomeLimitMixin {
	// Fuji applies the creation quota to replacements too; moving an existing home uses no additional slot.
	@WrapOperation(
	    method = "$set",
	    at = @At(value = "INVOKE",
		         target = "Lmod/fuji/core/auxiliary/minecraft/LuckpermsHelper;getMeta(Ljava/util/UUID;Lmod/fuji/core/"
	                      + "document/descriptor/MetaDescriptor;[Ljava/lang/Object;)Ljava/util/Optional;"))
	private static Optional<?>
	smp3$creationLimit(UUID owner, MetaDescriptor<?> descriptor, Object[] arguments, Operation<Optional<?>> original,
	                   @Local(argsOnly = true) ServerPlayer player, @Local(argsOnly = true) HomeName home,
	                   @Local(argsOnly = true) Optional<Boolean> override) {
		return override.orElse(false) && HomeService.withHomeMap(player).containsKey(home.getValue())
		    ? Optional.empty()
			: original.call(owner, descriptor, arguments);
	}
}
