package club.sjmc.smp3.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.sjmc.smp3.daily.DailyQuestEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceQuestMixin extends Slot {
	@Shadow
	@Final
	private Player player;
	@Shadow
	private int removeCount;
	protected FurnaceQuestMixin(Container container, int slot, int x, int y) { super(container, slot, x, y); }
	@Inject(method = "checkTakeAchievements", at = @At("HEAD"))
	private void smp3$crafted(ItemStack result, CallbackInfo ci) {
		if (removeCount > 0 && player instanceof ServerPlayer serverPlayer)
			DailyQuestEvents.crafted(serverPlayer, result, removeCount);
	}

	@Override
	protected void onSwapCraft(int amount) {
		removeCount += amount;
	}
}
