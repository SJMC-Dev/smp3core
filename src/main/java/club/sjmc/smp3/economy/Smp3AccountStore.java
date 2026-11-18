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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

public final class Smp3AccountStore {
	private static final class Holder {
		private static final Ledger LEDGER =
		    new Ledger(FabricLoader.getInstance().getConfigDir().resolve("smp3/accounts"));
	}

	private Smp3AccountStore() {}

	public static long balance(UUID owner, Smp3Currency currency) {
		return Holder.LEDGER.balance(owner, currency.id().getPath());
	}

	public static void setBalance(UUID owner, Smp3Currency currency, long rawValue) {
		Holder.LEDGER.set(owner, currency.id().getPath(), rawValue);
	}

	public static JsonObject rewardState(UUID owner, String key, String... fields) {
		return Holder.LEDGER.rewardState(owner, key, fields);
	}

	public static void commitReward(UUID owner, String key, JsonObject expected, JsonObject state,
	                                Map<Smp3Currency, Integer> credits) {
		if (credits.values().stream().anyMatch(amount -> amount < 0))
			throw new IllegalArgumentException("Negative reward");
		commitChange(owner, key, expected, state, credits);
	}

	public static void commitChange(UUID owner, String key, JsonObject expected, JsonObject state,
	                                Map<Smp3Currency, Integer> changes) {
		Map<String, Long> raw = new HashMap<>();
		changes.forEach((currency, amount) -> raw.put(currency.id().getPath(), Smp3Currency.rawUnits(amount)));
		Holder.LEDGER.update(owner, key, expected, state, raw);
	}

	public static void saveRewardFields(String key, Map<UUID, JsonObject> fields) {
		Holder.LEDGER.saveRewardFields(key, fields);
	}

	static final class Ledger {
		private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
		private record Account(Map<String, Long> balances, JsonObject rewards) {}
		private final Path directory;
		private final Map<UUID, Account> accounts = new HashMap<>();

		Ledger(Path directory) { this.directory = directory; }

		synchronized long balance(UUID owner, String currency) {
			return load(owner).balances.getOrDefault(currency, 0L);
		}

		synchronized void set(UUID owner, String currency, long value) {
			if (value < 0)
				throw new IllegalArgumentException("余额不能为负数");
			Account account = load(owner);
			Map<String, Long> balances = new HashMap<>(account.balances);
			if (value == 0)
				balances.remove(currency);
			else
				balances.put(currency, value);
			commit(owner, new Account(balances, account.rewards));
		}

		synchronized JsonObject rewardState(UUID owner, String key, String... fields) {
			JsonObject rewards = load(owner).rewards;
			if (!rewards.has(key))
				return null;
			JsonObject state = rewards.getAsJsonObject(key);
			if (fields.length == 0)
				return state.deepCopy();
			JsonObject selected = new JsonObject();
			for (String field : fields)
				if (state.has(field))
					selected.add(field, state.get(field).deepCopy());
			return selected;
		}

		synchronized void update(UUID owner, String key, JsonObject expected, JsonObject state,
		                         Map<String, Long> changes) {
			Account account = load(owner);
			if (!Objects.equals(account.rewards.get(key), expected))
				throw new IllegalStateException("Reward state changed; reload before retrying");
			Map<String, Long> balances = new HashMap<>(account.balances);
			changes.forEach((currency, amount) -> {
				long value = Math.addExact(balances.getOrDefault(currency, 0L), amount);
				if (value < 0)
					throw new IllegalArgumentException("Insufficient balance");
				balances.put(currency, value);
			});
			JsonObject rewards = account.rewards.deepCopy();
			rewards.add(key, state.deepCopy());
			commit(owner, new Account(balances, rewards));
		}

		synchronized void saveRewardFields(String key, Map<UUID, JsonObject> fields) {
			fields.forEach((owner, update) -> {
				Account account = load(owner);
				JsonObject rewards = account.rewards.deepCopy();
				JsonObject state = rewards.has(key) ? rewards.getAsJsonObject(key) : new JsonObject();
				update.entrySet().forEach(entry -> state.add(entry.getKey(), entry.getValue().deepCopy()));
				rewards.add(key, state);
				if (!rewards.equals(account.rewards))
					commit(owner, new Account(account.balances, rewards));
			});
		}

		private Account load(UUID owner) {
			return accounts.computeIfAbsent(owner, id -> {
				Path file = directory.resolve(id + ".json");
				if (!Files.exists(file))
					return new Account(Map.of(), new JsonObject());
				try {
					JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
					Map<String, Long> balances = new HashMap<>();
					root.getAsJsonObject("balances")
					    .entrySet()
					    .forEach(entry -> balances.put(entry.getKey(), entry.getValue().getAsLong()));
					return new Account(balances,
					                   Objects.requireNonNull(root.getAsJsonObject("rewardStates"), "rewardStates"));
				} catch (IOException | RuntimeException error) {
					throw new IllegalStateException("读取玩家账本失败: " + file, error);
				}
			});
		}

		// Only the affected account is replaced; currency, quest state and delivery receipts commit together.
		private void commit(UUID owner, Account account) {
			Path file = directory.resolve(owner + ".json");
			JsonObject root = new JsonObject();
			root.add("balances", JSON.toJsonTree(account.balances));
			root.add("rewardStates", account.rewards);
			try {
				Files.createDirectories(directory);
				Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
				Files.writeString(temporary, JSON.toJson(root));
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				accounts.put(owner, account);
			} catch (IOException error) {
				throw new IllegalStateException("保存玩家账本失败: " + file, error);
			}
		}
	}
}
