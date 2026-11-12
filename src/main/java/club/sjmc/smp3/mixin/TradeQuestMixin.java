package club.sjmc.smp3.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.sjmc.smp3.daily.DailyQuestEvents;
import club.sjmc.smp3.daily.QuestEventType;

import net.minecraft.advancements.triggers.TradeTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.ItemStack;

@Mixin(TradeTrigger.class)
public abstract class TradeQuestMixin {
	@Inject(method = "trigger", at = @At("TAIL"))
	private void smp3$traded(ServerPlayer player, AbstractVillager villager, ItemStack result, CallbackInfo ci) {
		DailyQuestEvents.items(player, QuestEventType.VILLAGER_TRADE.id(), villager, List.of(result));
	}
}
