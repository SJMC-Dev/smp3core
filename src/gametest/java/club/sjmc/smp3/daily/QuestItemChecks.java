package club.sjmc.smp3.daily;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Map;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import club.sjmc.smp3.daily.ui.QuestTextChecks;
import club.sjmc.smp3.item.ItemDefinitions;
import club.sjmc.smp3.item.PackItem;
import club.sjmc.smp3.item.Smp3Items;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class QuestItemChecks {
	static void run(GameTestHelper test, ServerPlayer player) throws IOException {
		QuestConfig config;
		try (var input = QuestItemChecks.class.getResourceAsStream("/dailyquests.json")) {
			config = QuestConfig.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
		}
		config.validateRegistries(player.registryAccess(),
		                          Set.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"));
		config.validateLoot(player.level().getServer());
		for (var definition : config.tasks()) {
			for (var band : config.progression().bands()) {
				if (!definition.supports(band.tier()))
					continue;
				var task =
				    new QuestDay.Assignment(definition, band.tier(), config.rewards().get(definition.difficulty()),
					                        Set.of(), new Random(1), player);
				String text = QuestTextChecks.render(task);
				test.assertTrue(!text.isBlank(), "Empty quest display: " + definition.id());
				for (var goal : task.definition.objectives())
					goal.objective().conditions().keySet().forEach(QuestField::find);
			}
		}
		for (var entry : Map.of("fish", "鱼类", "junk", "垃圾", "treasure", "宝藏").entrySet()) {
			var filters = JsonParser.parseString("{\"loot\":\"" + entry.getKey() + "\"}").getAsJsonObject();
			var goal = new QuestDefinition.Goal("Fishing", "fish_caught", null, filters, new JsonPrimitive(1),
			                                    List.of(), "amount", "any");
			var definition = new QuestDefinition("display", "Display", "类别：{loot}", "world", "C",
			                                     new JsonPrimitive("minecraft:fishing_rod"), List.of(goal), "all",
			                                     List.of(), List.of(), null, 1);
			var task = new QuestDay.Assignment(definition, 1, new QuestReward(0, 0), Set.of(), new Random(1), player);
			String text = QuestTextChecks.render(task);
			test.assertTrue(text.contains("类别：" + entry.getValue()) &&
			                    text.contains("渔获类别：" + entry.getValue()),
			                "Fishing condition or placeholder was not translated: " + entry.getKey());
			filters.addProperty("future_field", "Future text / 中文");
			var extended = new QuestDefinition.Goal("Fishing", "fish_caught", null, filters, new JsonPrimitive(1),
			                                        List.of(), "amount", "any");
			definition = new QuestDefinition("display", "Display", null, "world", "C", definition.icon(),
			                                 List.of(extended), "all", List.of(), List.of(), null, 1);
			task = new QuestDay.Assignment(definition, 1, new QuestReward(0, 0), Set.of(), new Random(1), player);
			test.assertTrue(QuestTextChecks.render(task).contains("future_field：Future text / 中文"),
			                "Unknown display field did not preserve its literal value");
		}
		var coral = config.tasks().stream().filter(t -> t.id().equals("coral_broken")).findFirst().orElseThrow();
		var coralTask = new QuestDay.Assignment(coral, 3, new QuestReward(0, 0), Set.of(), new Random(1), player);
		var day = new QuestDay(100, 0, 0, Set.of(), config, new Random(1), player);
		day.assignments.clear();
		day.assignments.add(coralTask);
		for (String block : List.of("brain_coral_block", "brain_coral_fan", "brain_coral_wall_fan", "brain_coral"))
			day.record(new QuestDefinition.Event("block_broken", Map.of("block", Set.of("minecraft:" + block))));
		test.assertTrue(coralTask.progress() == 1, "Same-color coral forms were rejected or counted separately");
		day.record(new QuestDefinition.Event("block_broken", Map.of("block", Set.of("minecraft:fire_coral_fan"))));
		test.assertTrue(coralTask.progress() == 2, "A second coral color did not advance the quest");
		var pack = new ItemStack(Smp3Items.RANDOM_PACK, 2);
		pack.set(ItemDefinitions.PACK, JsonParser.parseString("""
		    {"options":[{"name":"Map","grants":[{"type":"loot_table","id":"smp3:quests/explorer_maps"}]}]}
		    """).getAsJsonObject());
		player.setItemInHand(InteractionHand.MAIN_HAND, pack);
		test.assertTrue(PackItem.open(player, InteractionHand.MAIN_HAND, pack, 0, new Random(1)) ==
		                        PackItem.OpenResult.EMPTY &&
		                    player.getMainHandItem() == pack && pack.getCount() == 2,
		                "Empty loot consumed a pouch");

		var level = player.level();
		var pos = test.absolutePos(new BlockPos(2, 3, 2));
		for (var direction : Direction.values())
			level.setBlockAndUpdate(pos.relative(direction), Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
		var tool = new ItemStack(Smp3Items.CORAL_TOOL);
		player.setItemInHand(InteractionHand.MAIN_HAND, tool);
		var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
		                               new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
		level.setBlockAndUpdate(
		    pos, Blocks.DEAD_BRAIN_CORAL.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
		test.assertTrue(tool.useOn(context) == InteractionResult.FAIL && tool.getDamageValue() == 0,
		                "Dry coral revival succeeded or consumed durability");
		level.setBlockAndUpdate(pos.east(), Blocks.WATER.defaultBlockState());
		tool.useOn(context);
		test.assertTrue(level.getBlockState(pos).is(Blocks.BRAIN_CORAL) && tool.getDamageValue() == 1,
		                "Wet coral did not revive with one durability cost");
		level.setBlockAndUpdate(pos.south(), Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(pos, Blocks.BRAIN_CORAL_WALL_FAN.defaultBlockState()
		                                 .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
		                                 .setValue(BlockStateProperties.WATERLOGGED, true));
		tool.useOn(context);
		var state = level.getBlockState(pos);
		test.assertTrue(state.is(Blocks.DEAD_BRAIN_CORAL_WALL_FAN) &&
		                    state.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.NORTH &&
		                    state.getValue(BlockStateProperties.WATERLOGGED) && tool.getDamageValue() == 2,
		                "Coral conversion lost shape, facing or waterlogging");
		test.assertTrue(tool.getMaxDamage() == 250 && !tool.has(DataComponents.REPAIRABLE),
		                "Coral tool durability or material repair is incorrect");
		tool.setDamageValue(249);
		tool.useOn(context);
		test.assertTrue(player.getMainHandItem().isEmpty(), "Coral tool survived its final durability point");
	}
}
