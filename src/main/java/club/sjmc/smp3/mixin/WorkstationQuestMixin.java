package club.sjmc.smp3.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.sjmc.smp3.daily.DailyQuestEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

@Mixin(targets = {"net.minecraft.world.inventory.StonecutterMenu$2", "net.minecraft.world.inventory.LoomMenu$6"})
public abstract class WorkstationQuestMixin extends Slot {
	@Unique
	private ItemStack smp3$quickResult;

	protected WorkstationQuestMixin(Container container, int slot, int x, int y) { super(container, slot, x, y); }

	@Override
	protected void onQuickCraft(ItemStack original, int amount) {
		smp3$quickResult = original.copyWithCount(amount);
	}

	@Inject(method = "onTake", at = @At("HEAD"))
	private void smp3$crafted(Player player, ItemStack carried, CallbackInfo ci) {
		ItemStack result = smp3$quickResult == null ? carried : smp3$quickResult;
		smp3$quickResult = null;
		if (!result.isEmpty() && player instanceof ServerPlayer serverPlayer)
			DailyQuestEvents.crafted(serverPlayer, result, result.getCount(), index == 1 ? "stonecutting" : "loom");
	}
}
