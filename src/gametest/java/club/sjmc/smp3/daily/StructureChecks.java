package club.sjmc.smp3.daily;

import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece;
import net.minecraft.world.phys.Vec3;

final class StructureChecks {
	static void run(GameTestHelper test, ServerPlayer player) {
		var service = DailyQuestModule.current();
		var day = service.state(player);
		var definition = QuestConfig.JSON.fromJson("""
            {"id":"structure_test","name":"结构任务","category":"exploration","difficulty":"C","icon":"minecraft:map",
             "objectives":[
              {"name":"访问","count":100,"objective":{"event":"structure_visited","conditions":{"structure":["minecraft:fortress","#minecraft:cats_spawn_as_black"]}}},
              {"name":"持有","count":100,"objective":{"event":"inventory_changed","conditions":{"item":"minecraft:apple","structure":"minecraft:swamp_hut"}}},
              {"name":"击杀","count":100,"objective":{"event":"entity_killed","conditions":{"entity":"minecraft:zombie","structure":"minecraft:swamp_hut"}}}
             ]}
            """, QuestDefinition.class);
		new QuestConfig(0, service.config.progression(), service.config.milestones(), service.config.rewards(),
		                List.of(definition))
		    .validateRegistries(player.registryAccess(), Set.of("minecraft:overworld"));
		var task = new QuestDay.Assignment(definition, 1, new QuestReward(0, 0, List.of()), Set.of(), new Random(1));
		var previous = day.assignments.set(0, task);
		var position = player.position();
		var held = player.getInventory().getItem(0);
		var level = player.level();
		var chunks = level.getChunkSource();
		var chunk = level.getChunkAt(player.blockPosition());
		var references = new HashMap<>(chunk.getAllReferences());
		var origin = level.getChunk(chunk.getPos().x() + 1, chunk.getPos().z());
		var starts = new HashMap<>(origin.getAllStarts());
		var structure =
		    player.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValueOrThrow(BuiltinStructures.SWAMP_HUT);
		var missing = new ChunkPos(chunk.getPos().x() + 1000, chunk.getPos().z());
		var piece = new SwampHutPiece(RandomSource.create(1), chunk.getPos().getMinBlockX() + 4,
		                              chunk.getPos().getMinBlockZ() + 4);
		var start = new StructureStart(structure, origin.getPos(), 0, new PiecesContainer(List.of(piece)));
		var bounds = start.getBoundingBox();
		var inside = new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
		var modified = new HashMap<>(references);
		var refs = new LongOpenHashSet();
		modified.put(structure, refs);
		chunk.setAllReferences(modified);
		try {
			player.setPos(Vec3.atCenterOf(inside));
			player.getInventory().setItem(0, new ItemStack(Items.APPLE, 2));
			refs.add(missing.pack());
			test.assertTrue(chunks.getChunkNow(missing.x(), missing.z()) == null, "Expected unloaded structure origin");
			DailyQuestEvents.explore(player);
			DailyQuestEvents.inventoryLocation(player, day);
			DailyQuestEvents.flushInventories(level.getServer());
			test.assertTrue(task.goalProgress(0) == 0 && task.goalProgress(1) == 0 &&
			                    chunks.getChunkNow(missing.x(), missing.z()) == null,
			                "Structure lookup loaded an origin chunk or matched without a start");
			origin.setStartForStructure(structure, start);
			refs.add(origin.getPos().pack());
			DailyQuestEvents.explore(player);
			DailyQuestEvents.inventoryLocation(player, day);
			DailyQuestEvents.flushInventories(level.getServer());
			test.assertTrue(task.goalProgress(0) == 1 && task.goalProgress(1) == 2,
			                "Loaded cross-chunk structure ID/tag or inventory matching failed");
			var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, BlockPos.ZERO);
			zombie.setPos(Vec3.atCenterOf(inside));
			zombie.hurtServer(level, player.damageSources().playerAttack(player), 1000);
			test.assertTrue(task.goalProgress(2) == 1, "Kill event ignored its structure location");
			zombie.discard();
			for (var outside : List.of(inside.above(bounds.getYSpan()), inside.west())) {
				player.setPos(Vec3.atCenterOf(outside));
				DailyQuestEvents.explore(player);
				DailyQuestEvents.inventoryLocation(player, day);
				DailyQuestEvents.flushInventories(level.getServer());
				test.assertTrue(task.goalProgress(0) == 1 && task.goalProgress(1) == 0,
				                "Structure boundary or inventory progress after leaving is incorrect");
			}
		} finally {
			chunk.setAllReferences(references);
			origin.setAllStarts(starts);
			day.assignments.set(0, previous);
			player.setPos(position);
			player.getInventory().setItem(0, held);
		}
	}
}
