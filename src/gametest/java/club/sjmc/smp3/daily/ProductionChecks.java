package club.sjmc.smp3.daily;

import java.util.List;

import com.google.gson.JsonParser;

import club.sjmc.smp3.reward.Reward;
import club.sjmc.smp3.reward.RewardService;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;

final class ProductionChecks {
	static void run(GameTestHelper test, ServerPlayer player, BlockPos pos) {
		var definition = new QuestDefinition(
		    "production", "Production", null, "crafting", "A", "minecraft:iron_ingot",
		    new QuestDefinition.Objective(
		        "item_crafted", JsonParser
		                            .parseString("{\"item\":[\"minecraft:iron_ingot\",\"minecraft:cooked_cod\","
		                                         + "\"minecraft:netherite_pickaxe\",\"minecraft:potion\"]}")
		                            .getAsJsonObject()),
		    100, List.of(), List.of(), null);
		var task = new QuestDay.Assignment(definition, 1, new Reward(0, 0));
		DailyQuestModule.current().state(player).assignments.set(0, task);
		player.getInventory().clearContent();
		var furnace = new FurnaceMenu(6, player.getInventory());
		furnace.getSlot(2).set(new ItemStack(Items.IRON_INGOT, 5));
		test.assertTrue(task.progress == 0, "Furnace contents counted before collection");
		furnace.clicked(2, 1, ContainerInput.PICKUP, player);
		test.assertTrue(task.progress == 3, "Partial smelting collection counted incorrectly");
		furnace.setCarried(ItemStack.EMPTY);
		furnace.clicked(2, 0, ContainerInput.QUICK_MOVE, player);
		test.assertTrue(task.progress == 5, "Shift-click smelting counted twice or lost output");
		var blast = new BlastFurnaceMenu(7, player.getInventory());
		blast.getSlot(2).set(new ItemStack(Items.IRON_INGOT, 2));
		blast.clicked(2, 0, ContainerInput.QUICK_MOVE, player);
		var smoker = new SmokerMenu(8, player.getInventory());
		smoker.getSlot(2).set(new ItemStack(Items.COOKED_COD, 2));
		player.getInventory().setItem(0, ItemStack.EMPTY);
		smoker.clicked(2, 0, ContainerInput.SWAP, player);
		test.assertTrue(task.progress == 9, "Blast furnace or smoker did not emit item_crafted");
		smoker.setCarried(ItemStack.EMPTY);

		var level = player.level();
		level.setBlockAndUpdate(pos, Blocks.SMITHING_TABLE.defaultBlockState());
		var smithing = new SmithingMenu(9, player.getInventory(), ContainerLevelAccess.create(level, pos));
		smithing.getSlot(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 2));
		smithing.getSlot(1).set(new ItemStack(Items.DIAMOND_PICKAXE));
		smithing.getSlot(2).set(new ItemStack(Items.NETHERITE_INGOT, 2));
		test.assertTrue(smithing.getSlot(3).getItem().is(Items.NETHERITE_PICKAXE) && task.progress == 9,
		                "Smithing preview counted");
		smithing.clicked(3, 0, ContainerInput.PICKUP, player);
		smithing.setCarried(ItemStack.EMPTY);
		smithing.getSlot(1).set(new ItemStack(Items.DIAMOND_PICKAXE));
		smithing.clicked(3, 0, ContainerInput.QUICK_MOVE, player);
		test.assertTrue(task.progress == 11, "Normal or shift-click smithing did not count exactly once");

		level.setBlockAndUpdate(pos, Blocks.BREWING_STAND.defaultBlockState());
		var stand = (BrewingStandBlockEntity)level.getBlockEntity(pos);
		var brewing = new BrewingStandMenu(10, player.getInventory(), stand, new SimpleContainerData(4));
		stand.setItem(0, PotionContents.createItemStack(Items.POTION, Potions.WATER));
		brewing.clicked(0, 0, ContainerInput.PICKUP, player);
		test.assertTrue(task.progress == 11, "Unbrewed bottle counted");
		brewing.setCarried(ItemStack.EMPTY);
		for (int slot = 0; slot < 3; slot++)
			stand.setItem(slot, PotionContents.createItemStack(Items.POTION, Potions.WATER));
		stand.setItem(3, new ItemStack(Items.NETHER_WART));
		stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
		for (int tick = 0; tick <= 400; tick++)
			BrewingStandBlockEntity.serverTick(level, pos, stand.getBlockState(), stand);
		stand.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(),
		                                              stand.saveWithoutMetadata(player.registryAccess())));
		brewing.clicked(0, 0, ContainerInput.PICKUP, player);
		test.assertTrue(task.progress == 12, "Brewed output not counted");
		brewing.clicked(0, 0, ContainerInput.PICKUP, player);
		brewing.clicked(0, 0, ContainerInput.PICKUP, player);
		test.assertTrue(task.progress == 12, "Reinserted potion counted again");
		brewing.setCarried(ItemStack.EMPTY);
		brewing.clicked(1, 0, ContainerInput.QUICK_MOVE, player);
		player.getInventory().setItem(0, PotionContents.createItemStack(Items.POTION, Potions.WATER));
		brewing.clicked(2, 0, ContainerInput.SWAP, player);
		test.assertTrue(task.progress == 14, "Shift-click or hotbar-swap brewing missed output");
		var service = DailyQuestModule.current();
		definition = new QuestDefinition(
		    "inventory", "Inventory", null, "collecting", "A", "minecraft:oak_planks",
		    new QuestDefinition.Objective(
		        "inventory_changed",
		        JsonParser.parseString("{\"item\":[\"minecraft:oak_planks\",\"#minecraft:planks\"]}")
		            .getAsJsonObject()),
		    6, List.of(), List.of(), null);
		task = new QuestDay.Assignment(definition, 1, new Reward(0, 0));
		service.state(player).assignments.set(0, task);
		player.getInventory().clearContent();
		player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
		player.getInventory().setItem(1, new ItemStack(Items.BIRCH_PLANKS, 2));
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.OAK_PLANKS));
		service.settle(player);
		test.assertTrue(task.progress == 5, "Inventory tags, overlapping filters or offhand counted incorrectly");
		player.getInventory().setItem(2, player.getInventory().removeItemNoUpdate(0));
		service.settle(player);
		test.assertTrue(task.progress == 5, "Moving stacks added possession progress");
		player.getInventory().setItem(1, ItemStack.EMPTY);
		service.settle(player);
		test.assertTrue(task.progress == 3, "Possession progress did not decrease after removal");
		long completed = RewardService.progress(player).completed();
		player.getInventory().setItem(2, new ItemStack(Items.OAK_PLANKS, 5));
		service.settle(player);
		test.assertTrue(task.claimed && player.getInventory().getItem(2).getCount() == 5,
		                "Possession task failed to settle or consumed items");
		player.getInventory().clearContent();
		service.settle(player);
		test.assertTrue(task.progress == 6 && RewardService.progress(player).completed() == completed + 1,
		                "Completed possession task reverted or paid twice");
	}
}
