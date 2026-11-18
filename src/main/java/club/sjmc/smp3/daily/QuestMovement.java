package club.sjmc.smp3.daily;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class QuestMovement {
	private static final Map<UUID, Motion> motion = new HashMap<>();
	private static final String[] MOVEMENTS = {"sprint", "swim", "elytra"};
	private record Motion(QuestDay day, Vec3 position, ResourceKey<Level> dimension, int[] stats, int[] origin,
	                      double crawl, boolean crawling) {}
	private QuestMovement() {}
	static void tick(ServerPlayer player, QuestDay day) {
		if (day == null || day.assignments.stream().noneMatch(
		                       a -> !a.complete() && (a.tracks("distance_traveled") || a.tracks("raid_won")))) {
			motion.remove(player.getUUID());
			return;
		}
		int[] stats = {player.getStats().getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM)),
		               player.getStats().getValue(Stats.CUSTOM.get(Stats.SWIM_ONE_CM)),
		               player.getStats().getValue(Stats.CUSTOM.get(Stats.AVIATE_ONE_CM)),
		               player.getStats().getValue(Stats.CUSTOM.get(Stats.RAID_WIN))};
		var dimension = player.level().dimension();
		var previous = motion.get(player.getUUID());
		boolean same = previous != null && previous.day == day;
		double crawl = same ? previous.crawl : 0;
		var origin = same ? previous.origin : stats;
		boolean crawling = player.isVisuallyCrawling() && player.onGround() && !player.isPassenger();
		if (same) {
			for (int i = 0; i < stats.length; i++) {
				int amount = i == 3 ? stats[i] - previous.stats[i]
				                    : (stats[i] - origin[i]) / 100 - (previous.stats[i] - origin[i]) / 100;
				if (amount <= 0)
					continue;
				var facts = DailyQuestEvents.context(player, player.level(), player.blockPosition());
				if (i != 3)
					facts.put("movement", Set.of(MOVEMENTS[i]));
				DailyQuestEvents.emit(
				    player, new QuestDefinition.Event(i == 3 ? "raid_won" : "distance_traveled", facts, amount));
			}
			if (crawling && previous.crawling && previous.dimension.equals(dimension)) {
				double distance = player.position().distanceTo(previous.position);
				if (distance < 2)
					crawl += distance;
				int blocks = (int)crawl;
				if (blocks > 0) {
					var facts = DailyQuestEvents.context(player, player.level(), player.blockPosition());
					facts.put("movement", Set.of("crawl"));
					DailyQuestEvents.emit(player, new QuestDefinition.Event("distance_traveled", facts, blocks));
					crawl -= blocks;
				}
			}
		}
		motion.put(player.getUUID(), new Motion(day, player.position(), dimension, stats, origin, crawl, crawling));
	}
	static void reset(ServerPlayer player, QuestDay day) {
		forget(player.getUUID());
		tick(player, day);
	}
	static void forget(UUID player) { motion.remove(player); }
	static void clear() { motion.clear(); }
}
