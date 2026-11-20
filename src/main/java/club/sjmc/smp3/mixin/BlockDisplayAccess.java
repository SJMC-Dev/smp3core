package club.sjmc.smp3.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;

/** Changing vanilla display metadata needs this private setter; no renderer is registered. */
@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayAccess {
	@Invoker("setBlockState")
	void gacha$block(BlockState state);
}
