package club.sjmc.smp3.shop;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import club.sjmc.smp3.time.ServerDates;

import net.minecraft.server.level.ServerPlayer;

public final class QuotaStore {

	private static final Map<UUID, PlayerState> STATES = new HashMap<>();

	private QuotaStore() {}

	public static int used(ServerPlayer player, ShopEntry entry) {
		return stateFor(player.getUUID()).usedItems.getOrDefault(entry.id(), 0);
	}

	static void add(ServerPlayer player, ShopEntry entry, int items) {
		PlayerState state = stateFor(player.getUUID());
		state.usedItems.merge(entry.id(), items, Integer::sum);
	}

	private static PlayerState stateFor(UUID playerId) {
		LocalDate today = ServerDates.today();
		PlayerState state = STATES.get(playerId);
		if (state == null) {
			state = new PlayerState(today);
			STATES.put(playerId, state);
			return state;
		}
		if (today.isAfter(state.date)) {
			state.usedItems.clear();
			state.date = today;
		}
		return state;
	}

	private static final class PlayerState {
		private LocalDate date;
		private final Map<String, Integer> usedItems = new HashMap<>();

		private PlayerState(LocalDate date) { this.date = date; }
	}
}
