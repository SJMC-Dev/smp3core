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

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

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
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, zombie, null);
		test.assertTrue(zombie.getUUID().equals(task.binding(0)) && task.progress() == 0,
		                "Designation changed progress or failed to bind");
		zombie.convertTo(EntityTypes.DROWNED, ConversionParams.single(zombie, false, false), converted -> {});
		test.assertTrue(task.progress() == 1, "Native conversion lost the designated player");
		var another = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
		UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, another, null);
		task.reset();
		another.convertTo(EntityTypes.DROWNED, ConversionParams.single(another, false, false), converted -> {});
		test.assertTrue(task.progress() == 0 && task.binding(0) == null, "Reset retained conversion attribution");
		player.setShiftKeyDown(false);
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
		var definition = new QuestDefinition("event_test", "Event", null, "world", "C",
		                                     new JsonPrimitive("minecraft:book"), List.of(goal),
		                                     "all", List.of(), List.of(), null, 1);
		var task = new QuestDay.Assignment(definition, 1, new QuestReward(0, 0), Set.of(), new Random(1), player);
		Smp3AccountStore.commitReward(player.getUUID(), ItemDelivery.LEDGER_KEY,
		                              Smp3AccountStore.rewardState(player.getUUID(), ItemDelivery.LEDGER_KEY),
		                              new JsonObject(), Map.of());
		player.removeAttached(ItemDelivery.APPLIED);
		DailyQuestModule.current().state(player).assignments.set(0, task);
		return task;
	}
}
