package club.sjmc.smp3.daily;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.reward.RewardService;

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
	private static final Map<UUID, QuestHudPayload> hudSnapshots = new HashMap<>();
	private DailyQuestModule() {}

	public static DailyQuestService current() { return instance; }

	public static void register() {
		BarterAttribution.register();
		RewardService.register();
		PayloadTypeRegistry.clientboundPlay().register(QuestHudPayload.TYPE, QuestHudPayload.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				QuestConfig config = QuestConfig.load(
				    FabricLoader.getInstance().getConfigDir().resolve("smp3/dailyquests.json"), server.registryAccess(),
				    server.levelKeys().stream().map(key -> key.identifier().toString()).collect(Collectors.toSet()));
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
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			instance = null;
			hudSnapshots.clear();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (instance != null) {
				instance.settle(handler.player);
				syncHud(handler.player, instance.state(handler.player));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register(
		    (handler, server) -> hudSnapshots.remove(handler.player.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (instance == null || server.getTickCount() % 5 != 0)
				return;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				QuestDay day = instance.state(player);
				if (day != null && server.getTickCount() % 20 == 0 &&
				    day.assignments.stream().anyMatch(a
				                                      -> !a.complete() && a.definition.objective().event().equals(
				                                                              QuestEventType.BIOME_VISITED.id()))) {
					DailyQuestEvents.explore(player);
				}
				instance.settle(player);
				syncHud(player, day);
			}
		});
		DailyQuestEvents.register();
	}

	private static void syncHud(ServerPlayer player, QuestDay day) {
		if (!ServerPlayNetworking.canSend(player, QuestHudPayload.TYPE))
			return;
		QuestHudPayload snapshot = QuestHudPayload.of(day);
		if (!snapshot.equals(hudSnapshots.put(player.getUUID(), snapshot)))
			ServerPlayNetworking.send(player, snapshot);
	}
}
