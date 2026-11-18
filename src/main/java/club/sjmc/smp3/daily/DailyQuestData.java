package club.sjmc.smp3.daily;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.item.ItemDelivery;

import net.minecraft.server.MinecraftServer;

final class DailyQuestData {
	static final Gson JSON = new Gson();
	private static DailyQuestData instance;
	private final MinecraftServer server;
	final Map<UUID, QuestDay> players = new HashMap<>();
	private final Set<UUID> dirty = new HashSet<>();

	private DailyQuestData(MinecraftServer server) { this.server = server; }

	static DailyQuestData get(MinecraftServer server) {
		if (instance == null || instance.server != server)
			instance = new DailyQuestData(server);
		return instance;
	}

	static void clear() { instance = null; }

	QuestDay player(UUID id) {
		return players.computeIfAbsent(id, owner -> {
			var state = Smp3AccountStore.rewardState(owner, ItemDelivery.LEDGER_KEY, "questDay");
			return state == null ? null : JSON.fromJson(state.get("questDay"), QuestDay.class);
		});
	}

	void setDirty(UUID player) { dirty.add(player); }

	void save() {
		if (dirty.isEmpty())
			return;
		Map<UUID, JsonObject> snapshots = new HashMap<>();
		dirty.forEach(id -> {
			QuestDay day = players.get(id);
			if (day == null)
				return;
			JsonObject fields = new JsonObject();
			fields.add("questDay", JSON.toJsonTree(day));
			snapshots.put(id, fields);
		});
		Smp3AccountStore.saveRewardFields(ItemDelivery.LEDGER_KEY, snapshots);
		dirty.clear();
	}
}
