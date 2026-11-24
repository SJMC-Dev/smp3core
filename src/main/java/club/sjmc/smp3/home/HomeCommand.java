package club.sjmc.smp3.home;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.home.HomePayloads.Action;
import club.sjmc.smp3.home.HomePayloads.Request;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

final class HomeCommand {
	private final Supplier<HomeService> service;

	private HomeCommand(Supplier<HomeService> service) { this.service = service; }

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, Supplier<HomeService> service) {
		var command = new HomeCommand(service);
		var home = Commands.literal("home")
		               .requires(HomeCommand::available)
		               .executes(context -> command.execute(context, new Request(Action.TELEPORT, "", "")))
		               .then(command.name().executes(context -> command.named(context, Action.TELEPORT)));
		for (var action : new Action[] {Action.CREATE, Action.TELEPORT, Action.DELETE}) {
			String verb = switch (action) {
				case CREATE -> "set";
				case TELEPORT -> "tp";
				case DELETE -> "unset";
				default -> throw new AssertionError();
			};
			var name =
			    action == Action.CREATE ? Commands.argument("name", StringArgumentType.string()) : command.name();
			home.then(Commands.literal(verb)
			              .executes(context -> command.execute(context, new Request(Action.TELEPORT, verb, "")))
			              .then(name.executes(context -> command.named(context, action))));
		}
		var root = dispatcher.register(home);
		dispatcher.register(
		    Commands.literal("sethome").requires(HomeCommand::available).redirect(root.getChild("set")));
		dispatcher.register(
		    Commands.literal("delhome").requires(HomeCommand::available).redirect(root.getChild("unset")));
		dispatcher.register(Commands.literal("edithome")
		                        .requires(HomeCommand::available)
		                        .then(command.name()
		                                  .then(Commands.literal("rename").then(
		                                      Commands.argument("new_name", StringArgumentType.string())
		                                          .executes(context -> command.named(context, Action.RENAME))))
		                                  .then(Commands.literal("relocate")
		                                            .executes(context -> command.named(context, Action.UPDATE)))));
	}

	private static boolean available(CommandSourceStack source) {
		return source.getEntity() instanceof ServerPlayer player && HomeService.available(player);
	}

	private RequiredArgumentBuilder<CommandSourceStack, String> name() {
		return Commands.argument("name", StringArgumentType.string()).suggests(this::suggest);
	}

	private CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context,
	                                               SuggestionsBuilder builder) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrException();
		String prefix = builder.getRemainingLowerCase();
		if (prefix.startsWith("\""))
			prefix = prefix.substring(1);
		try {
			for (var entry : service.get().entries(player))
				if (entry.name().toLowerCase(Locale.ROOT).startsWith(prefix))
					builder.suggest(StringArgumentType.escapeIfRequired(entry.name()));
		} catch (IOException | RuntimeException error) {
			Smp3Core.LOGGER.error("[SMP3 Home] Failed to suggest homes for {}", player.getUUID(), error);
		}
		return builder.buildFuture();
	}

	private int named(CommandContext<CommandSourceStack> context, Action action) throws CommandSyntaxException {
		String name = StringArgumentType.getString(context, "name");
		String replacement = action == Action.RENAME   ? StringArgumentType.getString(context, "new_name")
		                     : action == Action.CREATE ? name
		                                               : "";
		return execute(context, new Request(action, name, replacement));
	}

	private int execute(CommandContext<CommandSourceStack> context, Request request) throws CommandSyntaxException {
		var source = context.getSource();
		var player = source.getPlayerOrException();
		try {
			var result = service.get().apply(player, request);
			if (!result.success()) {
				source.sendFailure(Component.literal(result.message()));
				return 0;
			}
			source.sendSuccess(() -> Component.literal(result.message()), false);
			HomeModule.refresh(player);
			return 1;
		} catch (IOException | RuntimeException error) {
			Smp3Core.LOGGER.error("[SMP3 Home] Failed to {} for {}", request.action(), player.getUUID(), error);
			source.sendFailure(Component.literal("家园操作失败，请联系管理员检查日志"));
			return 0;
		}
	}
}
