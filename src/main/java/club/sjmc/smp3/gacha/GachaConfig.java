package club.sjmc.smp3.gacha;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import club.sjmc.smp3.item.ItemDefinitions;
import club.sjmc.smp3.item.RewardBundle;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import static club.sjmc.smp3.config.JsonConfig.fields;
import static club.sjmc.smp3.config.JsonConfig.object;
import static club.sjmc.smp3.config.JsonConfig.array;
import static club.sjmc.smp3.config.JsonConfig.require;

public final class GachaConfig {
	public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
	public enum Tier { S, A, B }
	public enum Facing {
		POS_X("+x", 0, -1),
		NEG_X("-x", 0, 1),
		POS_Z("+z", 1, 0),
		NEG_Z("-z", -1, 0);
		public final String id;
		final int rightX, rightZ;
		Facing(String id, int x, int z) {
			this.id = id;
			rightX = x;
			rightZ = z;
		}
		static Facing parse(String id) {
			for (var facing : values())
				if (facing.id.equals(id))
					return facing;
			throw new IllegalArgumentException("displayFacing: +x, -x, +z, -z");
		}
	}
	public int animationTicks = 100, resultTicks = 60, actionbarTicks = 100;
	public PoolRules poolDefaults;
	public Map<String, Pool> pools = new LinkedHashMap<>();

