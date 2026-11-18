package club.sjmc.smp3.notice.ui;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import club.sjmc.smp3.notice.NoticeModule;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class NoticeCommand {
	private NoticeCommand() {}

	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("info")
		    .executes(context -> {
			    var source = context.getSource();
			    if (!NoticeModule.current().open(source.getPlayerOrException())) {
				    source.sendFailure(Component.literal("请安装 SMP3 客户端以打开服务器手册"));
				    return 0;
			    }
			    return 1;
		    })
		    .then(
		        Commands.literal("edit")
		            .requires(NoticeCommand::allowed)
		            .executes(context -> {
			            NoticeModule.current().edit(context.getSource().getPlayerOrException(), "");
			            return 1;
		            })
		            .then(Commands.argument("id", StringArgumentType.string())
					          .suggests((context, builder)
					                        -> SharedSuggestionProvider.suggest(NoticeModule.current().ids(), builder))
					          .executes(context -> {
			                      NoticeModule.current().edit(context.getSource().getPlayerOrException(),
								                              StringArgumentType.getString(context, "id"));
			                      return 1;
		                      })))
		    .then(Commands.literal("reload").requires(NoticeCommand::allowed).executes(context -> {
			    var source = context.getSource();
			    if (!NoticeModule.current().reload(
			            message -> source.sendSuccess(() -> Component.literal(message), false))) {
				    source.sendFailure(Component.literal("公告正在加载，请稍候"));
				    return 0;
			    }
			    source.sendSuccess(() -> Component.literal("正在加载公告…"), false);
			    return 1;
		    }));
	}

	private static boolean allowed(CommandSourceStack source) {
		return source.getEntity() == null ||
		    source.getEntity() instanceof ServerPlayer player && NoticeModule.mayEdit(player);
	}
}
