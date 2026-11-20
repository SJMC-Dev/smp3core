package club.sjmc.smp3.gacha;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;

import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.item.ItemDelivery;
import club.sjmc.smp3.item.RewardBundle;

import net.minecraft.server.level.ServerPlayer;

import static club.sjmc.smp3.gacha.GachaConfig.JSON;

public final class GachaLedger {
	private GachaLedger() {}
	public static final class PoolState {
		public long total;
		public int misses;
		public Map<String, Long> obtained = new HashMap<>();
	}
	public static final class Data {
		public Map<String, PoolState> pools = new HashMap<>();
		public List<Round> pending = new ArrayList<>();
		public PoolState pool(String id) { return pools.computeIfAbsent(id, ignored -> new PoolState()); }
	}
	public record Draw(GachaConfig.Tier tier, String prize, String label, boolean hardPity, boolean tenGuarantee) {}
	record History(UUID id, long time, long firstDraw, List<Draw> draws, RewardBundle rewards, List<String> commands) {}
	public enum Status { READY, REVIEW, DONE }
	public static final class Command {
		public String text;
		public Status status = Status.READY;
		public Command(String text) { this.text = text; }
	}
	public static final class Round {
		public UUID id;
		public String pool, playerName;
		public long time, firstDraw;
		public List<Draw> draws = new ArrayList<>();
		public RewardBundle rewards;
		public List<Command> commands = new ArrayList<>();
		public boolean delivered, archived;
		public boolean complete() {
			return delivered && archived && commands.stream().allMatch(c -> c.status == Status.DONE);
		}
	}
	public record Stored(JsonObject ledger, Data data) {
		public Round round(UUID id) {
			return data.pending.stream().filter(r -> r.id.equals(id)).findFirst().orElseThrow();
		}
	}
	public static Stored read(UUID owner) {
		var ledger = Smp3AccountStore.rewardState(owner, ItemDelivery.LEDGER_KEY);
		var data = ledger != null && ledger.has("gacha") ? JSON.fromJson(ledger.get("gacha"), Data.class) : new Data();
		return new Stored(ledger, data);
	}
	static void save(UUID owner, Stored stored, List<JsonObject> items, List<JsonObject> effects,
	                 Map<Smp3Currency, Integer> credits) {
		var next = stored.ledger == null ? new JsonObject() : stored.ledger.deepCopy();
		stored.data.pending.removeIf(Round::complete);
		next.add("gacha", JSON.toJsonTree(stored.data));
		ItemDelivery.queue(next, items);
		ItemDelivery.queueEffects(next, effects);
		Smp3AccountStore.commitReward(owner, ItemDelivery.LEDGER_KEY, stored.ledger, next, credits);
	}
	static void reserve(ServerPlayer player, Stored stored, List<JsonObject> returned) {
		ItemDelivery.transact(
		    player, inventory -> true, returned, next -> next.add("gacha", JSON.toJsonTree(stored.data)), Map.of());
	}
	static void save(UUID owner, Stored stored) { save(owner, stored, List.of(), List.of(), Map.of()); }
}
