package club.sjmc.smp3.daily;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.item.ItemDelivery;

import net.minecraft.server.level.ServerPlayer;

public final class QuestRewards {
	private static final Gson JSON = new Gson();
	private QuestRewards() {}
	private static final class State {
		long completed, completionDay = Long.MIN_VALUE, paidDay = Long.MIN_VALUE;
		int completionSlots, paidSlots;
		List<DailyReward> ready = new ArrayList<>();
	}
	private record Stored(JsonObject expected, State state) {}
	private static Stored read(ServerPlayer player) {
		JsonObject json = ItemDelivery.ledgerState(player);
		return new Stored(json, json == null ? new State() : JSON.fromJson(json, State.class));
	}
	private static void commit(ServerPlayer player, Stored stored, Map<Smp3Currency, Integer> credits,
	                           List<JsonObject> items) {
		JsonObject next = stored.expected == null ? new JsonObject() : stored.expected.deepCopy();
		JSON.toJsonTree(stored.state).getAsJsonObject().entrySet().forEach(e -> next.add(e.getKey(), e.getValue()));
		ItemDelivery.queue(next, items);
		Smp3AccountStore.commitReward(player.getUUID(), ItemDelivery.LEDGER_KEY, stored.expected, next, credits);
	}
	public record Progress(long completed, long completionDay, int completionSlots, long paidDay, int paidSlots) {
		public boolean completed(long day, int slot) {
			return completionDay == day && (completionSlots & 1 << slot) != 0;
		}
		public boolean paid(long day, int slot) {
			return paidDay > day || paidDay == day && (paidSlots & 1 << slot) != 0;
		}
	}
	public static Progress progress(ServerPlayer player) {
		State state = read(player).state;
		return new Progress(state.completed, state.completionDay, state.completionSlots, state.paidDay,
		                    state.paidSlots);
	}
	public static void complete(ServerPlayer player, long day, int slot, String name, QuestReward reward) {
		Stored stored = read(player);
		State state = stored.state;
		if (day < state.completionDay || day == state.completionDay && (state.completionSlots & 1 << slot) != 0)
			return;
		if (day != state.completionDay) {
			state.completionDay = day;
			state.completionSlots = 0;
		}
		state.completionSlots |= 1 << slot;
		state.completed = Math.incrementExact(state.completed);
		state.ready.add(new DailyReward(day, slot, name, reward));
		commit(player, stored, Map.of(), List.of());
	}
	public record DailyReward(long day, int slot, String name, QuestReward reward) {}
	public static List<DailyReward> ready(ServerPlayer player) { return List.copyOf(read(player).state.ready); }
	public static void grant(ServerPlayer player, DailyReward entitlement) {
		long day = entitlement.day;
		int slot = entitlement.slot;
		QuestReward reward = entitlement.reward;
		Stored stored = read(player);
		State state = stored.state;
		if (state.paidDay > day || state.paidDay == day && (state.paidSlots & 1 << slot) != 0)
			return;
		if (state.paidDay != day) {
			state.paidDay = day;
			state.paidSlots = 0;
		}
		state.paidSlots |= 1 << slot;
		state.ready.removeIf(entry -> entry.day == day && entry.slot == slot);
		commit(player, stored,
		       Map.of(Smp3Currency.TONGBAO, reward.tongbao(), Smp3Currency.COFFEE_STAMP, reward.coffeeStamp()),
		       reward.items());
	}
}
