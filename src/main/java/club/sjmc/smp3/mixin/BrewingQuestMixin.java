package club.sjmc.smp3.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.sjmc.smp3.daily.BrewingOutput;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingQuestMixin implements BrewingOutput {
	@Shadow
	private NonNullList<ItemStack> items;
	@Unique
	private final ItemStack[] smp3$outputs = new ItemStack[3];
	@Unique
	private final int[] smp3$counts = new int[3];

	// Identity ties credit to this brew's output; replacement bottles cannot reuse it.
	@Override
	public int smp3$takeBrewed(int slot, int amount) {
		if (items.get(slot) != smp3$outputs[slot])
			return 0;
		int count = Math.min(amount, smp3$counts[slot]);
		smp3$counts[slot] -= count;
		return count;
	}

	@Inject(method = "doBrew", at = @At("HEAD"))
	private static void smp3$before(ServerLevel level, BlockPos pos, BrewingStandBlockEntity entity, CallbackInfo ci) {
		BrewingQuestMixin self = (BrewingQuestMixin)(Object)entity;
		for (int slot = 0; slot < 3; slot++) {
			if (self.items.get(slot) != self.smp3$outputs[slot])
				self.smp3$counts[slot] = 0;
			self.smp3$outputs[slot] = self.items.get(slot);
		}
	}

	@Inject(method = "doBrew", at = @At("TAIL"))
	private static void smp3$after(ServerLevel level, BlockPos pos, BrewingStandBlockEntity entity, CallbackInfo ci) {
		BrewingQuestMixin self = (BrewingQuestMixin)(Object)entity;
		for (int slot = 0; slot < 3; slot++) {
			ItemStack output = self.items.get(slot);
			if (output != self.smp3$outputs[slot]) {
				self.smp3$outputs[slot] = output;
				self.smp3$counts[slot] = output.getCount();
			}
		}
	}

	@Inject(method = "saveAdditional", at = @At("TAIL"))
	private void smp3$save(ValueOutput output, CallbackInfo ci) {
		for (int slot = 0; slot < 3; slot++)
			output.putInt("smp3:brewed_" + slot, items.get(slot) == smp3$outputs[slot]
			                                         ? Math.min(items.get(slot).getCount(), smp3$counts[slot])
													 : 0);
	}

	@Inject(method = "loadAdditional", at = @At("TAIL"))
	private void smp3$load(ValueInput input, CallbackInfo ci) {
		for (int slot = 0; slot < 3; slot++) {
			smp3$outputs[slot] = items.get(slot);
			smp3$counts[slot] = input.getIntOr("smp3:brewed_" + slot, 0);
		}
	}
}
