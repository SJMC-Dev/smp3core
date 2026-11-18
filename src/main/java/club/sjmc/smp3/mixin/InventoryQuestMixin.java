package club.sjmc.smp3.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.sjmc.smp3.daily.DailyQuestEvents;
import club.sjmc.smp3.item.ItemDelivery;

import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

@Mixin(InventoryChangeTrigger.class)
public abstract class InventoryQuestMixin {
	@Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/player/"
	                 + "Inventory;Lnet/minecraft/world/item/ItemStack;)V",
	        at = @At("HEAD"))
	private void
	smp3$changed(ServerPlayer player, Inventory inventory, ItemStack changed, CallbackInfo ci) {
		DailyQuestEvents.inventoryChanged(player);
		ItemDelivery.inventoryChanged(player);
	}
}
