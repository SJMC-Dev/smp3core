package club.sjmc.smp3.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;

@Mixin(Entity.class)
public abstract class PlayerRidingEntityMixin {
	@WrapOperation(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
	               at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z"))
	private boolean
	smp3$playerVehicle(EntityType<?> type, Operation<Boolean> original) {
		return type == EntityTypes.PLAYER && (Object)this instanceof Player || original.call(type);
	}

	@Inject(method = {"addPassenger", "removePassenger"}, at = @At("TAIL"))
	private void smp3$syncCarrier(Entity passenger, CallbackInfo ci) {
		if ((Object)this instanceof ServerPlayer carrier && passenger instanceof Player)
			carrier.connection.send(new ClientboundSetPassengersPacket(carrier));
	}
}
