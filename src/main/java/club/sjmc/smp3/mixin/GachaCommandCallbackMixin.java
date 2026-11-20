package club.sjmc.smp3.mixin;

import com.mojang.brigadier.context.ContextChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.sjmc.smp3.gacha.GachaCommandRewards.Outcome;

import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ExecutionContext;

/**
 * Fuji advice/cooldown replace the source callback in Commands.performCommand.
 * There is no public event for composing that replacement. Restore only our marked
 * callback at the shared execution boundary, retaining the replacement's events.
 */
@Mixin(ExecutionContext.class)
public abstract class GachaCommandCallbackMixin {
	@Inject(method = "queueInitialCommandExecution", at = @At("HEAD"), cancellable = true)
	private static void gacha$preserveRewardCallback(ExecutionContext<?> context, String command, ContextChain<?> chain,
	                                                 ExecutionCommandSource<?> source,
	                                                 CommandResultCallback frameCallback, CallbackInfo ci) {
		if (!(chain.getTopContext().getSource() instanceof CommandSourceStack original) ||
		    !(source instanceof CommandSourceStack executing) || !(original.callback() instanceof Outcome outcome) ||
		    executing.callback() instanceof Outcome)
			return;
		// Both sources establish the concrete generic type. The marked replacement
		// makes the recursive entry a no-op, so the command is queued exactly once.
		@SuppressWarnings("unchecked")
		var typedContext = (ExecutionContext<CommandSourceStack>)context;
		@SuppressWarnings("unchecked")
		var typedChain = (ContextChain<CommandSourceStack>)chain;
		ExecutionContext.queueInitialCommandExecution(typedContext, command, typedChain,
		                                              executing.withCallback(outcome.preserving(executing.callback())),
		                                              frameCallback);
		ci.cancel();
	}
}
