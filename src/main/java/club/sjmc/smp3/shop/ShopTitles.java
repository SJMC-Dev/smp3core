package club.sjmc.smp3.shop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;
import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.item.ItemDelivery;
import club.sjmc.smp3.perm.Permissions;
import net.minecraft.server.level.ServerPlayer;

final class ShopTitles {
	private static final String FIELD = "shopTitles";
	private static final Map<UUID, CompletableFuture<Void>> active = new HashMap<>();
	private ShopTitles() {}
	static String permission(String title) { return "fuji.title.obtain." + title; }
	static boolean pending(ServerPlayer player, String title) {
		var state = Smp3AccountStore.rewardState(player.getUUID(), ItemDelivery.LEDGER_KEY, FIELD);
		return state != null && state.has(FIELD) && state.getAsJsonObject(FIELD).has(title);
	}
	static void queue(JsonObject state, String title) {
		var pending = state.has(FIELD) ? state.getAsJsonObject(FIELD) : new JsonObject();
		pending.addProperty(title, true);
		state.add(FIELD, pending);
	}
	static void deliver(ServerPlayer player) {
		UUID owner = player.getUUID();
		if (active.containsKey(owner))
			return;
		var state = Smp3AccountStore.rewardState(owner, ItemDelivery.LEDGER_KEY, FIELD);
		if (state == null || !state.has(FIELD) || state.getAsJsonObject(FIELD).isEmpty())
			return;
		var titles = List.copyOf(state.getAsJsonObject(FIELD).keySet());
		var server = player.level().getServer();
		var future = Permissions.grant(player, titles.stream().map(ShopTitles::permission).toList());
		active.put(owner, future);
		future.whenComplete((ignored, error) -> server.execute(() -> {
			if (active.get(owner) != future)
				return;
			active.remove(owner);
			try {
				if (error != null)
					throw new IllegalStateException("Title permission save failed", error);
				var before = Smp3AccountStore.rewardState(owner, ItemDelivery.LEDGER_KEY);
				var after = before.deepCopy();
				titles.forEach(after.getAsJsonObject(FIELD)::remove);
				Smp3AccountStore.commitReward(owner, ItemDelivery.LEDGER_KEY, before, after, Map.of());
				var online = server.getPlayerList().getPlayer(owner);
				if (online != null)
					deliver(online);
			} catch (RuntimeException failure) {
				Smp3Core.LOGGER.error("Shop titles pending for {}; retry on reconnect or shop open", owner, failure);
			}
		}));
	}
	static void clear() { active.clear(); }
}
