package club.sjmc.smp3.daily;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.item.ItemDelivery;
import club.sjmc.smp3.item.RewardBundle;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.InteractionResult;
import club.sjmc.smp3.block.NetherReactorCoreBlock;
import club.sjmc.smp3.block.Smp3Blocks;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;

final class QuestExpansionChecks {
	static void run(GameTestHelper test, ServerPlayer player) {
		var level = player.level();
		var hook = new FishingHook(EntityTypes.FISHING_BOBBER, level);
		var rod = new ItemStack(Items.FISHING_ROD);
		var treasureRod = rod.copy();
		treasureRod.enchant(
		    player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LUCK_OF_THE_SEA), 1);
		for (String kind : List.of("junk", "treasure")) {
			var fishing = watch(player, "fish_caught", "{\"loot\":\"" + kind + "\"}");
			CriteriaTriggers.FISHING_ROD_HOOKED.trigger(player, rod, hook, List.of(rod));
			test.assertTrue(fishing.progress() == (kind.equals("junk") ? 1 : 0), "Junk rod counted as treasure");
			CriteriaTriggers.FISHING_ROD_HOOKED.trigger(player, rod, hook, List.of(treasureRod));
			test.assertTrue(fishing.progress() == 1, "Treasure rod counted as junk");
			CriteriaTriggers.FISHING_ROD_HOOKED.trigger(player, rod, hook,
			                                            List.of(new ItemStack(Items.ENCHANTED_BOOK)));
			test.assertTrue(fishing.progress() == (kind.equals("junk") ? 1 : 2),
			                "Enchanted book classification failed");
		}
		var catches = watch(player, "fish_caught", "{}");
		CriteriaTriggers.FISHING_ROD_HOOKED.trigger(player, rod, hook, List.of(new ItemStack(Items.INK_SAC, 10)));
		test.assertTrue(catches.progress() == 1, "One catch counted multiple items");
		var cow = test.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(1, 2, 1));
		var task = watch(player, "entity_bred", "{\"entity\":\"minecraft:cow\"}");
		CriteriaTriggers.BRED_ANIMALS.trigger(player, cow, cow, null);
		test.assertTrue(task.progress() == 1, "Breeding success hook missed");
		var wolf = test.spawnWithNoFreeWill(EntityTypes.WOLF, new BlockPos(1, 2, 1));
		task = watch(player, "entity_tamed", "{\"entity\":\"minecraft:wolf\"}");
		CriteriaTriggers.TAME_ANIMAL.trigger(player, wolf);
		test.assertTrue(task.progress() == 1, "Taming success hook missed");
		var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
		task = watch(player, "entity_converted", "{\"entity\":\"minecraft:zombie\",\"result\":\"minecraft:drowned\"}");
		var origin = zombie.position();
		player.setPos(origin.add(16, 0, 0));
		zombie.convertTo(EntityTypes.DROWNED, ConversionParams.single(zombie, false, false), converted -> {});
		test.assertTrue(task.progress() == 1, "Conversion at the range boundary was not credited");
		var another = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
		player.setPos(origin.add(17, 0, 0));
		another.convertTo(EntityTypes.DROWNED, ConversionParams.single(another, false, false), converted -> {});
		test.assertTrue(task.progress() == 1, "Conversion beyond 16 blocks was credited");
		var nearer = (ServerPlayer)test.makeMockServerPlayer(GameType.SURVIVAL);
		var connection = new Connection(PacketFlow.SERVERBOUND);
		var channel = new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(
		    connection, nearer, CommonListenerCookie.createInitial(nearer.getGameProfile(), false));
		try {
			var nearestTask =
			    watch(nearer, "entity_converted", "{\"entity\":\"minecraft:zombie\",\"result\":\"minecraft:drowned\"}");
			player.setPos(origin.add(4, 0, 0));
			nearer.setPos(origin.add(2, 0, 0));
			another = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
			another.convertTo(EntityTypes.DROWNED, ConversionParams.single(another, false, false), converted -> {});
			test.assertTrue(nearestTask.progress() == 1 && task.progress() == 1,
			                "Conversion did not credit only the nearest eligible player");
			watch(nearer, "entity_converted", "{\"entity\":\"minecraft:piglin\"}");
			another = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
			another.convertTo(EntityTypes.DROWNED, ConversionParams.single(another, false, false), converted -> {});
			test.assertTrue(task.progress() == 2, "Closer player without a matching goal blocked credit");
		} finally {
			DailyQuestData.get(level.getServer()).players.remove(nearer.getUUID());
			level.getServer().getPlayerList().remove(nearer);
			channel.finishAndReleaseAll();
		}
		var core = test.absolutePos(new BlockPos(2, 4, 2));
		var reactor = Smp3Blocks.NETHER_REACTOR_CORE;
		level.setBlockAndUpdate(core, reactor.defaultBlockState());
		test.assertTrue(QuestStructures.activate(player, core) == InteractionResult.PASS,
		                "Reactor activated without an active quest");
		task = watch(player, "reactor_activated", "{}");
		QuestStructures.activate(player, core);
		test.assertTrue(task.progress() == 0 && level.getBlockState(core).getValue(NetherReactorCoreBlock.PHASE) == 0,
		                "Incomplete reactor changed state or progress");
		String[][] layers = {{"GCG", "CCC", "GCG"}, {"C C", " R ", "C C"}, {" C ", "CCC", " C "}};
		for (int y = 0; y < 3; y++)
			for (int z = 0; z < 3; z++)
				for (int x = 0; x < 3; x++) {
					var block = switch (layers[y][z].charAt(x)) {
						case 'G' -> Blocks.GOLD_BLOCK;
						case 'C' -> Blocks.COBBLESTONE;
						case 'R' -> reactor;
						default -> Blocks.AIR;
					};
					level.setBlockAndUpdate(core.offset(x - 1, y - 1, z - 1), block.defaultBlockState());
				}
		QuestStructures.activate(player, core);
		QuestStructures.activate(player, core);
		var activeCore = level.getBlockState(core);
		test.assertTrue(task.progress() == 1 && activeCore.getValue(NetherReactorCoreBlock.PHASE) == 1 &&
		                    level.getBlockState(core.below()).is(Blocks.NETHERRACK) &&
		                    level.getBlockTicks().hasScheduledTick(core, reactor),
		                "Reactor failed to activate exactly once or schedule completion");
		activeCore.tick(level, core, level.getRandom());
		test.assertTrue(level.getBlockState(core).getValue(NetherReactorCoreBlock.PHASE) == 2 &&
		                    QuestStructures.activate(player, core) == InteractionResult.PASS,
		                "Finished reactor could activate again");
		var registry = player.registryAccess();
		var grants = RewardBundle.objects(JsonParser.parseString("""
		    [{"type":"loot_table","id":"smp3:quests/shipwreck","rolls":3}]
		    """).getAsJsonArray());
		var loot = RewardBundle.resolve(grants, new Random(1), player);
		test.assertTrue(loot.items().stream().mapToInt(item -> item.get("count").getAsInt()).sum() == 3,
		                "Three shipwreck reward rolls must yield exactly three items");
		player.getInventory().clearContent();
		for (int i = 0; i < 36; i++)
			player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		var effect = JsonParser.parseString("""
		    {"type":"equipment","slot":"head","item":{"id":"minecraft:carved_pumpkin","count":1}}
		    """).getAsJsonObject();
		ItemDelivery.transact(
		    player, inventory -> true, List.of(), state -> ItemDelivery.queueEffects(state, List.of(effect)), Map.of());
		ItemDelivery.deliver(player);
		test.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN) &&
		                    ItemDelivery.pending(player).stream().anyMatch(item -> item.is(Items.DIAMOND_HELMET)),
		                "Full inventory lost the original helmet");
		ItemDelivery.deliver(player);
		test.assertTrue(ItemDelivery.pending(player)
		                        .stream()
		                        .filter(item -> item.is(Items.DIAMOND_HELMET))
		                        .mapToInt(ItemStack::getCount)
		                        .sum() == 1,
		                "Equipment retry duplicated helmet");
		player.getInventory().clearContent();
		ItemDelivery.deliver(player);
		for (BlockPos pos :
		     BlockPos.betweenClosed(player.blockPosition().offset(-4, -1, -4), player.blockPosition().offset(4, 2, 4)))
			level.setBlockAndUpdate(pos, pos.getY() < player.blockPosition().getY() ? Blocks.STONE.defaultBlockState()
			                                                                        : Blocks.AIR.defaultBlockState());
		var effects = RewardBundle.objects(JsonParser.parseString("""
		    [{"type":"entity","id":"minecraft:dolphin","count":1}, {"type":"entity","id":"minecraft:mooshroom","count":1}]
		    """).getAsJsonArray());
		int before = level.getEntitiesOfClass(MushroomCow.class, player.getBoundingBox().inflate(8)).size();
		ItemDelivery.transact(
		    player, inventory -> true, List.of(), state -> ItemDelivery.queueEffects(state, effects), Map.of());
		ItemDelivery.deliver(player);
		test.assertTrue(ItemDelivery.pendingEffects(player).size() == 1 &&
		                    level.getEntitiesOfClass(MushroomCow.class, player.getBoundingBox().inflate(8)).size() ==
		                        before + 1,
		                "Pending aquatic spawn blocked a valid animal reward");
		ItemDelivery.deliver(player);
		test.assertTrue(level.getEntitiesOfClass(MushroomCow.class, player.getBoundingBox().inflate(8)).size() ==
		                    before + 1,
		                "Retry duplicated a successful entity reward");
		var water = player.blockPosition().offset(2, 0, 2);
		level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
		level.setBlockAndUpdate(water.above(), Blocks.WATER.defaultBlockState());
		ItemDelivery.deliver(player);
		test.assertTrue(ItemDelivery.pendingEffects(player).isEmpty(), "Aquatic reward did not retry in valid water");
		var batch = JsonParser.parseString("""
		    {"type":"entity","id":"minecraft:mooshroom","count":64}
		    """).getAsJsonObject();
		before = level.getEntitiesOfClass(MushroomCow.class, player.getBoundingBox().inflate(8)).size();
		ItemDelivery.transact(
		    player, inventory -> true, List.of(), state -> ItemDelivery.queueEffects(state, List.of(batch)), Map.of());
		for (int i = 0; i < 20; i++)
			ItemDelivery.deliver(player);
		int delivered = level.getEntitiesOfClass(MushroomCow.class, player.getBoundingBox().inflate(8)).size() - before;
		int remaining = ItemDelivery.pendingEffects(player).stream().mapToInt(e -> e.get("count").getAsInt()).sum();
		test.assertTrue(delivered <= 16 && remaining + delivered == 64 && remaining > 0,
		                "Repeated delivery bypassed the tick budget or lost a deferred reward");
	}
	private static QuestDay.Assignment watch(ServerPlayer player, String event, String filters) {
		var goal = new QuestDefinition.Goal("Event", event, null, JsonParser.parseString(filters).getAsJsonObject(),
		                                    new JsonPrimitive(100), List.of(), "amount", "any");
		var definition =
		    new QuestDefinition("event_test", "Event", null, "world", "C", new JsonPrimitive("minecraft:book"),
			                    List.of(goal), "all", List.of(), List.of(), null, 1);
		var task = new QuestDay.Assignment(definition, 1, new QuestReward(0, 0), Set.of(), new Random(1), player);
		Smp3AccountStore.commitReward(player.getUUID(), ItemDelivery.LEDGER_KEY,
		                              Smp3AccountStore.rewardState(player.getUUID(), ItemDelivery.LEDGER_KEY),
		                              new JsonObject(), Map.of());
		player.removeAttached(ItemDelivery.APPLIED);
		DailyQuestModule.current().state(player).assignments.set(0, task);
		return task;
	}
}
