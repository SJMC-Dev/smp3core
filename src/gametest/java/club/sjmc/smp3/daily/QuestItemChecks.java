package club.sjmc.smp3.daily;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Set;

import com.google.gson.JsonParser;

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
		for (String id : new String[] {"craft_travel", "coral_broken"}) {
			var definition = config.tasks().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
			int[] counts = id.equals("craft_travel") ? new int[] {1, 2, 4} : new int[] {3, 5, 7};
			for (int tier = 1; tier <= 3; tier++) {
				var task =
				    new QuestDay.Assignment(definition, tier, new QuestReward(0, 0), Set.of(), new Random(1), player);
				test.assertTrue(task.target == counts[tier - 1], "Deferred quest used the wrong tier count");
			}
		}
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
