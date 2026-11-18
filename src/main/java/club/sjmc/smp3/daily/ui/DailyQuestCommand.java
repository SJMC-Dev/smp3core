package club.sjmc.smp3.daily.ui;

import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.daily.DailyQuestModule;
import club.sjmc.smp3.daily.DailyQuestService;
import club.sjmc.smp3.daily.QuestDay;
import club.sjmc.smp3.perm.Permissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

public final class DailyQuestCommand {
	public static final String RESET_PERMISSION = "smp.dailyquest.reset";
	private DailyQuestCommand() {}

	public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		var command = Commands.literal("dailyquest")
		                  .executes(context -> DailyQuestGui.show(context.getSource().getPlayerOrException()) ? 1 : 0)
		                  .then(Commands.argument("player", EntityArgument.player())
		                            .requires(DailyQuestCommand::mayReset)
		                            .then(operation("reset"))
		                            .then(operation("reroll")))
		                  .build();
		root.then(command);
		for (String alias : List.of("dailyquests", "dailyrequest"))
			root.then(Commands.literal(alias).executes(command.getCommand()).redirect(command));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> operation(String name) {
		return Commands.literal(name)
		    .executes(context -> execute(context.getSource(), EntityArgument.getPlayer(context, "player"), -1, name))
		    .then(Commands.argument("slot", IntegerArgumentType.integer(1, 4))
		              .suggests(
		                  (context, builder) -> SharedSuggestionProvider.suggest(List.of("1", "2", "3", "4"), builder))
		              .executes(context
		                        -> execute(context.getSource(), EntityArgument.getPlayer(context, "player"),
		                                   IntegerArgumentType.getInteger(context, "slot") - 1, name)));
	}

	private static int execute(CommandSourceStack source, ServerPlayer target, int slot, String operation) {
		if (operation.equals("reroll"))
			return reroll(source, target, slot);
		try {
			var service = DailyQuestModule.current();
			if (service == null)
				throw new IllegalStateException("每日任务不可用");
			int changed = service.reset(target, slot);
			source.sendSuccess(
			    ()
			        -> Component.literal("已重置 " + target.getName().getString() + " 的 " + changed + " 项每日任务"),
			    false);
			target.sendSystemMessage(Component.literal("管理员已重置你的 " + changed + " 项每日任务"));
			Smp3Core.LOGGER.info("Quest reset operator={} target={} slot={}", source.getTextName(), target.getUUID(),
			                     slot + 1);
			return changed;
		} catch (RuntimeException error) {
			Smp3Core.LOGGER.error("Quest reset failed", error);
			source.sendFailure(Component.literal("重置失败：" + error.getMessage()));
			return 0;
		}
	}

	private static boolean mayReset(CommandSourceStack source) {
		if (source.getEntity() == null)
			return true;
		if (!(source.getEntity() instanceof ServerPlayer player))
			return false;
		return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)) ||
		    Permissions.has(player, RESET_PERMISSION, false);
	}

	private static int reroll(CommandSourceStack source, ServerPlayer target, int requested) {
		DailyQuestService service = DailyQuestModule.current();
		QuestDay day = service == null ? null : service.state(target);
		if (day == null) {
			source.sendFailure(Component.literal("每日任务不可用"));
			return 0;
		}
		int changed = 0;
		boolean attempted = false;
		for (int slot = 0; slot < 4; slot++) {
			if (requested >= 0 && slot != requested)
				continue;
			QuestDay.Assignment assignment = day.assignments().get(slot);
			if (requested < 0 && assignment.complete())
				continue;
			attempted = true;
			DailyQuestService.Outcome result = service.reroll(target, day.date, slot, assignment.token, true);
			Component message = Component.literal(target.getName().getString() + " 槽位 " + (slot + 1) + "：" +
			                                      QuestMessages.message(result));
			if (result.success()) {
				changed++;
				source.sendSuccess(() -> message, false);
			} else
				source.sendFailure(message);
			Smp3Core.LOGGER.info("[SMP3] 管理员任务刷新 operator={} target={} slot={} result={}", source.getTextName(),
			                     target.getUUID(), slot + 1, QuestMessages.message(result));
		}
		if (!attempted)
			source.sendFailure(Component.literal("该玩家没有未完成任务"));
		if (changed > 0)
			target.sendSystemMessage(Component.literal("管理员已更换你的 " + changed + " 项每日任务"));
		return changed;
	}
}
