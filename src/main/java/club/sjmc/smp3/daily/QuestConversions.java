package club.sjmc.smp3.daily;

import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonElement;

import club.sjmc.smp3.time.ServerDates;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class QuestConversions {
	private static final AttachmentType<UUID> OWNER = AttachmentRegistry.createPersistent(
	    Identifier.fromNamespaceAndPath("smp3", "conversion_owner"), UUIDUtil.CODEC);
	private QuestConversions() {}
	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!(source.getEntity() instanceof ServerPlayer player) || entity.getAttached(OWNER) != null)
				return;
			var service = DailyQuestModule.current();
			if (service == null)
				return;
			var day = service.state(player);
			if (day == null)
				return;
			for (var task : day.assignments) {
				if (task.complete())
					continue;
				for (var goal : task.definition.objectives()) {
					if (!goal.event().equals("entity_converted"))
						continue;
					var filter = goal.objective().conditions();
					if (filter.has("entity") && !matchesType(entity, filter.get("entity")))
						continue;
					entity.setAttached(OWNER, player.getUUID());
					return;
				}
			}
		});
		UseEntityCallback.EVENT.register((actor, level, hand, entity, hit) -> {
			if (!(actor instanceof ServerPlayer player) || !player.getItemInHand(hand).isEmpty())
				return InteractionResult.PASS;
			var service = DailyQuestModule.current();
			if (service == null)
				return InteractionResult.PASS;
			var day = service.state(player);
			if (day == null)
				return InteractionResult.PASS;
			boolean conversionTask = false;
			for (var task : day.assignments) {
				if (task.complete())
					continue;
				for (int i = 0; i < task.definition.objectives().size(); i++) {
					var goal = task.definition.objectives().get(i);
					if (!goal.event().equals("entity_converted"))
						continue;
					conversionTask = true;
					var filter = goal.objective().conditions();
					if (filter.has("entity") && !matchesType(entity, filter.get("entity")))
						continue;
					UUID owner = entity.getAttached(OWNER);
					if (owner != null && !owner.equals(player.getUUID()) && bound(level, owner, entity.getUUID())) {
						player.sendSystemMessage(Component.literal("该生物已被其他玩家指定"));
						return InteractionResult.SUCCESS;
					}
					task.bindings.set(i, entity.getUUID());
					entity.setAttached(OWNER, player.getUUID());
					day.revision++;
					DailyQuestData.get(player.level().getServer()).setDirty(player.getUUID());
					player.sendSystemMessage(Component.literal("已指定：").append(entity.getDisplayName()));
					return InteractionResult.SUCCESS;
				}
			}
			if (conversionTask) {
				player.sendSystemMessage(Component.literal("该生物不符合当前转化任务"));
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.MOB_CONVERSION.register((before, after, params) -> converted(before, after, null));
	}

	private static boolean matchesType(Entity entity, JsonElement values) {
		var facts = new HashMap<String, Set<String>>();
		DailyQuestEvents.target(facts, "entity", BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()));
		return QuestDefinition.values(values).stream().anyMatch(facts.get("entity")::contains);
	}
	private static boolean bound(Level level, UUID owner, UUID entity) {
		var day = DailyQuestData.get(((ServerLevel)level).getServer()).player(owner);
		return day != null && day.date == ServerDates.today().toEpochDay() &&
		    day.assignments.stream().anyMatch(a -> !a.complete() && a.bindings.contains(entity));
	}
	public static void converted(Entity before, Entity after, String variant) {
		if (!(before.level() instanceof ServerLevel level))
			return;
		var player = QuestActions.player();
		UUID owner = before.getAttached(OWNER);
		if (player == null && owner != null && bound(level, owner, before.getUUID()))
			player = level.getServer().getPlayerList().getPlayer(owner);
		if (player == null || owner != null && !owner.equals(player.getUUID()) && bound(level, owner, before.getUUID()))
			return;
		var facts = DailyQuestEvents.context(player, level, before.blockPosition());
		DailyQuestEvents.target(facts, "entity", BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(before.getType()));
		DailyQuestEvents.target(facts, "result", BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(after.getType()));
		facts.put("subject", Set.of(before.getUUID().toString()));
		if (variant != null)
			facts.put("variant", Set.of(variant));
		DailyQuestEvents.emit(player, "entity_converted", facts);
	}
}
