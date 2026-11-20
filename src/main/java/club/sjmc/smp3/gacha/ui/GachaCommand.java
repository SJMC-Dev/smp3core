package club.sjmc.smp3.gacha.ui;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.gacha.GachaConfig.Bonus;
import club.sjmc.smp3.gacha.GachaConfig.Prize;
import club.sjmc.smp3.gacha.GachaConfig.Tier;
import club.sjmc.smp3.gacha.GachaConfig;
import club.sjmc.smp3.gacha.GachaLedger;
import club.sjmc.smp3.gacha.GachaModule;
import club.sjmc.smp3.item.ItemDefinitions;
import club.sjmc.smp3.item.ItemDelivery;
import club.sjmc.smp3.item.Smp3Items;
import club.sjmc.smp3.perm.Permissions;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class GachaCommand {
	private GachaCommand() {}
	private static boolean admin(CommandSourceStack source) { return Permissions.isAdmin(source, "smp.gacha.admin"); }
	private static boolean owner(CommandSourceStack source) {
		return source.getEntity() == null ||
		    source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_OWNER) ||
		    source.getEntity() instanceof ServerPlayer player && Permissions.has(player, "smp.gacha.command", true);
	}
	@FunctionalInterface
	private interface Action {
		void run() throws Exception;
	}
	private static int run(CommandContext<CommandSourceStack> c, Action action) {
		try {
			GachaModule.current();
			action.run();
			return 1;
		} catch (Exception error) {
			c.getSource().sendFailure(
			    Component.literal(error.getMessage() == null ? "操作失败，请查看日志" : error.getMessage()));
			if (!(error instanceof IllegalArgumentException) && !(error instanceof CommandSyntaxException))
				Smp3Core.LOGGER.error("Gacha command failed", error);
			return 0;
		}
	}
	private static String text(CommandContext<CommandSourceStack> c, String key) {
		return StringArgumentType.getString(c, key);
	}
	private static void tell(CommandSourceStack source, String text) {
		source.sendSuccess(() -> Component.literal(text), false);
	}
	private static RequiredArgumentBuilder<CommandSourceStack, String> pool() { return pool("pool"); }
	private static RequiredArgumentBuilder<CommandSourceStack, String> pool(String name) {
		return Commands.argument(name, StringArgumentType.word()).suggests((c, builder) -> {
			var ids = new HashSet<>(GachaModule.current().config().pools.keySet());
			if (c.getSource().getEntity() instanceof ServerPlayer player)
				ids.addAll(GachaLedger.read(player.getUUID()).data().pools.keySet());
			return SharedSuggestionProvider.suggest(ids, builder);
		});
	}
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		var root = Commands.literal("gacha").executes(c -> run(c, () -> help(c.getSource())));
		root.then(Commands.literal("help").executes(c -> run(c, () -> help(c.getSource()))));
		root.then(
		    Commands.literal("reload")
		        .requires(GachaCommand::admin)
		        .executes(c -> run(c, () -> GachaModule.current().reload(message -> tell(c.getSource(), message)))));
		var history = pool().executes(c -> run(c, () -> history(c, 1, false)));
		history.then(Commands.argument("page", IntegerArgumentType.integer(1))
		                 .executes(c -> run(c, () -> history(c, IntegerArgumentType.getInteger(c, "page"), false))));
		history.then(
		    Commands.literal("details")
		        .executes(c -> run(c, () -> history(c, 1, true)))
		        .then(Commands.argument("page", IntegerArgumentType.integer(1))
				          .executes(c -> run(c, () -> history(c, IntegerArgumentType.getInteger(c, "page"), true)))));
		root.then(Commands.literal("history").then(history));
		root.then(Commands.literal("pity").then(pool().executes(
		    c -> run(c, () -> {
			    var data =
			        GachaLedger.read(c.getSource().getPlayerOrException().getUUID()).data().pool(text(c, "pool"));
			    var rules = GachaModule.current().config().pool(text(c, "pool"));
			    tell(c.getSource(),
				     "累计 " + data.total + " 抽，距 S 保底至多 " + Math.max(1, rules.pity - data.misses) + " 抽");
		    }))));
		root.then(Commands.literal("pending").then(
		    pool()
		        .executes(c -> run(c, () -> pending(c, c.getSource().getPlayerOrException())))
		        .then(Commands.argument("player", EntityArgument.player())
				          .requires(GachaCommand::admin)
				          .executes(c -> run(c, () -> pending(c, EntityArgument.getPlayer(c, "player")))))));
		root.then(Commands.literal("check")
		              .requires(GachaCommand::admin)
		              .then(pool().executes(c -> run(c, () -> {
			                                    GachaModule.current().config().pool(text(c, "pool"));
			                                    GachaModule.current().config().validate(c.getSource().getServer());
			                                    tell(c.getSource(),
												     "配置及原生奖励校验通过；外部命令在发奖时确认执行结果");
		                                    }))));
		var give = Commands.argument("players", EntityArgument.players());
		for (String kind : List.of("single", "ten"))
			give.then(
			    Commands.literal(kind)
			        .executes(c -> run(c, () -> give(c, kind, 1)))
			        .then(Commands.argument("count", IntegerArgumentType.integer(1, 2304))
					          .executes(c -> run(c, () -> give(c, kind, IntegerArgumentType.getInteger(c, "count"))))));
		root.then(Commands.literal("give").requires(GachaCommand::admin).then(give));
		var pools = Commands.literal("pool");
		pools.then(Commands.literal("list").executes(
		    c -> run(c, () -> {
			    var entries = GachaModule.current().config().pools;
			    if (entries.isEmpty())
				    tell(c.getSource(), "尚无奖池：/smp3 gacha pool set <name> <from> <to>");
			    entries.forEach((id, p)
				                    -> tell(c.getSource(), id + "  " + p.dimension + "  " + p.minX + " " + p.minY +
				                                               " " + p.minZ + " → " + p.maxX + " " + p.maxY + " " +
				                                               p.maxZ + "  朝向 " + p.displayFacing));
		    })));
		pools.then(
		    Commands.literal("set")
		        .requires(GachaCommand::admin)
		        .then(pool("name").then(
		            Commands.argument("from", BlockPosArgument.blockPos())
		                .then(Commands.argument("to", BlockPosArgument.blockPos())
						          .executes(c -> run(c, () -> {
			                                    var a = BlockPosArgument.getBlockPos(c, "from");
			                                    var b = BlockPosArgument.getBlockPos(c, "to");
			                                    GachaModule.current().edit(config -> {
				                                    var p = config.pools.computeIfAbsent(text(c, "name"),
													                                     ignored -> config.newPool());
				                                    p.dimension =
				                                        c.getSource().getLevel().dimension().identifier().toString();
				                                    p.minX = Math.min(a.getX(), b.getX());
				                                    p.maxX = Math.max(a.getX(), b.getX());
				                                    p.minY = Math.min(a.getY(), b.getY());
				                                    p.maxY = Math.max(a.getY(), b.getY());
				                                    p.minZ = Math.min(a.getZ(), b.getZ());
				                                    p.maxZ = Math.max(a.getZ(), b.getZ());
			                                    }, message -> tell(c.getSource(), message));
		                                    }))))));
		pools.then(Commands.literal("remove")
		               .requires(GachaCommand::admin)
		               .then(pool().executes(c -> run(c, () -> GachaModule.current().edit(config -> {
			                                     config.pool(text(c, "pool"));
			                                     config.pools.remove(text(c, "pool"));
		                                     }, message -> tell(c.getSource(), message))))));
		var facing = pool();
		for (var direction : GachaConfig.Facing.values())
			facing.then(Commands.literal(direction.id)
			                .executes(c
			                          -> run(c,
			                                 ()
			                                     -> GachaModule.current().edit(
			                                         config
			                                         -> config.pool(text(c, "pool")).displayFacing = direction.id,
			                                         message -> tell(c.getSource(), message)))));
		pools.then(Commands.literal("facing").requires(GachaCommand::admin).then(facing));
		root.then(pools).then(rewards(false)).then(rewards(true));
		var action = Commands.argument("action", IntegerArgumentType.integer(1));
		for (String resolution : List.of("retry", "done"))
			action.then(Commands.literal(resolution).executes(c -> run(c, () -> {
				                                                  GachaModule.current().recover(
				                                                      EntityArgument.getPlayer(c, "player").getUUID(),
				                                                      text(c, "pool"),
				                                                      UUID.fromString(text(c, "receipt")),
				                                                      IntegerArgumentType.getInteger(c, "action"),
				                                                      resolution.equals("done"));
				                                                  tell(c.getSource(), "核对结果已保存");
			                                                  })));
		var receipt = Commands.argument("receipt", StringArgumentType.word())
		                  .suggests((c, builder)
		                                -> SharedSuggestionProvider.suggest(
		                                    GachaLedger.read(EntityArgument.getPlayer(c, "player").getUUID())
		                                        .data()
		                                        .pending.stream()
		                                        .filter(r -> r.pool.equals(text(c, "pool")))
		                                        .map(r -> r.id.toString()),
		                                    builder))
		                  .then(action);
		root.then(Commands.literal("recover")
		              .requires(GachaCommand::owner)
		              .then(pool().then(Commands.argument("player", EntityArgument.player()).then(receipt))));
		return root;
	}
	private static LiteralArgumentBuilder<CommandSourceStack> rewards(boolean fixed) {
		var root = Commands.literal(fixed ? "bonus" : "reward").requires(GachaCommand::admin);
		root.then(Commands.literal("list").then(pool().executes(
		    c -> run(c, () -> {
			    var p = GachaModule.current().config().pool(text(c, "pool"));
			    for (Tier tier : Tier.values())
				    for (var prize : fixed ? p.fixedRewards.get(tier) : p.rewards.get(tier))
					    tell(c.getSource(), "[" + tier + "] " + prize.id + "  " + prize.label +
						                        (prize instanceof Prize r ? "  权重 " + r.weight + "  限额 " + r.limit
						                                                  : "  固定奖励"));
		    }))));
		for (String operation :
		     fixed ? List.of("sethand", "setcommand", "remove")
		           : List.of("sethand", "setcommand", "remove", "limit", "setdisplay", "cleardisplay")) {
			var command = Commands.literal(operation);
			if (operation.equals("setcommand"))
				command.requires(GachaCommand::owner);
			var pool = pool();
			for (Tier tier : Tier.values()) {
				var id = Commands.argument("id", StringArgumentType.word()).suggests((c, builder) -> {
					var p = GachaModule.current().config().pool(text(c, "pool"));
					return SharedSuggestionProvider.suggest(
					    (fixed ? p.fixedRewards.get(tier) : p.rewards.get(tier)).stream().map(r -> r.id), builder);
				});
				switch (operation) {
				case "sethand" -> {
					id.executes(c -> run(c, () -> hand(c, tier, fixed, 1, -1)));
					if (!fixed)
						id.then(
						    Commands.argument("weight", IntegerArgumentType.integer(1))
						        .executes(
						            c
						            -> run(c,
									       () -> hand(c, tier, false, IntegerArgumentType.getInteger(c, "weight"), -1)))
						        .then(Commands.argument("limit", IntegerArgumentType.integer(-1))
								          .executes(c
								                    -> run(c,
								                           ()
								                               -> hand(c, tier, false,
								                                       IntegerArgumentType.getInteger(c, "weight"),
								                                       IntegerArgumentType.getInteger(c, "limit"))))));
				}
				case "setcommand" ->
					id.then(Commands.argument("label", StringArgumentType.string())
					            .then(Commands.argument("command", StringArgumentType.greedyString())
					                      .executes(c -> run(c, () -> {
						                                Bonus prize = fixed ? new Bonus() : new Prize();
						                                prize.id = text(c, "id");
						                                prize.label = text(c, "label");
						                                prize.commands.add(text(c, "command"));
						                                replace(c, tier, fixed, prize);
					                                }))));
				case "limit" ->
					id.then(Commands.argument("limit", IntegerArgumentType.integer(-1))
					            .executes(c
					                      -> run(c,
					                             ()
					                                 -> GachaModule.current().edit(
					                                     config
					                                     -> prize(config, text(c, "pool"), tier, text(c, "id")).limit =
					                                            IntegerArgumentType.getInteger(c, "limit"),
					                                     message -> tell(c.getSource(), message)))));
				case "remove" ->
					id.executes(c -> run(c, () -> GachaModule.current().edit(config -> {
						            var p = config.pool(text(c, "pool"));
						            if (!(fixed ? p.fixedRewards.get(tier) : p.rewards.get(tier))
									         .removeIf(r -> r.id.equals(text(c, "id"))))
							            throw new IllegalArgumentException("奖品不存在");
					            }, message -> tell(c.getSource(), message))));
				default ->
					id.executes(c -> run(c, () -> {
						            var display = operation.equals("cleardisplay")
									                  ? null
									                  : capture(c.getSource().getPlayerOrException());
						            GachaModule.current().edit(
						                config
						                -> prize(config, text(c, "pool"), tier, text(c, "id")).displayItem = display,
						                message -> tell(c.getSource(), message));
					            }));
				}
				pool.then(Commands.literal(tier.name()).then(id));
			}
			root.then(command.then(pool));
		}
		return root;
	}
	private static Prize prize(GachaConfig config, String pool, Tier tier, String id) {
		return config.pool(pool)
		    .rewards.get(tier)
		    .stream()
		    .filter(p -> p.id.equals(id))
		    .findFirst()
		    .orElseThrow(() -> new IllegalArgumentException("奖品不存在"));
	}
	private static JsonObject capture(ServerPlayer player) {
		if (player.getMainHandItem().isEmpty())
			throw new IllegalArgumentException("请在主手拿着奖品");
		return ItemDefinitions.json(player.getMainHandItem(), player.registryAccess());
	}
	private static void hand(CommandContext<CommandSourceStack> c, Tier tier, boolean fixed, int weight, int limit)
	    throws Exception {
		var player = c.getSource().getPlayerOrException();
		var item = capture(player);
		item.addProperty("type", "item");
		Bonus reward;
		if (fixed)
			reward = new Bonus();
		else {
			var prize = new Prize();
			prize.weight = weight;
			prize.limit = limit;
			reward = prize;
		}
		reward.id = text(c, "id");
		reward.label =
		    player.getMainHandItem().getHoverName().getString() + " x " + player.getMainHandItem().getCount();
		reward.grants.add(item);
		replace(c, tier, fixed, reward);
	}
	private static void replace(CommandContext<CommandSourceStack> c, Tier tier, boolean fixed, Bonus prize) {
		GachaModule.current().edit(config -> {
			var pool = config.pool(text(c, "pool"));
			if (fixed) {
				var list = pool.fixedRewards.get(tier);
				list.removeIf(p -> p.id.equals(prize.id));
				list.add(prize);
			} else {
				var list = pool.rewards.get(tier);
				list.removeIf(p -> p.id.equals(prize.id));
				list.add((Prize)prize);
			}
		}, message -> tell(c.getSource(), message));
	}
	private static void give(CommandContext<CommandSourceStack> c, String kind, int count) throws Exception {
		for (var player : EntityArgument.getPlayers(c, "players")) {
			var item = ItemDefinitions.json(
			    new ItemStack(kind.equals("ten") ? Smp3Items.TICKET_TEN : Smp3Items.TICKET_SINGLE, count),
			    player.registryAccess());
			ItemDelivery.transact(player, inventory -> true, List.of(item), state -> {}, Map.of());
		}
		tell(c.getSource(), "抽奖券已发放，背包满时进入待发队列");
	}
	private static void pending(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		boolean found = false;
		for (var round : GachaLedger.read(player.getUUID()).data().pending)
			if (round.pool.equals(text(c, "pool"))) {
				if (!round.delivered) {
					tell(c.getSource(), round.id + "  等待开奖/原生结算");
					found = true;
				}
				for (int i = 0; i < round.commands.size(); i++)
					if (round.commands.get(i).status != GachaLedger.Status.DONE) {
						tell(c.getSource(), round.id + "  动作 " + (i + 1) + "  " + round.commands.get(i).status);
						found = true;
					}
			}
		if (!found)
			tell(c.getSource(), "本池结算已完成；统一物品待发队列 " + ItemDelivery.pending(player).size() + " 组");
	}
	private static void history(CommandContext<CommandSourceStack> c, int page, boolean details) throws Exception {
		GachaModule.current().history(c.getSource().getPlayerOrException(), text(c, "pool"), page, details);
	}
	private static void help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal("投券到奖池区域开始抽奖").withStyle(ChatFormatting.GOLD), false);
		for (String command : List.of("history <pool> [details] [page]", "pity <pool>", "pending <pool>"))
			tell(source, "/smp3 gacha " + command);
		if (admin(source))
			for (String command : List.of("give <players> <single|ten> [count]", "pool <set|remove|facing|list>",
			                              "reward <list|sethand|setcommand|remove|limit|setdisplay|cleardisplay>",
			                              "bonus <list|sethand|setcommand|remove>", "check <pool>", "reload"))
				tell(source, "/smp3 gacha " + command);
		if (owner(source))
			tell(source, "/smp3 gacha recover <pool> <player> <receipt> <action> <retry|done>");
	}
}
