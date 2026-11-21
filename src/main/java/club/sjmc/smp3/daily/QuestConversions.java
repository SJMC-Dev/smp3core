package club.sjmc.smp3.daily;

import java.util.Set;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class QuestConversions {
	private QuestConversions() {}
	static void register() {
		ServerLivingEntityEvents.MOB_CONVERSION.register((before, after, params) -> converted(before, after, null));
	}

	public static void converted(Entity before, Entity after, String variant) {
		if (!(before.level() instanceof ServerLevel level))
			return;
		var service = DailyQuestModule.current();
		if (service == null)
			return;
		ServerPlayer nearest = null;
		QuestDefinition.Event matched = null;
		double distance = 16 * 16;
		for (var player : level.players()) {
			double candidate = player.distanceToSqr(before);
			if (!player.isAlive() || player.isCreative() || player.isSpectator() || candidate > distance)
				continue;
			var day = service.state(player);
			if (day == null)
				continue;
			var facts = DailyQuestEvents.context(player, level, before.blockPosition());
			DailyQuestEvents.target(facts, "entity", BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(before.getType()));
			DailyQuestEvents.target(facts, "result", BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(after.getType()));
			if (variant != null)
				facts.put("variant", Set.of(variant));
			var event = new QuestDefinition.Event("entity_converted", facts);
			if (day.assignments.stream().noneMatch(task -> task.accepts(event)))
				continue;
			nearest = player;
			matched = event;
			distance = candidate;
		}
		if (nearest != null)
			DailyQuestEvents.emit(nearest, matched);
	}
}
