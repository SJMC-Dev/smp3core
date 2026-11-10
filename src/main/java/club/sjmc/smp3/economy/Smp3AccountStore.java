package club.sjmc.smp3.economy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import club.sjmc.smp3.Smp3Core;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 货币账本：按玩家 UUID + 货币 path 记账，持久化到 {@code config/smp3/economy.json}。
 *
 * <p>余额以「原始单位」保存；每次变更立即落盘（交易频率低，保证崩溃不丢账）。</p>
 */
public final class Smp3AccountStore {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir()
			.resolve("smp3").resolve("economy.json");
	/** UUID → (货币 path → 原始值)。 */
	private static final Map<UUID, Map<String, Long>> BALANCES = new HashMap<>();

	private static boolean loaded;

	private Smp3AccountStore() {
	}

	/** 读取余额（原始单位）。 */
	public static long balance(UUID owner, Smp3Currency currency) {
		load();
		Map<String, Long> perCurrency = BALANCES.get(owner);
		return perCurrency == null ? 0L : perCurrency.getOrDefault(currency.id().getPath(), 0L);
	}

	/** 写入余额（原始单位）并立即持久化。 */
	public static void setBalance(UUID owner, Smp3Currency currency, long rawValue) {
		load();
		Map<String, Long> perCurrency = BALANCES.computeIfAbsent(owner, key -> new HashMap<>());
		if (rawValue == 0) {
			perCurrency.remove(currency.id().getPath());
		} else {
			perCurrency.put(currency.id().getPath(), rawValue);
		}
		if (perCurrency.isEmpty()) {
			BALANCES.remove(owner);
		}
		save();
	}

	private static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		if (!Files.exists(FILE)) {
			return;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8))
					.getAsJsonObject();
			JsonObject balances = root.getAsJsonObject("balances");
			if (balances == null) {
				return;
			}
			for (Map.Entry<String, JsonElement> entry : balances.entrySet()) {
				UUID owner = UUID.fromString(entry.getKey());
				JsonElement value = entry.getValue();
				Map<String, Long> perCurrency = new HashMap<>();
				if (value.isJsonPrimitive()) {
					// 旧格式：uuid -> 数值，视为通宝余额
					perCurrency.put(Smp3Currency.TONGBAO.id().getPath(), value.getAsLong());
				} else {
					for (Map.Entry<String, JsonElement> currency : value.getAsJsonObject().entrySet()) {
						perCurrency.put(currency.getKey(), currency.getValue().getAsLong());
					}
				}
				BALANCES.put(owner, perCurrency);
			}
		} catch (Exception e) {
			Smp3Core.LOGGER.error("[SMP3] 读取经济数据失败（{}）：{}", FILE, e.toString());
		}
	}

	private static void save() {
		JsonObject root = new JsonObject();
		JsonObject balances = new JsonObject();
		BALANCES.forEach((owner, perCurrency) -> {
			JsonObject currencyObject = new JsonObject();
			perCurrency.forEach(currencyObject::addProperty);
			balances.add(owner.toString(), currencyObject);
		});
		root.add("balances", balances);

		try {
			Files.createDirectories(FILE.getParent());
			Path tmp = FILE.resolveSibling("economy.json.tmp");
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Smp3Core.LOGGER.error("[SMP3] 写入经济数据失败（{}）：{}", FILE, e.toString());
		}
	}
}
