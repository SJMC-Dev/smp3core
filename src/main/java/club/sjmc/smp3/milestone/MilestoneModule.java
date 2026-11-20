package club.sjmc.smp3.milestone;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.config.JsonConfig;
import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.perm.Permissions;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class MilestoneModule {
	private static final String LEDGER_KEY = "milestones";
	private static volatile MilestoneConfig config;
	private static final Map<UUID, Map<String, MilestoneConfig.Rule>> queued = new ConcurrentHashMap<>();
	private static final Set<UUID> online = ConcurrentHashMap.newKeySet();
	private record TitleDelivery(JsonObject receipt, CompletableFuture<Void> future) {}

	private static final Map<UUID, TitleDelivery> titles = new HashMap<>();

	private MilestoneModule() {}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(MilestoneModule::load);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			if (success)
				load(server);
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			online.add(handler.player.getUUID());
			reconcile(handler.player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> online.remove(handler.player.getUUID()));
		Smp3AccountStore.BALANCE_CHANGED.register(MilestoneModule::balanceChanged);
		ServerTickEvents.END_SERVER_TICK.register(MilestoneModule::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			config = null;
			queued.clear();
			online.clear();
			titles.clear();
		});
	}

	private static void load(MinecraftServer server) {
		try {
			reload(server);
		} catch (Exception error) {
			Smp3Core.LOGGER.error("Milestone configuration failed", error);
		}
	}

	public static void reload(MinecraftServer server) throws IOException {
		var source = JsonConfig.read(FabricLoader.getInstance().getConfigDir().resolve("smp3/milestone-rewards.json"),
		                             "/milestone-rewards.json");
		config = MilestoneConfig.parse(source, server);
		server.getPlayerList().getPlayers().forEach(MilestoneModule::reconcile);
	}

	private static void queue(UUID owner, MilestoneConfig.Rule rule) {
		queued.compute(owner, (id, rules) -> {
			if (rules == null)
				rules = new HashMap<>();
			rules.putIfAbsent(rule.id(), rule);
			return rules;
		});
	}

	private static void balanceChanged(UUID owner, Smp3Currency currency, long balance) {
		var current = config;
		if (current == null || !current.enabled() || !online.contains(owner))
			return;
		for (var rule : current.balances().getOrDefault(currency, List.of()))
			if (balance >= rule.minimum())
				queue(owner, rule);
	}

	public static void advancementChanged(ServerPlayer player, Identifier id) {
		var current = config;
		if (current == null || !current.enabled())
			return;
		for (var rule : current.advancements().getOrDefault(id, List.of()))
			if (rule.completed(player))
				queue(player.getUUID(), rule);
	}

	static void reconcile(ServerPlayer player) {
		var current = config;
		if (current == null)
			return;
		try {
			if (current.enabled()) {
				for (var rule : current.rules().values()) {
					boolean eligible =
					    rule.currency() == null
					        ? rule.completed(player)
					        : Smp3AccountStore.balance(player.getUUID(), rule.currency()) >= rule.minimum();
					if (eligible)
						queue(player.getUUID(), rule);
				}
			}
			startTitles(player);
		} catch (RuntimeException error) {
			Smp3Core.LOGGER.error("Milestone reconciliation failed for {}", player.getUUID(), error);
		}
	}

	static void tick(MinecraftServer server) {
		if (queued.isEmpty() && titles.isEmpty())
			return;
		for (UUID owner : List.copyOf(queued.keySet())) {
			var rules = queued.remove(owner);
			var player = server.getPlayerList().getPlayer(owner);
			try {
				award(owner, rules.values());
				if (player != null)
					startTitles(player);
			} catch (RuntimeException error) {
				Smp3Core.LOGGER.error("Milestone rewards pending for {}", owner, error);
			}
		}
		for (var entry : List.copyOf(titles.entrySet())) {
			var delivery = entry.getValue();
			if (!delivery.future().isDone())
				continue;
			UUID owner = entry.getKey();
			titles.remove(owner);
			try {
				delivery.future().join();
				var before = Smp3AccountStore.rewardState(owner, LEDGER_KEY);
				var after = before.deepCopy();
				delivery.receipt().keySet().forEach(id -> after.add(id, new JsonArray()));
				Smp3AccountStore.commitReward(owner, LEDGER_KEY, before, after, Map.of());
				var player = server.getPlayerList().getPlayer(owner);
				if (player != null)
					startTitles(player);
			} catch (RuntimeException error) {
				Smp3Core.LOGGER.error("Milestone titles pending for {}", owner, error);
			}
		}
	}

	private static void award(UUID owner, Collection<MilestoneConfig.Rule> rules) {
		var before = Smp3AccountStore.rewardState(owner, LEDGER_KEY);
		var after = before == null ? new JsonObject() : before.deepCopy();
		var credits = new HashMap<Smp3Currency, Integer>();
		for (var rule : rules) {
			if (after.has(rule.id()))
				continue;
			var pending = new JsonArray();
			rule.titles().forEach(pending::add);
			after.add(rule.id(), pending);
			rule.credits().forEach((currency, amount) -> credits.merge(currency, amount, Math::addExact));
		}
		if (!after.equals(before))
			Smp3AccountStore.commitReward(owner, LEDGER_KEY, before, after, credits);
	}

	private static void startTitles(ServerPlayer player) {
		UUID owner = player.getUUID();
		if (titles.containsKey(owner))
			return;
		var receipt = Smp3AccountStore.rewardState(owner, LEDGER_KEY);
		if (receipt == null)
			return;
		receipt.entrySet().removeIf(entry -> entry.getValue().getAsJsonArray().isEmpty());
		if (receipt.isEmpty())
			return;
		var nodes = receipt.entrySet()
		                .stream()
		                .flatMap(entry -> entry.getValue().getAsJsonArray().asList().stream())
		                .map(title -> "fuji.title.obtain." + title.getAsString())
		                .distinct()
		                .toList();
		titles.put(owner, new TitleDelivery(receipt, Permissions.grant(player, nodes)));
	}
}
