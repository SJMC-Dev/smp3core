package club.sjmc.smp3.mixin;

import java.util.Collection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.sjmc.smp3.daily.DailyQuestEvents;
import club.sjmc.smp3.daily.QuestEventType;

import net.minecraft.advancements.triggers.FishingRodHookedTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;

@Mixin(FishingRodHookedTrigger.class)
public abstract class FishingQuestMixin {
	@Inject(method = "trigger", at = @At("TAIL"))
	private void smp3$caught(ServerPlayer player, ItemStack rod, FishingHook hook, Collection<ItemStack> items,
	                         CallbackInfo ci) {
		if (hook.getHookedIn() == null)
			DailyQuestEvents.items(player, QuestEventType.FISH_CAUGHT.id(), hook, items);
	}
}
