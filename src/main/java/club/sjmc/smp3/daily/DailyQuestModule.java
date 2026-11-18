package club.sjmc.smp3.daily;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.time.ServerDates;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class DailyQuestModule {
	private static DailyQuestService instance;
	private static final Map<UUID, HudSnapshot> hudSnapshots = new HashMap<>();
	private record HudSnapshot(QuestDay day, int revision, QuestHudPayload payload) {}
	private static long currentDate = Long.MIN_VALUE;
	private DailyQuestModule() {}

	public static DailyQuestService current() { return instance; }

	public static void register() {
		BarterAttribution.register();
		PayloadTypeRegistry.clientboundPlay().register(QuestHudPayload.TYPE, QuestHudPayload.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				QuestConfig config = QuestConfig.load(
				    FabricLoader.getInstance().getConfigDir().resolve("smp3/dailyquests.json"), server.registryAccess(),
				    server.levelKeys().stream().map(key -> key.identifier().toString()).collect(Collectors.toSet()));
				config.validateLoot(server);
				config.milestones().forEach((name, id) -> {
					if (server.getAdvancements().get(Identifier.parse(id)) == null)
						throw new IllegalArgumentException("milestones." + name + ": unknown advancement " + id);
				});
				instance = new DailyQuestService(config, DailyQuestData.get(server), new Random());
				Smp3Core.LOGGER.info("[SMP3] 已加载 {} 个每日任务", instance.config.tasks().size());
			} catch (Exception e) {
				instance = null;
				Smp3Core.LOGGER.error("[SMP3] 每日任务停用，检查配置或存档", e);
			}
		});
		ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> {
			if (instance != null)
				DailyQuestData.get(server).save();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DailyQuestData.clear();
			instance = null;
			hudSnapshots.clear();
			currentDate = Long.MIN_VALUE;
			DailyQuestEvents.clearInventories();
			QuestStructures.clear();
			QuestMovement.clear();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (instance != null) {
				instance.settle(handler.player);
				DailyQuestEvents.inventoryChanged(handler.player);
				syncHud(handler.player, instance.state(handler.player));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			hudSnapshots.remove(handler.player.getUUID());
			DailyQuestEvents.forgetInventory(handler.player.getUUID());
			QuestStructures.forget(handler.player.getUUID());
			QuestMovement.forget(handler.player.getUUID());
		});
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			long date = ServerDates.today().toEpochDay();
			if (instance != null && date != currentDate) {
				currentDate = date;
				server.getPlayerList().getPlayers().forEach(instance::state);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (instance == null)
				return;
			DailyQuestEvents.flushInventories(server);
			QuestStructures.tick(server);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				int phase = Math.floorMod(player.getUUID().hashCode(), 20);
				if (server.getTickCount() % 5 != phase % 5)
					continue;
				QuestDay day = instance.state(player);
				QuestMovement.tick(player, day);
				if (server.getTickCount() % 20 == phase)
					DailyQuestEvents.inventoryLocation(player, day);
				if (day != null && server.getTickCount() % 20 == phase &&
				    day.assignments.stream().anyMatch(a
				                                      -> !a.complete() &&
				                                             (a.tracks(QuestEventType.BIOME_VISITED.id()) ||
				                                              a.tracks(QuestEventType.STRUCTURE_VISITED.id())))) {
					DailyQuestEvents.explore(player);
				}
				instance.settle(player, day);
				syncHud(player, day);
			}
		});
		DailyQuestEvents.register();
		QuestConversions.register();
		QuestStructures.register();
	}

	private static void syncHud(ServerPlayer player, QuestDay day) {
		if (!ServerPlayNetworking.canSend(player, QuestHudPayload.TYPE))
			return;
		var previous = hudSnapshots.get(player.getUUID());
		int revision = day == null ? 0 : day.revision;
		if (previous != null && previous.day == day && previous.revision == revision)
			return;
		QuestHudPayload snapshot = QuestHudPayload.of(day);
		hudSnapshots.put(player.getUUID(), new HudSnapshot(day, revision, snapshot));
		if (previous == null || !snapshot.equals(previous.payload))
			ServerPlayNetworking.send(player, snapshot);
	}
}
