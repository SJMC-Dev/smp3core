package club.sjmc.smp3.watchdog.ui;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import club.sjmc.smp3.perm.Permissions;
import club.sjmc.smp3.watchdog.WatchdogModule;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

public final class SmpPerfCommand {
	public static final String ADMIN_PERMISSION = "smp.watchdog.admin";
	private SmpPerfCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var top =
		    Commands.literal("top")
		        .executes(context -> WatchdogModule.current().top(context.getSource(), 10, 0))
		        .then(Commands.argument("durationSeconds", IntegerArgumentType.integer(5, 60))
				          .executes(
		                      context
		                      -> WatchdogModule.current().top(
		                          context.getSource(), IntegerArgumentType.getInteger(context, "durationSeconds"), 0))
				          .then(Commands.argument("minimumMillisPerTick", DoubleArgumentType.doubleArg(0))
				                    .executes(context
				                              -> WatchdogModule.current().top(
		                                          context.getSource(),
		                                          IntegerArgumentType.getInteger(context, "durationSeconds"),
		                                          DoubleArgumentType.getDouble(context, "minimumMillisPerTick")))));
		dispatcher.register(Commands.literal("smp3perf")
		                        .requires(SmpPerfCommand::allowed)
		                        .then(top)
		                        .then(Commands.literal("cleanup")
		                                  .then(Commands.literal("on").executes(
		                                      context -> WatchdogModule.current().change(context.getSource(), true)))
		                                  .then(Commands.literal("off").executes(
		                                      context -> WatchdogModule.current().change(context.getSource(), false))))
		                        .then(Commands.literal("reload").executes(
		                            context -> WatchdogModule.current().change(context.getSource(), null))));
	}

	private static boolean allowed(CommandSourceStack source) {
		return source.getEntity() == null ||
		    source.getEntity() instanceof ServerPlayer player &&
		        (source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)) ||
		         Permissions.has(player, ADMIN_PERMISSION, false));
	}
}
