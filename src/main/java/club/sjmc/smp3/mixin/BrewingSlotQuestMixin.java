package club.sjmc.smp3.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.sjmc.smp3.daily.BrewingOutput;
import club.sjmc.smp3.daily.DailyQuestEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

@Mixin(targets = "net.minecraft.world.inventory.BrewingStandMenu$PotionSlot")
public abstract class BrewingSlotQuestMixin extends Slot {
	@Unique
	private int smp3$taken;
	protected BrewingSlotQuestMixin(Container container, int slot, int x, int y) { super(container, slot, x, y); }

	@Unique
	private void smp3$take(int amount) {
		if (container instanceof BrewingOutput brewed)
			smp3$taken += brewed.smp3$takeBrewed(getContainerSlot(), amount);
	}
	@Override
	public ItemStack remove(int amount) {
		smp3$take(Math.min(amount, getItem().getCount()));
		return super.remove(amount);
	}
	@Override
	protected void onQuickCraft(ItemStack original, int amount) {
		smp3$take(amount);
	}
	@Override
	public void setByPlayer(ItemStack item, ItemStack previous) {
		smp3$take(previous.getCount());
		super.setByPlayer(item, previous);
	}
	@Inject(method = "onTake", at = @At("TAIL"))
	private void smp3$crafted(Player player, ItemStack result, CallbackInfo ci) {
		int amount = smp3$taken;
		smp3$taken = 0;
		if (amount > 0 && player instanceof ServerPlayer serverPlayer)
			DailyQuestEvents.crafted(serverPlayer, result, amount, "brewing");
	}
}
