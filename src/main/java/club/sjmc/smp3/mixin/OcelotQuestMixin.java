package club.sjmc.smp3.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import club.sjmc.smp3.daily.DailyQuestEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.player.Player;

@Mixin(Ocelot.class)
public abstract class OcelotQuestMixin {
	@Shadow
	protected abstract boolean isTrusting();
	@WrapMethod(method = "mobInteract")
	private InteractionResult smp3$trust(Player player, InteractionHand hand, Operation<InteractionResult> original) {
		boolean before = isTrusting();
		var result = original.call(player, hand);
		if (!before && isTrusting() && player instanceof ServerPlayer serverPlayer)
			DailyQuestEvents.entity(serverPlayer, "entity_trusted", (Ocelot)(Object)this);
		return result;
	}
}