	public static class PoolRules {
		public double sProbability = 0.02, aProbability = 0.10;
		public int pity = 80;
		public double displayOffsetX, displayOffsetY, displayOffsetZ;
		public double fireworkDistance = 3.2;
		public Map<Tier, List<Prize>> rewards = new EnumMap<>(Tier.class);
		public Map<Tier, List<Bonus>> fixedRewards = new EnumMap<>(Tier.class);
	}
	public static final class Pool extends PoolRules {
		public String dimension;
		public int minX, minY, minZ, maxX, maxY, maxZ;
		public String displayFacing = "+z";
		Facing facing() { return Facing.parse(displayFacing); }
		double displayX() { return (minX + (double)maxX + 1) / 2 + displayOffsetX; }
		double displayY(int draws) { return maxY + (draws == 10 ? 2.0 : 2.5) + displayOffsetY; }
		double displayZ() { return (minZ + (double)maxZ + 1) / 2 + displayOffsetZ; }
		public boolean contains(double x, double y, double z) {
			return x >= minX && x < (double)maxX + 1 && y >= minY && y < (double)maxY + 1 && z >= minZ &&
			    z < (double)maxZ + 1;
		}
		boolean overlaps(Pool p) {
			return dimension.equals(p.dimension) && minX <= p.maxX && maxX >= p.minX && minY <= p.maxY &&
			    maxY >= p.minY && minZ <= p.maxZ && maxZ >= p.minZ;
		}
		ServerLevel level(MinecraftServer server) {
			return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension)));
		}
	}
	public static class Bonus {
		public String id, label;
		public List<JsonObject> grants = new ArrayList<>();
		public List<String> commands = new ArrayList<>();
	}
	public static final class Prize extends Bonus {
		public int weight = 1, limit = -1;
		public JsonObject displayItem;
	}
	public Pool newPool() { return JSON.fromJson(JSON.toJsonTree(poolDefaults), Pool.class); }
	public Pool pool(String id) {
		var pool = pools.get(id);
		require(pool != null, "抽奖池不存在：" + id);
		return pool;
	}
	static String prizeKey(Tier tier, String id) { return tier + "/" + id; }
	public GachaConfig copy() { return JSON.fromJson(JSON.toJsonTree(this), GachaConfig.class); }

	public static GachaConfig parse(JsonObject json) {
		fields(json, "poolDefaults pools", "animationTicks resultTicks actionbarTicks");
		integerFields(json, "animationTicks resultTicks actionbarTicks");
		try {
			checkRules(object(json, "poolDefaults"), false);
		} catch (RuntimeException error) {
			throw new IllegalArgumentException("poolDefaults/" + error.getMessage(), error);
		}
		object(json, "pools").entrySet().forEach(entry -> {
			try {
				checkRules(entry.getValue().getAsJsonObject(), true);
			} catch (RuntimeException error) {
				throw new IllegalArgumentException("pools/" + entry.getKey() + "/" + error.getMessage(), error);
			}
		});
		return JSON.fromJson(json, GachaConfig.class);
	}
	private static void checkRules(JsonObject json, boolean region) {
		fields(json, "rewards fixedRewards" + (region ? " dimension minX minY minZ maxX maxY maxZ" : ""),
		       "sProbability aProbability pity displayOffsetX displayOffsetY displayOffsetZ fireworkDistance" +
		           (region ? " displayFacing" : ""));
		integerFields(json, "pity minX minY minZ maxX maxY maxZ");
		for (String table : List.of("rewards", "fixedRewards")) {
			var tiers = object(json, table);
			fields(tiers, "S A B", "");
			for (var entry : tiers.entrySet())
				for (var value : array(tiers, entry.getKey())) {
					var prize = value.getAsJsonObject();
					fields(prize, "id label",
					       "grants commands" + (table.equals("rewards") ? " weight limit displayItem" : ""));
					integerFields(prize, "weight limit");
				}
		}
	}
	private static void integerFields(JsonObject object, String names) {
		for (String name : names.split(" "))
			if (object.has(name)) {
				var value = object.get(name);
				require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(), name + (": expected "
				                                                                                  + "integer"));
				try {
					value.getAsBigDecimal().intValueExact();
				} catch (ArithmeticException error) {
					throw new IllegalArgumentException(name + ": expected 32-bit integer", error);
				}
			}
	}

	public void validate(MinecraftServer server) {
		require(animationTicks >= 20 && animationTicks <= 1200, "animationTicks: 20–1200");
		require(resultTicks >= 0 && resultTicks <= 1200, "resultTicks: 0–1200");
		require(actionbarTicks >= 0 && actionbarTicks <= 1200, "actionbarTicks: 0–1200");
		validateRules(poolDefaults, server, "poolDefaults");
		require(pools != null, "pools: expected object");
		pools.forEach((id, p) -> {
			require(id.matches("[A-Za-z0-9_-]{1,32}"), "pools/" + id + ": invalid ID");
			try {
				require(p != null && p.level(server) != null, "dimension: unknown dimension");
				require(p.minX <= p.maxX && p.minY <= p.maxY && p.minZ <= p.maxZ &&
				            Math.abs((long)p.minX) <= 30000000 && Math.abs((long)p.maxX) <= 30000000 &&
				            Math.abs((long)p.minZ) <= 30000000 && Math.abs((long)p.maxZ) <= 30000000 &&
				            p.minY >= p.level(server).getMinY() && p.maxY <= p.level(server).getMaxY() &&
				            (long)p.maxX - p.minX <= 64 && (long)p.maxY - p.minY <= 64 && (long)p.maxZ - p.minZ <= 64,
				        "region: ordered endpoints, at most 65 blocks per side");
				p.facing();
				validateRules(p, server, "rules");
			} catch (RuntimeException e) {
				throw new IllegalArgumentException("pools/" + id + "/" + e.getMessage(), e);
			}
		});
		var regions = new ArrayList<>(pools.values());
		for (int i = 0; i < regions.size(); i++)
			for (int j = i + 1; j < regions.size(); j++)
				require(!regions.get(i).overlaps(regions.get(j)), "pools: regions overlap");
	}
	private static void validateRules(PoolRules rules, MinecraftServer server, String path) {
		try {
			require(rules != null, "missing rules");
			require(Double.isFinite(rules.sProbability) && Double.isFinite(rules.aProbability) &&
			            rules.sProbability >= 0 && rules.aProbability >= 0 &&
			            rules.sProbability + rules.aProbability <= 1,
			        "sProbability/aProbability: nonnegative, sum <= 1");
			require(rules.pity > 0 && rules.pity <= 1000000, "pity: 1–1000000");
			require(Double.isFinite(rules.fireworkDistance) && rules.fireworkDistance >= 0 &&
			            rules.fireworkDistance <= 64,
			        "fireworkDistance: 0–64");
			for (double offset : new double[] {rules.displayOffsetX, rules.displayOffsetY, rules.displayOffsetZ})
				require(Double.isFinite(offset) && Math.abs(offset) <= 64, "displayOffset: -64–64");
			for (Tier tier : Tier.values()) {
				var random = rules.rewards.get(tier);
				var fixed = rules.fixedRewards.get(tier);
				require(random != null && fixed != null, tier + ": missing rewards/fixedRewards array");
				validatePrizes(random, tier, "rewards", server);
				validatePrizes(fixed, tier, "fixedRewards", server);
				require(random.stream().mapToLong(p -> p.weight).sum() <= Integer.MAX_VALUE,
				        tier + ": total weight too large");
				if (tier != Tier.B)
					require(!fixed.isEmpty() || random.stream().anyMatch(p -> p.limit == -1),
					        tier + ": requires fixed rewards or an unlimited prize");
			}
		} catch (RuntimeException e) {
			throw new IllegalArgumentException(path + "/" + e.getMessage(), e);
		}
	}
	private static void validatePrizes(List<? extends Bonus> prizes, Tier tier, String table, MinecraftServer server) {
		var ids = new HashSet<String>();
		for (var prize : prizes) {
			try {
				require(prize.id != null && prize.id.matches("[A-Za-z0-9_-]{1,64}") && ids.add(prize.id),
				        "invalid or duplicate ID");
				require(prize.label != null && !prize.label.isBlank() && prize.label.length() <= 256,
				        "label: 1–256 characters");
				require(!prize.grants.isEmpty() || !prize.commands.isEmpty(), "empty reward");
				RewardBundle.validate(prize.grants, server.registryAccess());
				prize.commands.forEach(command -> GachaCommandRewards.validate(server, command));
				if (prize instanceof Prize random) {
					require(random.weight > 0 && random.limit >= -1, "weight > 0, limit >= -1");
					if (random.displayItem != null)
						ItemDefinitions.validate(random.displayItem, server.registryAccess());
				}
			} catch (RuntimeException e) {
				throw new IllegalArgumentException(table + "/" + tier + "/" + prize.id + "/" + e.getMessage(), e);
			}
		}
	}
}
