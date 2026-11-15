package club.sjmc.smp3.economy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

public final class Smp3AccountStore {
	private static final class Holder {
		private static final Ledger LEDGER =
		    new Ledger(FabricLoader.getInstance().getConfigDir().resolve("smp3/economy.json"));
	}

	private Smp3AccountStore() {}

	public static long balance(UUID owner, Smp3Currency currency) {
		return Holder.LEDGER.balance(owner, currency.id().getPath());
	}

	public static void setBalance(UUID owner, Smp3Currency currency, long rawValue) {
		Holder.LEDGER.set(owner, currency.id().getPath(), rawValue);
	}

	public static JsonObject rewardState(UUID owner, String key) { return Holder.LEDGER.rewardState(owner, key); }

	public static void commitReward(UUID owner, String key, JsonObject expected, JsonObject state,
	                                Map<Smp3Currency, Integer> credits) {
		Map<String, Long> raw = new HashMap<>();
		credits.forEach((currency, amount) -> raw.put(currency.id().getPath(), Smp3Currency.rawUnits(amount)));
		Holder.LEDGER.updateReward(owner, key, expected, state, raw);
	}

	static final class Ledger {
		private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
		private final Path file;
		private Map<UUID, Map<String, Long>> balances = new HashMap<>();
		private Map<UUID, JsonObject> rewardStates = new HashMap<>();
		private boolean loaded;

		Ledger(Path file) { this.file = file; }

		synchronized long balance(UUID owner, String currency) {
			load();
			return balances.getOrDefault(owner, Map.of()).getOrDefault(currency, 0L);
		}

		synchronized void set(UUID owner, String currency, long value) {
			load();
			if (value < 0)
				throw new IllegalArgumentException("余额不能为负数");
			Map<String, Long> next = new HashMap<>(balances.getOrDefault(owner, Map.of()));
			if (value == 0)
				next.remove(currency);
			else
				next.put(currency, value);
			commit(owner, next, rewardStates);
		}

		synchronized JsonObject rewardState(UUID owner, String key) {
			load();
			JsonObject states = rewardStates.get(owner);
			return states == null || !states.has(key) ? null : states.getAsJsonObject(key).deepCopy();
		}

		// A receipt and its money changes become visible together, after the atomic file replacement.
		synchronized void updateReward(UUID owner, String key, JsonObject expected, JsonObject state,
		                               Map<String, Long> credits) {
			if (!Objects.equals(rewardState(owner, key), expected)) {
				throw new IllegalStateException("Reward state changed; reload before retrying");
			}
			Map<String, Long> next = new HashMap<>(balances.getOrDefault(owner, Map.of()));
			credits.forEach((currency, amount) -> {
				if (amount < 0) {
					throw new IllegalArgumentException("Negative reward");
				}
				next.put(currency, Math.addExact(next.getOrDefault(currency, 0L), amount));
			});
			Map<UUID, JsonObject> nextStates = new HashMap<>(rewardStates);
			JsonObject player = rewardStates.getOrDefault(owner, new JsonObject()).deepCopy();
			player.add(key, state.deepCopy());
			nextStates.put(owner, player);
			commit(owner, next, nextStates);
		}

		private void load() {
			if (loaded)
				return;
			if (!Files.exists(file)) {
				loaded = true;
				return;
			}
			try {
				JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				Map<UUID, Map<String, Long>> loadedBalances = new HashMap<>();
				for (var entry : root.getAsJsonObject("balances").entrySet()) {
					Map<String, Long> currencies = new HashMap<>();
					JsonElement value = entry.getValue();
					if (value.isJsonPrimitive())
						currencies.put("tongbao", value.getAsLong());
					else
						value.getAsJsonObject().entrySet().forEach(
						    c -> currencies.put(c.getKey(), c.getValue().getAsLong()));
					loadedBalances.put(UUID.fromString(entry.getKey()), currencies);
				}
				Map<UUID, JsonObject> loadedStates = new HashMap<>();
				if (root.has("rewardStates")) {
					root.getAsJsonObject("rewardStates")
					    .entrySet()
					    .forEach(
					        entry
					        -> loadedStates.put(UUID.fromString(entry.getKey()), entry.getValue().getAsJsonObject()));
				}
				rewardStates = loadedStates;
				balances = loadedBalances;
				loaded = true;
			} catch (IOException | RuntimeException e) {
				throw new IllegalStateException("读取经济账本失败: " + file, e);
			}
		}

		private void commit(UUID owner, Map<String, Long> currencies, Map<UUID, JsonObject> nextStates) {
			Map<UUID, Map<String, Long>> nextBalances = new HashMap<>(balances);
			if (currencies.isEmpty())
				nextBalances.remove(owner);
			else
				nextBalances.put(owner, currencies);
			JsonObject root = new JsonObject();
			root.add("balances", JSON.toJsonTree(nextBalances));
			root.add("rewardStates", JSON.toJsonTree(nextStates));
			try {
				Files.createDirectories(file.getParent());
				Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
				Files.writeString(temporary, JSON.toJson(root));
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				balances = nextBalances;
				rewardStates = nextStates;
			} catch (IOException e) {
				throw new IllegalStateException("保存经济账本失败: " + file, e);
			}
		}
	}
}
