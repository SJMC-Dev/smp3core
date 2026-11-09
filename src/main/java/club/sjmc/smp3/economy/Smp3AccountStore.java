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
 * 通宝账本（按玩家 UUID 记账），持久化到 {@code config/smp3/economy.json}。
 *
 * <p>余额以「原始单位」保存（1 通宝 = {@value Smp3Currency#RAW_PER_UNIT}），
 * 每次变更立即落盘（交易频率低，保证崩溃不丢账）。</p>
 */
public final class Smp3AccountStore {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir()
			.resolve("smp3").resolve("economy.json");
	private static final Map<UUID, Long> BALANCES = new HashMap<>();

	private static boolean loaded;

	private Smp3AccountStore() {
	}

	/** 读取余额（原始单位）。 */
	public static long balance(UUID owner) {
		load();
		return BALANCES.getOrDefault(owner, 0L);
	}

	/** 写入余额（原始单位）并立即持久化。 */
	public static void setBalance(UUID owner, long rawValue) {
		load();
		if (rawValue == 0) {
			BALANCES.remove(owner);
		} else {
			BALANCES.put(owner, rawValue);
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
				BALANCES.put(UUID.fromString(entry.getKey()), entry.getValue().getAsLong());
			}
		} catch (Exception e) {
			Smp3Core.LOGGER.error("[SMP3] 读取经济数据失败（{}）：{}", FILE, e.toString());
		}
	}

	private static void save() {
		JsonObject root = new JsonObject();
		JsonObject balances = new JsonObject();
		BALANCES.forEach((owner, value) -> balances.addProperty(owner.toString(), value));
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
