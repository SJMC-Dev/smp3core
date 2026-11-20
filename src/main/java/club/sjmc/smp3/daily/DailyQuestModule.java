package club.sjmc.smp3.daily;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.time.ServerDates;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public final class DailyQuestModule {
	private static DailyQuestService instance;
	private static final Map<ServerGamePacketListenerImpl, HudSnapshot> hudSnapshots = new HashMap<>();
	private record HudSnapshot(QuestDay day, int revision, QuestHudPayload payload) {}
	private static long currentDate = Long.MIN_VALUE;
	private DailyQuestModule() {}

	public static DailyQuestService current() { return instance; }

	public static void register() {
		BarterAttribution.register();
		PayloadTypeRegistry.clientboundPlay().register(QuestHudPayload.TYPE, QuestHudPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(QuestHudPayload.Request.TYPE, QuestHudPayload.Request.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(QuestHudPayload.Request.TYPE, (request, context) -> {
			var player = context.player();
			hudSnapshots.remove(player.connection);
			syncHud(player, instance == null ? null : instance.state(player), false);
		});
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
				syncHud(handler.player, instance.state(handler.player), false);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			hudSnapshots.remove(handler);
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
				syncHud(player, day, false);
			}
		});
		DailyQuestEvents.register();
		QuestConversions.register();
		QuestStructures.register();
	}

	public static boolean refreshHud(ServerPlayer player) {
		return syncHud(player, instance == null ? null : instance.state(player), true);
	}

	private static boolean syncHud(ServerPlayer player, QuestDay day, boolean diagnostic) {
		boolean available = ServerPlayNetworking.canSend(player, QuestHudPayload.TYPE);
		var previous = hudSnapshots.get(player.connection);
		int revision = day == null ? 0 : day.revision;
		boolean unchanged = previous != null && previous.day == day && previous.revision == revision;
		if (diagnostic) {
			Smp3Core.LOGGER.info(
			    "[SMP3 HUD] server player={} uuid={} date={} total={} unclaimed={} channel={} revision={} cache={} "
			        + "cachedDate={} cachedRevision={}",
			    player.getName().getString(), player.getUUID(), day == null ? null : day.date,
			    day == null ? 0 : day.assignments.size(),
			    day == null ? 0 : day.assignments.stream().filter(a -> !a.claimed).count(), available, revision,
			    previous == null ? "missing"
				: unchanged      ? "current"
				                 : "stale",
			    previous == null ? null : previous.payload.date(), previous == null ? null : previous.revision);
			if (!available || day == null) {
				String message = !available ? "任务 HUD 通道不可用，请更新 SMP3 客户端"
				                            : "每日任务数据不可用，HUD 未刷新，请检查服务器日志";
				player.sendSystemMessage(Component.literal(message));
				return false;
			}
		}
		if (!available)
			return false;
		if (!diagnostic && unchanged)
			return true;
		QuestHudPayload snapshot = QuestHudPayload.of(day);
		if (diagnostic || previous == null || !snapshot.equals(previous.payload))
			ServerPlayNetworking.send(
			    player, diagnostic ? new QuestHudPayload(snapshot.date(), snapshot.entries(), true) : snapshot);
		hudSnapshots.put(player.connection, new HudSnapshot(day, revision, snapshot));
		return true;
	}
}
