package club.sjmc.smp3.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.sjmc.smp3.daily.DailyQuestEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;

@Mixin(SmithingMenu.class)
public abstract class SmithingQuestMixin extends ItemCombinerMenu {
	@Unique
	private ItemStack smp3$quickResult;
	protected SmithingQuestMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access,
	                             ItemCombinerMenuSlotDefinition slots) {
		super(type, id, inventory, access, slots);
	}
	@Override
	public ItemStack quickMoveStack(Player player, int slot) {
		smp3$quickResult = slot == getResultSlot() ? getSlot(slot).getItem().copy() : null;
		try {
			return super.quickMoveStack(player, slot);
		} finally {
			smp3$quickResult = null;
		}
	}
	@Inject(method = "onTake", at = @At("HEAD"))
	private void smp3$crafted(Player player, ItemStack result, CallbackInfo ci) {
		ItemStack output = smp3$quickResult == null ? result : smp3$quickResult;
		int amount = smp3$quickResult == null ? result.getCount() : output.getCount() - result.getCount();
		if (amount > 0 && player instanceof ServerPlayer serverPlayer)
			DailyQuestEvents.crafted(serverPlayer, output, amount);
	}
}
