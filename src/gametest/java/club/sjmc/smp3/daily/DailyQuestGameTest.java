package club.sjmc.smp3.daily;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.SguiUtils;
import eu.pb4.sgui.api.gui.SimpleGui;
import io.netty.channel.embedded.EmbeddedChannel;

import club.sjmc.smp3.FeatureChecks;
import club.sjmc.smp3.daily.ui.DailyQuestGui;
import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.item.ItemDelivery;
import club.sjmc.smp3.sign.ui.SignGui;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.Prediction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class DailyQuestGameTest {
	@GameTest(maxTicks = 200)
	public void gameplay(GameTestHelper test) throws Exception {
		ServerLevel level = test.getLevel();
		ServerPlayer player = (ServerPlayer)test.makeMockServerPlayer(GameType.SURVIVAL);
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel(connection);
		var server = level.getServer();
		server.getPlayerList().placeNewPlayer(connection, player,
		                                      CommonListenerCookie.createInitial(player.getGameProfile(), false));
		try {
			player.setGameMode(GameType.SURVIVAL);
			BlockPos pos = test.absolutePos(new BlockPos(1, 2, 1));
			player.setPos(Vec3.atCenterOf(pos.offset(3, 0, 0)));
			DailyQuestService service = DailyQuestModule.current();
			test.assertTrue(service != null && service.state(player) != null, "任务服务启动失败");
			QuestDay.Assignment task = select(player, "zombie_a");
			var skeleton = test.spawnWithNoFreeWill(EntityTypes.SKELETON, new BlockPos(1, 2, 1));
			skeleton.hurtServer(level, player.damageSources().playerAttack(player), 1000);
			test.assertTrue(task.progress == 0, "错误生物计入击杀");
			var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
			zombie.hurtServer(level, player.damageSources().playerAttack(player), 1000);
			test.assertTrue(task.progress == 1, "击杀事件未计数");
			skeleton.discard();
			zombie.discard();
			var arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
			zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
			zombie.hurtServer(level, player.damageSources().arrow(arrow, player), 1000);
			test.assertTrue(task.progress == 2, "玩家投射物未计入击杀");
			zombie.discard();

			task = select(player, "stone_a");
			level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
			player.gameMode.destroyBlock(pos);
			test.assertTrue(task.progress == 0, "错误方块计入挖矿");
			level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
			player.gameMode.destroyBlock(pos);
			test.assertTrue(task.progress == 1, "成功挖矿未计数");
			BlockPos cancelled = pos.offset(1, 0, 0);
			PlayerBlockBreakEvents.BEFORE.register(
			    (world, who, at, state, entity) -> !who.getUUID().equals(player.getUUID()) || !at.equals(cancelled));
			level.setBlockAndUpdate(cancelled, Blocks.STONE.defaultBlockState());
			test.assertTrue(!player.gameMode.destroyBlock(cancelled) && task.progress == 1, "取消破坏仍推进任务");
			for (GameType mode : List.of(GameType.CREATIVE, GameType.SPECTATOR)) {
				ServerPlayer excluded = (ServerPlayer)test.makeMockServerPlayer(mode);
				QuestDay.Assignment ignored = select(excluded, "stone_a");
				DailyQuestEvents.block(excluded, "block_broken", pos, Blocks.STONE.defaultBlockState());
				DailyQuestData.get(server).players.remove(excluded.getUUID());
				test.assertTrue(ignored.progress == 0, "非生存模式推进任务");
			}

			task = select(player, "carrot_b");
			level.setBlockAndUpdate(pos.below(), Blocks.FARMLAND.defaultBlockState());
			level.setBlockAndUpdate(pos, Blocks.CARROTS.defaultBlockState());
			player.gameMode.destroyBlock(pos);
			test.assertTrue(task.progress == 0, "未成熟作物被计数");
			level.setBlockAndUpdate(pos, Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 7));
			player.gameMode.destroyBlock(pos);
			test.assertTrue(task.progress == 1, "成熟作物未计数");

			task = select(player, "build_a");
			level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 4));
			CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(player, pos, player.getMainHandItem());
			test.assertTrue(task.progress == 0, "非放置事件被计数");
			var context = new BlockPlaceContext(
			    player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
			    new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false));
			test.assertTrue(((BlockItem)Items.OAK_PLANKS).place(context).consumesAction(), "测试方块放置失败");
			test.assertTrue(task.progress == 1, "方块放置 Mixin 未触发");

			task = select(player, "craft_planks_a");
			var inventory = player.inventoryMenu;
			inventory.getCraftSlots().setItem(0, new ItemStack(Items.OAK_LOG));
			test.assertTrue(inventory.getSlot(0).getItem().is(Items.OAK_PLANKS) && task.progress == 0,
			                "合成预览计入进度或未生成结果");
			inventory.clicked(0, 0, ContainerInput.PICKUP, player);
			test.assertTrue(task.progress == 4, "背包合成未按实际产物计数");
			inventory.setCarried(ItemStack.EMPTY);
			inventory.getCraftSlots().setItem(0, new ItemStack(Items.OAK_PLANKS));
			inventory.getCraftSlots().setItem(2, new ItemStack(Items.OAK_PLANKS));
			inventory.clicked(0, 0, ContainerInput.PICKUP, player);
			test.assertTrue(task.progress == 4, "错误合成产物计入进度");
			inventory.setCarried(ItemStack.EMPTY);
			level.setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
			var crafting = new CraftingMenu(5, player.getInventory(), ContainerLevelAccess.create(level, pos));
			crafting.getInputGridSlots().getFirst().set(new ItemStack(Items.BIRCH_LOG));
			crafting.clicked(0, 0, ContainerInput.PICKUP, player);
			test.assertTrue(task.progress == 8, "工作台合成或标签匹配错误");
			crafting.setCarried(ItemStack.EMPTY);
			crafting.getInputGridSlots().getFirst().set(new ItemStack(Items.OAK_LOG, 2));
			crafting.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
			test.assertTrue(task.progress == 16 && task.complete(), "工作台批量合成数量错误");
			task = select(player, "craft_planks_a");
			inventory.getCraftSlots().setItem(0, new ItemStack(Items.OAK_LOG, 2));
			inventory.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
			test.assertTrue(task.progress == 8, "背包批量合成重复或遗漏计数");
			DailyQuestGui.show(player);
			var craftingGui = (SimpleGui)SguiUtils.getCurrentGui(player);
			String description = craftingGui.getGuiElement(10)
			                         .getItemStack()
			                         .get(DataComponents.LORE)
			                         .lines()
			                         .stream()
			                         .map(Component::getString)
			                         .collect(Collectors.joining());
			test.assertTrue(description.contains("16") && description.contains("任意木材") &&
			                    !description.contains("{count}"),
			                "任务说明未使用目标快照替换占位符");
			player.closeContainer();

			ProductionChecks.run(test, player, pos);

			task = select(player, "fishing_b");
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
			FishingHook empty = new FishingHook(player, level, 0, 0);
			empty.retrieve(player.getMainHandItem());
			test.assertTrue(task.progress == 0, "空收竿被计数");
			FishingHook catchHook = new FishingHook(player, level, 0, 0);
			var nibble = FishingHook.class.getDeclaredField("nibble");
			nibble.setAccessible(true);
			nibble.setInt(catchHook, 10);
			catchHook.retrieve(player.getMainHandItem());
			test.assertTrue(task.progress == 1, "钓鱼收获 Mixin 未触发");

			task = select(player, "villager_b");
			var villager = test.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
			var offer = new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD), 12, 1, .05F);
			villager.notifyTrade(offer);
			test.assertTrue(task.progress == 0, "无玩家交易被计数");
			villager.setTradingPlayer(player);
			villager.notifyTrade(offer);
			villager.notifyTrade(offer);
			test.assertTrue(task.progress == 2, "村民交易次数错误");

			task = select(player, "piglin_c");
			Piglin piglin = test.spawnWithNoFreeWill(EntityTypes.PIGLIN, new BlockPos(1, 2, 1));
			piglin.setBaby(false);
			piglin.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GOLD_INGOT));
			var barter = PiglinAi.class.getDeclaredMethod("stopHoldingOffHandItem", ServerLevel.class, Piglin.class,
			                                              boolean.class);
			barter.setAccessible(true);
			barter.invoke(null, level, piglin, true);
			test.assertTrue(task.progress == 0, "未归属交易被计数");
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT, 2));
			PiglinAi.mobInteract(level, piglin, player, InteractionHand.MAIN_HAND);
			barter.invoke(null, level, piglin, true);
			test.assertTrue(task.progress == 1, "猪灵完成交易未计数");
			var gold = player.drop(new ItemStack(Items.GOLD_INGOT, 3), true, Prediction.SERVER_ONLY);
			test.assertTrue(gold.getAttached(BarterAttribution.OWNER).equals(player.getUUID()) &&
			                    ItemStack.isSameItemSameComponents(gold.getItem(), new ItemStack(Items.GOLD_INGOT)),
			                "Dropped gold attribution changed its stack");
			var pickup =
			    PiglinAi.class.getDeclaredMethod("pickUpItem", ServerLevel.class, Piglin.class, ItemEntity.class);
			pickup.setAccessible(true);
			pickup.invoke(null, level, piglin, gold);
			var savedPiglin = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
			piglin.saveWithoutId(savedPiglin);
			piglin.removeAttached(BarterAttribution.OWNER);
			piglin.load(
			    TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), savedPiglin.buildResult()));
			barter.invoke(null, level, piglin, true);
			test.assertTrue(task.progress == 2 && gold.getItem().getCount() == 2 &&
			                    gold.getAttached(BarterAttribution.OWNER).equals(player.getUUID()),
			                "Ground barter or pending ownership persistence failed");
			QuestUpdateChecks.ownership(test, player, gold);

			select(player, "forest_c");
			StructureChecks.run(test, player);
			QuestDay day = service.state(player);
			var forest =
			    service.config.tasks().stream().filter(t -> t.id().equals("forest_c")).findFirst().orElseThrow();
			JsonObject conditions = new JsonObject();
			conditions.addProperty("dimension", "minecraft:the_nether");
			var exploration = new QuestDefinition("explore_test", "探索测试", null, "exploration", "C", forest.icon(),
			                                      new QuestDefinition.Objective("biome_visited", conditions), null,
			                                      List.of(), List.of(), null, List.of(), "all", List.of());
			task = new QuestDay.Assignment(exploration, 1, service.config.rewards().get("C"), Set.of(), new Random(1));
			day.assignments.set(0, task);
			DailyQuestEvents.explore(player);
			test.assertTrue(task.progress == 0, "错误维度探索被计数");
			conditions.remove("dimension");
			conditions.addProperty("biome", level.getBiome(player.blockPosition()).getRegisteredName());
			exploration = new QuestDefinition("explore_test", "探索测试", null, "exploration", "C", forest.icon(),
			                                  new QuestDefinition.Objective("biome_visited", conditions), null,
			                                  List.of(), List.of(), null, List.of(), "all", List.of());
			task = new QuestDay.Assignment(exploration, 1, service.config.rewards().get("C"), Set.of(), new Random(1));
			day.assignments.set(0, task);
			DailyQuestEvents.explore(player);
			test.assertTrue(task.complete(), "探索目标未完成");

			var data = DailyQuestData.get(server);
			QuestDay yesterday = new QuestDay(day.date - 1, 0, 0, Set.of(), service.config, new Random(1));
			yesterday.assignments.clear();
			yesterday.assignments.add(task);
			Smp3AccountStore.commitReward(player.getUUID(), "questsAndPacks",
			                              Smp3AccountStore.rewardState(player.getUUID(), "questsAndPacks"),
			                              new JsonObject(), Map.of());
			data.players.put(player.getUUID(), yesterday);
			day = service.state(player);
			task = new QuestDay.Assignment(exploration, 1, service.config.rewards().get("C"), Set.of(), new Random(1));
			task.progress = task.target;
			day.assignments.set(0, task);
			long stampsBefore = Smp3AccountStore.balance(player.getUUID(), Smp3Currency.COFFEE_STAMP);
			Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.TONGBAO, Long.MAX_VALUE);
			service.settle(player);
			test.assertTrue(!task.claimed && QuestRewards.ready(player).size() == 2 &&
			                    day.retryAt > level.getGameTime() &&
			                    Smp3AccountStore.balance(player.getUUID(), Smp3Currency.COFFEE_STAMP) == stampsBefore,
			                "Failed automatic payment lost a pending reward or partially credited currency");
			Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.TONGBAO, 0);
			service.settle(player);
			test.assertTrue(!task.claimed, "Automatic reward retry ignored its cooldown");
			day = QuestConfig.JSON.fromJson(QuestConfig.JSON.toJson(day), QuestDay.class);
			data.players.put(player.getUUID(), day);
			task = day.assignments.getFirst();
			service.settle(player);
			service.settle(player);
			test.assertTrue(task.claimed && QuestRewards.ready(player).isEmpty() &&
			                    Smp3AccountStore.balance(player.getUUID(), Smp3Currency.TONGBAO) ==
			                        2 * Smp3Currency.rawUnits(task.reward.tongbao()) &&
			                    Smp3AccountStore.balance(player.getUUID(), Smp3Currency.COFFEE_STAMP) ==
			                        stampsBefore + 200,
			                "Automatic reward recovery duplicated or lost old/new day payments");
			task.claimed = false;
			task.progress = 0;
			test.assertTrue(service.state(player).assignments.getFirst().claimed, "账本回执未恢复领取状态");
			day.assignments.get(1).progress = day.assignments.get(1).target;
			var completed = day.assignments.get(1);
			day.rerollsUsed = service.config.dailyRerollLimit();
			var commands = server.getCommands().getDispatcher();
			String playerName = player.getGameProfile().name();
			var console = server.createCommandSourceStack();
			for (String root : List.of("smp3 dailyquest", "smp3 dailyquests", "smp3 dailyrequest")) {
				for (String prefix : List.of("", playerName.substring(0, 2))) {
					var parsed = commands.parse(root + " " + prefix, console);
					var suggestions = commands.getCompletionSuggestions(parsed).join().getList();
					test.assertTrue(suggestions.stream().anyMatch(s -> s.getText().equals(playerName)),
					                "Online player completion missing: " + root);
				}
				var completions = Map.of(root + " " + playerName + " r", List.of("reroll", "reset"),
				                         root + " " + playerName + " reset ", List.of("1", "2", "3", "4"));
				for (var entry : completions.entrySet()) {
					var parsed = commands.parse(entry.getKey(), console);
					var suggestions = commands.getCompletionSuggestions(parsed).join().getList();
					test.assertTrue(suggestions.stream().map(s -> s.getText()).toList().equals(entry.getValue()),
					                "Command argument completion missing: " + entry.getKey());
				}
			}
			String reset = "smp3 dailyquest " + player.getGameProfile().name() + " reroll";
			for (String invalid : List.of(reset + " 0", reset + " 5", "smp3 dailyquest nobody-online reset")) {
				try {
					commands.execute(invalid, server.createCommandSourceStack());
					throw new AssertionError("无效命令被接受：" + invalid);
				} catch (CommandSyntaxException expected) {
				}
			}
			for (String root : List.of("smp3 dailyquest", "smp3 dailyquests", "smp3 dailyrequest")) {
				try {
					commands.execute(root + " " + playerName + " reset", player.createCommandSourceStack());
					throw new AssertionError("未授权玩家能执行管理命令");
				} catch (CommandSyntaxException expected) {
				}
			}
			var old = day.assignments.get(2);
			var operator = player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER);
			test.assertTrue(commands.execute(reset + " 3", operator) == 1, "OP reset failed without LuckPerms");
			test.assertTrue(day.assignments.get(2) != old && day.assignments.get(1) == completed,
			                "指定刷新修改了其他槽位");
			test.assertTrue(!service.reroll(player, day.date, 2, old.token, false).success(), "过期任务回调被接受");
			test.assertTrue(
			    commands.execute("smp3 dailyquests " + playerName + " reroll", server.createCommandSourceStack()) == 2,
			    "批量刷新未正确跳过已完成任务");
			test.assertTrue(day.assignments.getFirst() == task && day.assignments.get(1) == completed,
			                "刷新改动已领取或已完成任务");
			test.assertTrue(day.rerollsUsed == service.config.dailyRerollLimit(), "管理员改动个人额度");
			day.rerollsUsed = 0;
			test.assertTrue(DailyQuestGui.show(player), "每日任务 GUI 打开失败");
			SimpleGui overview = (SimpleGui)SguiUtils.getCurrentGui(player);
			overview.getGuiElement(32).getGuiCallback().click(32, ClickType.MOUSE_LEFT, ContainerInput.PICKUP,
			                                                  overview);
			SimpleGui confirmation = (SimpleGui)SguiUtils.getCurrentGui(player);
			test.assertTrue(confirmation != overview && day.rerollsUsed == 0, "进入确认页消耗额度");
			var staleConfirm = confirmation.getGuiElement(12);
			commands.execute(reset + " 3", server.createCommandSourceStack());
			old = day.assignments.get(2);
			confirmation.onTick();
			test.assertTrue(SguiUtils.getCurrentGui(player) == overview, "管理员刷新后确认页未返回总览");
			staleConfirm.getGuiCallback().click(12, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, confirmation);
			test.assertTrue(day.assignments.get(2) == old && day.rerollsUsed == 0, "旧确认回调修改新任务");
			overview.getGuiElement(32).getGuiCallback().click(32, ClickType.MOUSE_LEFT, ContainerInput.PICKUP,
			                                                  overview);
			player.closeContainer();
			test.assertTrue(day.rerollsUsed == 0 && day.assignments.get(2) == old, "关闭确认页消耗额度");
			commands.execute("smp3 dailyquest", player.createCommandSourceStack());
			player.closeContainer();
			player.closeContainer();
			SignGui.open(player);
			player.closeContainer();
			service.settle(player);
			long totalBeforeReset = QuestRewards.progress(player).completed();
			long balanceBeforeReset = Smp3AccountStore.balance(player.getUUID(), Smp3Currency.TONGBAO);
			var resetTask = service.state(player).assignments.getFirst();
			String definitionBeforeReset = QuestConfig.JSON.toJson(resetTask.definition);
			String rewardBeforeReset = QuestConfig.JSON.toJson(resetTask.reward);
			int allowance = service.state(player).rerollsUsed;
			var blockedWrite =
			    FabricLoader.getInstance().getConfigDir().resolve("smp3/accounts/" + player.getUUID() + ".json.tmp");
			Files.createDirectory(blockedWrite);
			try {
				service.reset(player, 0);
				throw new AssertionError("Reset accepted a failed ledger write");
			} catch (IllegalStateException expected) {
				test.assertTrue(service.state(player).assignments.getFirst() == resetTask && resetTask.claimed,
				                "Failed reset changed the active task");
			} finally {
				Files.delete(blockedWrite);
			}
			test.assertTrue(commands.execute("smp3 dailyrequest " + playerName + " reset 1", console) == 1,
			                "Completed task reset failed");
			day = service.state(player);
			test.assertTrue(
			    !day.assignments.getFirst().complete() && !day.assignments.getFirst().claimed &&
			        QuestConfig.JSON.toJson(day.assignments.getFirst().definition).equals(definitionBeforeReset) &&
			        QuestConfig.JSON.toJson(day.assignments.getFirst().reward).equals(rewardBeforeReset) &&
			        day.rerollsUsed == allowance && QuestRewards.progress(player).completed() == totalBeforeReset &&
			        Smp3AccountStore.balance(player.getUUID(), Smp3Currency.TONGBAO) == balanceBeforeReset,
			    "Reset changed task definition, rewards, totals or allowance");
			service.settle(player);
			test.assertTrue(!service.state(player).assignments.getFirst().complete(),
			                "Old receipt restored reset task");
			var persistedReset = QuestConfig.JSON.fromJson(
			    Smp3AccountStore.rewardState(player.getUUID(), ItemDelivery.LEDGER_KEY, "questDay").get("questDay"),
			    QuestDay.class);
			test.assertTrue(!persistedReset.assignments.getFirst().complete() &&
			                    !QuestRewards.progress(player).paid(day.date, 0),
			                "Reset was not persisted with its receipts");
			day.assignments.getFirst().progress = day.assignments.getFirst().target;
			service.settle(player);
			service.settle(player);
			test.assertTrue(Smp3AccountStore.balance(player.getUUID(), Smp3Currency.TONGBAO) ==
			                    balanceBeforeReset + Smp3Currency.rawUnits(resetTask.reward.tongbao()),
			                "Replayed completion paid more than once");
			test.assertTrue(commands.execute("smp3 dailyquest " + playerName + " reset", console) == 4,
			                "Reset did not include all slots");
			QuestUpdateChecks.rewards(test, player);
			FeatureChecks.run(test, player);
			data = DailyQuestData.get(server);
			var savedDay = data.players.get(player.getUUID());
			data.setDirty(player.getUUID());
			data.save();
			DailyQuestData.clear();
			DailyQuestData restored = DailyQuestData.get(server);
			test.assertTrue(QuestConfig.JSON.toJsonTree(restored.player(player.getUUID()))
			                    .equals(QuestConfig.JSON.toJsonTree(savedDay)),
			                "Persisted quest snapshot changed after reload");
			test.succeed();
		} finally {
			DailyQuestData data = DailyQuestData.get(server);
			data.players.remove(player.getUUID());
			data.setDirty(player.getUUID());
			server.getPlayerList().remove(player);
			channel.finishAndReleaseAll();
		}
	}

	private static QuestDay.Assignment select(ServerPlayer player, String id) {
		DailyQuestService service = DailyQuestModule.current();
		QuestDay day = service.state(player);
		Smp3AccountStore.commitReward(player.getUUID(), "questsAndPacks",
		                              Smp3AccountStore.rewardState(player.getUUID(), "questsAndPacks"),
		                              new JsonObject(), Map.of());
		player.removeAttached(ItemDelivery.APPLIED);
		QuestDefinition definition =
		    service.config.tasks().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
		QuestDay.Assignment assignment = new QuestDay.Assignment(
		    definition, 1, service.config.rewards().get(definition.difficulty()), Set.of(), new Random(1));
		day.assignments.set(0, assignment);
		return assignment;
	}
}
