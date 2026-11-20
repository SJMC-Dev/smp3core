package club.sjmc.smp3.mixin;

import java.util.Objects;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.sjmc.smp3.daily.BarterAttribution;
import club.sjmc.smp3.gacha.GachaModule;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

@Mixin(ItemEntity.class)
public abstract class DroppedItemOwnershipMixin {
	@Inject(method = "setThrower", at = @At("TAIL"))
	private void smp3$owner(Entity thrower, CallbackInfo ci) {
		ItemEntity self = (ItemEntity)(Object)this;
		if (thrower instanceof Player && GachaModule.draws(self.getItem()) != 0)
			self.setAttached(GachaModule.TICKET_OWNER, thrower.getUUID());
		if (thrower instanceof Player && self.getItem().is(Items.GOLD_INGOT))
			self.setAttached(BarterAttribution.OWNER, thrower.getUUID());
	}
	@Inject(method = "tryToMerge", at = @At("HEAD"), cancellable = true)
	private void smp3$merge(ItemEntity other, CallbackInfo ci) {
		ItemEntity self = (ItemEntity)(Object)this;
		if (GachaModule.draws(self.getItem()) != 0) {
			ci.cancel();
			return;
		}
		if (self.getItem().is(Items.GOLD_INGOT) &&
		    !Objects.equals(self.getAttached(BarterAttribution.OWNER), other.getAttached(BarterAttribution.OWNER)))
			ci.cancel();
	}
}
