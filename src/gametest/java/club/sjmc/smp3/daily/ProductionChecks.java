package club.sjmc.smp3.daily;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.SguiUtils;
import eu.pb4.sgui.api.gui.SimpleGui;

import club.sjmc.smp3.daily.ui.DailyQuestGui;
import club.sjmc.smp3.item.ItemDelivery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;

final class ProductionChecks {
	static void run(GameTestHelper test, ServerPlayer player, BlockPos pos) {
		var definition =
		    new QuestDefinition("production", "Production", null, "crafting", "A",
		                        new JsonPrimitive("minecraft:iron_ingot"),
			                    List.of(new QuestDefinition.Goal(
		                            "Production", "item_crafted", null,
		                            JsonParser
		                                .parseString("{\"item\":[\"minecraft:iron_ingot\",\"minecraft:cooked_cod\","
		                                             + "\"minecraft:netherite_pickaxe\",\"minecraft:potion\"]}")
		                                .getAsJsonObject(),
		                            new JsonPrimitive(100), List.of(), "amount", "any")),
			                    "all", List.of(), List.of(), null, 1);
		var task = new QuestDay.Assignment(definition, 1, new QuestReward(0, 0), Set.of(), new Random(1), player);
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
		definitions(test, player, pos);
		var service = DailyQuestModule.current();
		definition = new QuestDefinition(
		    "inventory", "Inventory", null, "collecting", "A", new JsonPrimitive("minecraft:oak_planks"),
		    List.of(new QuestDefinition.Goal(
		        "Inventory", "inventory_changed", null,
		        JsonParser.parseString("{\"item\":[\"minecraft:oak_planks\",\"#minecraft:planks\"]}").getAsJsonObject(),
		        new JsonPrimitive(6), List.of(), "amount", "any")),
		    "all", List.of(), List.of(), null, 1);
		task = new QuestDay.Assignment(
		    definition, 1,
		    new QuestReward(
		        0, 0, List.of(JsonParser.parseString("{\"id\":\"minecraft:bread\",\"count\":2}").getAsJsonObject())),
		    Set.of(), new Random(1), player);
		service.state(player).assignments.set(0, task);
		player.getInventory().clearContent();
		player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
		player.getInventory().setItem(1, new ItemStack(Items.BIRCH_PLANKS, 2));
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.OAK_PLANKS));
		player.inventoryMenu.broadcastChanges();
		player.inventoryMenu.broadcastChanges();
		test.assertTrue(task.progress == 0, "Slot notifications were not deferred to a complete inventory snapshot");
		DailyQuestEvents.flushInventories(player.level().getServer());
		service.settle(player);
		test.assertTrue(task.progress == 5, "Inventory tags, overlapping filters or offhand counted incorrectly");
		player.getInventory().setItem(2, player.getInventory().removeItemNoUpdate(0));
		player.inventoryMenu.broadcastChanges();
		DailyQuestEvents.flushInventories(player.level().getServer());
		service.settle(player);
		test.assertTrue(task.progress == 5, "Moving stacks added possession progress");
		player.getInventory().setItem(1, ItemStack.EMPTY);
		service.settle(player);
		test.assertTrue(task.progress == 5, "Settlement polled an unchanged notification state");
		player.inventoryMenu.broadcastChanges();
		DailyQuestEvents.flushInventories(player.level().getServer());
		service.settle(player);
		test.assertTrue(task.progress == 3, "Possession progress did not decrease after removal");
		long completed = QuestRewards.progress(player).completed();
		for (int slot = 0; slot < 36; slot++)
			player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
		player.getInventory().setItem(2, new ItemStack(Items.OAK_PLANKS, 5));
		player.inventoryMenu.broadcastChanges();
		DailyQuestEvents.flushInventories(player.level().getServer());
		service.settle(player);
		test.assertTrue(task.claimed && player.getInventory().getItem(2).getCount() == 5 &&
		                    ItemDelivery.pending(player).getFirst().getCount() == 2,
		                "Full inventory lost the entitlement or consumed target items");
		player.getInventory().setItem(2, new ItemStack(Items.STONE, 64));
		player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		player.inventoryMenu.broadcastChanges();
		DailyQuestEvents.flushInventories(player.level().getServer());
		service.settle(player);
		var hud = QuestHudPayload.of(service.state(player), player.registryAccess()).entries().getFirst();
		test.assertTrue(hud.progress() == 6 && hud.complete() && hud.claimed() &&
		                    ItemDelivery.pending(player).getFirst().getCount() == 2 &&
		                    QuestRewards.progress(player).completed() == completed + 1,
		                "Pending reward reverted completion or its displayed progress after target items were removed");
		player.getInventory().setItem(0, ItemStack.EMPTY);
		ItemDelivery.deliver(player);
		ItemDelivery.deliver(player);
		test.assertTrue(ItemDelivery.pending(player).isEmpty() && player.getInventory().getItem(0).is(Items.BREAD) &&
		                    player.getInventory().getItem(0).getCount() == 2,
		                "Freeing space lost or duplicated the pending reward");
	}
	private static void definitions(GameTestHelper test, ServerPlayer player, BlockPos pos) {
		var definition = QuestConfig.JSON.fromJson("""
{
  "id": "definitions",
  "name": "制作清单",
  "description": "完成五种加工目标。",
  "category": "crafting",
  "difficulty": "A",
  "icon": "minecraft:stone",
  "objectives": [
    {
      "event": "item_crafted",
      "conditions": {
        "source": "stonecutting"
      },
      "name": "切石",
      "count": 100
    },
    {
      "event": "item_crafted",
      "conditions": {
        "source": "loom",
        "pattern": "minecraft:creeper"
      },
      "name": "苦力怕旗帜",
      "count": 100
    },
    {
      "event": "item_crafted",
      "conditions": {
        "source": "brewing",
        "potion": "minecraft:night_vision"
      },
      "name": "夜视药水",
      "count": 100
    },
    {
      "event": "item_crafted",
      "conditions": {
        "item": "minecraft:suspicious_stew",
        "effect": "minecraft:invisibility"
      },
      "name": "隐身炖菜",
      "count": 100
    },
    {
      "event": "item_crafted",
      "conditions": {
        "source": "smithing",
        "template": "minecraft:netherite_upgrade_smithing_template"
      },
      "name": "下界合金升级",
      "count": 100
    }
  ]
}
""", QuestDefinition.class);
		var service = DailyQuestModule.current();
		new QuestConfig(0, service.config.progression(), service.config.milestones(), service.config.rewards(),
		                List.of(definition))
		    .validateRegistries(player.registryAccess(), Set.of("minecraft:overworld"));
		var task = new QuestDay.Assignment(definition, 1, new QuestReward(0, 0), Set.of(), new Random(1), player);
		service.state(player).assignments.set(0, task);
		player.getInventory().clearContent();
		var level = player.level();
		level.setBlockAndUpdate(pos, Blocks.STONECUTTER.defaultBlockState());
		var stone = new StonecutterMenu(11, player.getInventory(), ContainerLevelAccess.create(level, pos));
		stone.getSlot(0).set(new ItemStack(Items.STONE, 2));
		stone.clickMenuButton(player, 0);
		int amount = stone.getSlot(1).getItem().getCount();
		test.assertTrue(amount > 0 && task.goalProgress(0) == 0, "Stonecutting preview counted");
		stone.clicked(1, 0, ContainerInput.PICKUP, player);
		stone.setCarried(ItemStack.EMPTY);
		stone.clicked(1, 0, ContainerInput.QUICK_MOVE, player);
		test.assertTrue(task.goalProgress(0) == amount * 2, "Normal/quick stonecutting quantity incorrect");
		stone.getSlot(0).set(new ItemStack(Items.STONE));
		stone.clickMenuButton(player, 0);
		player.getInventory().setItem(0, ItemStack.EMPTY);
		stone.clicked(1, 0, ContainerInput.SWAP, player);
		test.assertTrue(task.goalProgress(0) == amount * 3, "Stonecutting hotbar swap missed");
		stone.getSlot(0).set(new ItemStack(Items.STONE));
		stone.clickMenuButton(player, 0);
		for (int i = 0; i < 36; i++)
			player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
		stone.clicked(1, 0, ContainerInput.QUICK_MOVE, player);
		test.assertTrue(task.goalProgress(0) == amount * 3, "Failed stonecutting transfer counted");
		player.getInventory().clearContent();

		level.setBlockAndUpdate(pos, Blocks.LOOM.defaultBlockState());
		var loom = new LoomMenu(12, player.getInventory(), ContainerLevelAccess.create(level, pos));
		loom.getSlot(0).set(new ItemStack(Items.BANNER.pick(DyeColor.WHITE), 2));
		loom.getSlot(1).set(new ItemStack(Items.DYE.pick(DyeColor.BLACK), 2));
		loom.getSlot(2).set(new ItemStack(Items.CREEPER_BANNER_PATTERN));
		test.assertTrue(!loom.getSlot(3).getItem().isEmpty() && task.goalProgress(1) == 0, "Loom preview counted");
		loom.clicked(3, 0, ContainerInput.PICKUP, player);
		var banner = loom.getCarried().copy();
		loom.setCarried(ItemStack.EMPTY);
		loom.clicked(3, 0, ContainerInput.QUICK_MOVE, player);
		test.assertTrue(task.goalProgress(1) == 2, "Normal/quick loom output not counted");
		loom.getSlot(2).set(ItemStack.EMPTY);
		loom.getSlot(0).set(banner);
		loom.getSlot(1).set(new ItemStack(Items.DYE.pick(DyeColor.RED)));
		loom.clickMenuButton(player, 0);
		player.getInventory().setItem(0, ItemStack.EMPTY);
		loom.clicked(3, 0, ContainerInput.SWAP, player);
		test.assertTrue(task.goalProgress(1) == 2, "Old banner layer credited as newly woven");

		DailyQuestEvents.crafted(player, PotionContents.createItemStack(Items.POTION, Potions.INVISIBILITY), 1,
		                         "brewing");
		DailyQuestEvents.crafted(player, PotionContents.createItemStack(Items.POTION, Potions.NIGHT_VISION), 1,
		                         "crafting");
		DailyQuestEvents.crafted(player, PotionContents.createItemStack(Items.POTION, Potions.NIGHT_VISION), 2,
		                         "brewing");
		var stew = new ItemStack(Items.SUSPICIOUS_STEW);
		DailyQuestEvents.crafted(player, stew, 1, "crafting");
		stew.set(DataComponents.SUSPICIOUS_STEW_EFFECTS,
		         new SuspiciousStewEffects(List.of(new SuspiciousStewEffects.Entry(MobEffects.INVISIBILITY, 160))));
		DailyQuestEvents.crafted(player, stew, 1, "crafting");
		level.setBlockAndUpdate(pos, Blocks.SMITHING_TABLE.defaultBlockState());
		var smithing = new SmithingMenu(13, player.getInventory(), ContainerLevelAccess.create(level, pos));
		smithing.getSlot(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
		smithing.getSlot(1).set(new ItemStack(Items.DIAMOND_PICKAXE));
		smithing.getSlot(2).set(new ItemStack(Items.NETHERITE_INGOT));
		smithing.clicked(3, 0, ContainerInput.QUICK_MOVE, player);
		test.assertTrue(task.goalProgress(2) == 2 && task.goalProgress(3) == 1 && task.goalProgress(4) == 1,
		                "Component, source or consumed template matching failed");
		test.assertTrue(DailyQuestGui.show(player), "Multi-objective GUI failed to open");
		var menu = (SimpleGui)SguiUtils.getCurrentGui(player);
		var lore = menu.getGuiElement(19).getItemStack().get(DataComponents.LORE).lines().toString();
		test.assertTrue(lore.contains("全部完成") && lore.contains("夜视药水 (2/100)"),
		                "Sub-goal progress missing from GUI");
		var summary = menu.getGuiElement(10).getItemStack().get(DataComponents.LORE).lines().toString();
		test.assertTrue(summary.contains("完成五种加工目标。") && !summary.contains("minecraft:night_vision") &&
		                    !summary.contains("夜视药水"),
		                "Task description duplicates detailed conditions");
		menu.getGuiElement(10).getGuiCallback().click(10, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, menu);
		var details = (SimpleGui)SguiUtils.getCurrentGui(player);
		boolean hasPotion = false;
		for (int i = 0; i < 18; i++) {
			var lines = details.getGuiElement(i).getItemStack().get(DataComponents.LORE).lines();
			hasPotion |= lines.toString().contains("minecraft:night_vision");
		}
		test.assertTrue(hasPotion, "Detailed conditions are inaccessible from the task icon");
		details.getGuiElement(22).getGuiCallback().click(22, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, details);
		test.assertTrue(SguiUtils.getCurrentGui(player) instanceof DailyQuestGui,
		                "Task details cannot return to the overview");
		player.closeContainer();
		var overviewHud = QuestHudPayload.of(service.state(player), player.registryAccess()).entries().getFirst();
		test.assertTrue(overviewHud.target() == 5, "Multi-objective HUD target does not count goals");
		var anyDefinition = QuestConfig.JSON.fromJson("""
		    {"id":"hud_any","name":"Mining","category":"mining","difficulty":"B","icon":"minecraft:book","completion":"any",
		     "objectives":[
		      {"name":"Emerald","event":"block_broken","conditions":{"block":"minecraft:emerald_ore"},"count":6},
		      {"name":"Diamond","event":"block_broken","conditions":{"block":"minecraft:diamond_ore"},"count":12},
		      {"name":"Debris","event":"block_broken","conditions":{"block":"minecraft:ancient_debris"},"count":2}]}
		    """, QuestDefinition.class);
		var any = new QuestDay.Assignment(anyDefinition, 1, new QuestReward(0, 0), Set.of(), new Random(1), player);
		var day = service.state(player);
		var previous = day.assignments.set(0, any);
		day.record(new QuestDefinition.Event("block_broken", Map.of("block", Set.of("minecraft:emerald_ore")), 3));
		day.record(new QuestDefinition.Event("block_broken", Map.of("block", Set.of("minecraft:diamond_ore")), 5));
		day.record(new QuestDefinition.Event("block_broken", Map.of("block", Set.of("minecraft:ancient_debris")), 1));
		var hud = QuestHudPayload.of(day, player.registryAccess()).entries().getFirst();
		test.assertTrue(hud.progress() == 3 && hud.target() == 6 && hud.icons().size() == 1 &&
		                    hud.icons().getFirst().is(Items.EMERALD_ORE) && !any.complete(),
		                "Any-objective HUD must compare ratios and keep configuration order on ties");
		day.record(new QuestDefinition.Event("block_broken", Map.of("block", Set.of("minecraft:diamond_ore")), 2));
		hud = QuestHudPayload.of(day, player.registryAccess()).entries().getFirst();
		test.assertTrue(hud.progress() == 7 && hud.target() == 12 && hud.icons().size() == 1 &&
		                    hud.icons().getFirst().is(Items.DIAMOND_ORE),
		                "HUD did not follow the new leading objective");
		day.record(new QuestDefinition.Event("block_broken", Map.of("block", Set.of("minecraft:ancient_debris")), 1));
		hud = QuestHudPayload.of(day, player.registryAccess()).entries().getFirst();
		test.assertTrue(hud.progress() == 2 && hud.target() == 2 && hud.complete() && any.complete(),
		                "Any-objective completion disagrees with HUD progress");
		var visitDefinition = QuestConfig.JSON.fromJson("""
		    {"id":"visit_random","name":"Explore","description":"到达指定的生物群系。","category":"exploration","difficulty":"C","icon":"minecraft:grass_block",
		     "objectives":[{"event":"biome_visited","selection":"random","countBy":"distinct","count":1,
		     "target":{"field":"biome","values":["minecraft:plains","minecraft:sunflower_plains"]}}]}
		    """, QuestDefinition.class);
		var diamond = JsonParser.parseString("{\"id\":\"minecraft:diamond\",\"count\":2}").getAsJsonObject();
		var visit = new QuestDay.Assignment(visitDefinition, 1, new QuestReward(0, 0, List.of(diamond)), Set.of(),
		                                    new Random(1), player);
		day.assignments.set(0, visit);
		String selected = QuestDefinition.values(visit.definition.objectives().getFirst().target().values()).getFirst();
		String other = selected.equals("minecraft:plains") ? "minecraft:sunflower_plains" : "minecraft:plains";
		day.record(new QuestDefinition.Event("biome_visited", Map.of("biome", Set.of(other))));
		test.assertTrue(!visit.complete(), "Unselected biome completed the random exploration task");
		DailyQuestGui.show(player);
		var visitGui = (SimpleGui)SguiUtils.getCurrentGui(player);
		var visitLore = visitGui.getGuiElement(10).getItemStack().get(DataComponents.LORE).lines().toString();
		test.assertTrue(visitLore.contains("biome." + selected.replace(':', '.')) &&
		                    !visitLore.contains("biome." + other.replace(':', '.')),
		                "Random exploration lore must show only assigned targets, preserving translation");
		test.assertTrue(visitGui.getGuiElement(19)
		                    .getItemStack()
		                    .get(DataComponents.LORE)
		                    .lines()
		                    .toString()
		                    .contains("item.minecraft.diamond"),
		                "Reward lore was flattened into the server language");
		player.closeContainer();
		day.record(new QuestDefinition.Event("biome_visited", Map.of("biome", Set.of(selected))));
		test.assertTrue(visit.complete(), "Selected biome did not complete exploration");
		day.assignments.set(0, previous);
	}
}
