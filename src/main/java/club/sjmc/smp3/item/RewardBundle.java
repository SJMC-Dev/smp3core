package club.sjmc.smp3.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import club.sjmc.smp3.economy.Smp3Currency;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public record RewardBundle(int tongbao, int coffeeStamp, List<JsonObject> items, List<JsonObject> effects) {
	public RewardBundle {
		items = List.copyOf(items);
		effects = List.copyOf(effects);
	}
	public Map<Smp3Currency, Integer> credits() {
		return Map.of(Smp3Currency.TONGBAO, tongbao, Smp3Currency.COFFEE_STAMP, coffeeStamp);
	}
	public static List<JsonObject> objects(JsonArray array) {
		return array.asList().stream().map(JsonElement::getAsJsonObject).toList();
	}
	public static int number(JsonObject object, String key, int fallback) {
		return object.has(key) ? object.get(key).getAsBigDecimal().intValueExact() : fallback;
	}
	private static int quantity(JsonObject grant, RandomGenerator random) {
		if (!grant.has("count"))
			return 1;
		var count = grant.get("count");
		if (!count.isJsonObject())
			return count.getAsBigDecimal().intValueExact();
		var range = count.getAsJsonObject();
		return (int)random.nextLong(number(range, "min", 1), (long)number(range, "max", 1) + 1);
	}
	public static RewardBundle resolve(List<JsonObject> grants, RandomGenerator random, ServerPlayer player) {
		int coins = 0, stamps = 0;
		var items = new ArrayList<JsonObject>();
		var effects = new ArrayList<JsonObject>();
		for (var grant : grants) {
			String type = grant.get("type").getAsString();
			int count = quantity(grant, random);
			switch (type) {
			case "item" -> {
				var item = grant.deepCopy();
				item.remove("type");
				item.addProperty("count", count);
				items.add(item);
			}
			case "currency" -> {
				if (grant.get("id").getAsString().equals("smp3:tongbao"))
					coins = Math.addExact(coins, count);
				else
					stamps = Math.addExact(stamps, count);
			}
			case "entity", "equipment" -> {
				var effect = grant.deepCopy();
				effect.addProperty("count", count);
				effects.add(effect);
			}
			case "random" -> {
				var entries = objects(grant.getAsJsonArray("options"));
				var pool = new ArrayList<>(entries);
				for (int i = 0; i < number(grant, "draws", 1); i++) {
					long total = pool.stream().mapToLong(e -> number(e, "weight", 1)).sum();
					long roll = random.nextLong(total);
					for (int j = 0; j < pool.size(); j++) {
						var entry = pool.get(j);
						roll -= number(entry, "weight", 1);
						if (roll >= 0)
							continue;
						var result = resolve(objects(entry.getAsJsonArray("grants")), random, player);
						coins = Math.addExact(coins, result.tongbao);
						stamps = Math.addExact(stamps, result.coffeeStamp);
						items.addAll(result.items);
						effects.addAll(result.effects);
						if (grant.has("unique") && grant.get("unique").getAsBoolean())
							pool.remove(j);
						break;
					}
				}
			}
			case "loot_table" -> {
				var key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(grant.get("id").getAsString()));
				var table = player.level()
				                .getServer()
				                .reloadableRegistries()
				                .lookup()
				                .lookupOrThrow(Registries.LOOT_TABLE)
				                .getOrThrow(key)
				                .value();
				var params = new LootParams.Builder(player.level())
				                 .withParameter(LootContextParams.ORIGIN, player.position())
				                 .withLuck(player.getLuck())
				                 .create(LootContextParamSets.CHEST);
				for (int i = 0; i < number(grant, "rolls", 1); i++)
					for (var stack : table.getRandomItems(params, random.nextLong()))
						if (!stack.isEmpty())
							items.add(ItemDefinitions.json(stack, player.registryAccess()));
			}
			default -> throw new IllegalArgumentException("Unknown grant: " + type);
			}
		}
		return new RewardBundle(coins, stamps, items, effects);
	}
	public static Component description(JsonObject grant, RegistryAccess registries) {
		String type = grant.get("type").getAsString();
		Component name = switch (type) {
			case "item" -> {
				var item = grant.deepCopy();
				item.remove("type");
				item.addProperty("count", 1);
				yield ItemDefinitions.stack(item, registries).getHoverName();
			}
			case "entity" ->
				Component.literal("生成 ").append(
				    BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(grant.get("id").getAsString()))
				        .getDescription());
			case "equipment" ->
				Component.literal("装备 ").append(
				    ItemDefinitions.stack(grant.getAsJsonObject("item"), registries).getHoverName());
			case "currency" ->
				Component.literal(grant.get("id").getAsString().equals("smp3:tongbao") ? "通宝" : "咖啡印章");
			case "loot_table" -> Component.literal("战利品：" + grant.get("id").getAsString());
			case "random" -> Component.literal("随机补给");
			default -> throw new IllegalArgumentException("Unknown grant: " + type);
		};
		String quantity = "1";
		if (grant.has("count")) {
			var count = grant.get("count");
			quantity = count.isJsonObject() ? count.getAsJsonObject().get("min").getAsString() + "–" +
			                                      count.getAsJsonObject().get("max").getAsString()
			                                : count.getAsString();
		} else if (type.equals("random"))
			quantity = Integer.toString(number(grant, "draws", 1));
		else if (type.equals("loot_table"))
			quantity = Integer.toString(number(grant, "rolls", 1));
		return name.copy().append(" x " + quantity);
	}

	public static void validate(List<JsonObject> grants, RegistryAccess registries) {
		for (var grant : grants) {
			String type = grant.get("type").getAsString();
			Set<String> fields = switch (type) {
				case "item" -> Set.of("type", "id", "count", "components");
				case "currency", "entity" -> Set.of("type", "id", "count");
				case "equipment" -> Set.of("type", "slot", "item");
				case "loot_table" -> Set.of("type", "id", "rolls");
				case "random" -> Set.of("type", "options", "draws", "unique");
				default -> throw new IllegalArgumentException("Unknown grant: " + type);
			};
			if (!fields.containsAll(grant.keySet()))
				throw new IllegalArgumentException("Unknown " + type + " grant field");
			int min = 1, max = 1;
			if (grant.has("count")) {
				var value = grant.get("count");
				if (value.isJsonObject()) {
					var range = value.getAsJsonObject();
					if (!range.keySet().equals(Set.of("min", "max")))
						throw new IllegalArgumentException("count needs min/max");
					min = number(range, "min", 1);
					max = number(range, "max", 1);
				} else
					min = max = number(grant, "count", 1);
			}
			if (min <= 0 || max < min)
				throw new IllegalArgumentException("Invalid reward count");
			switch (type) {
			case "item" -> {
				var item = grant.deepCopy();
				item.remove("type");
				item.addProperty("count", max);
				ItemDefinitions.validate(item, registries);
			}
			case "equipment" -> {
				var slot = EquipmentSlot.valueOf(grant.get("slot").getAsString().toUpperCase(Locale.ROOT));
				if (!Set.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
				            EquipmentSlot.OFFHAND)
				         .contains(slot))
					throw new IllegalArgumentException("Unsupported equipment slot");
				ItemDefinitions.validate(grant.getAsJsonObject("item"), registries);
				if (number(grant.getAsJsonObject("item"), "count", 1) != 1)
					throw new IllegalArgumentException("Equipment count must be one");
			}
			case "currency" -> {
				if (!Set.of("smp3:tongbao", "smp3:coffee_stamp").contains(grant.get("id").getAsString()))
					throw new IllegalArgumentException("Unknown currency");
			}
			case "entity" -> {
				var id = Identifier.parse(grant.get("id").getAsString());
				var registry = registries.lookupOrThrow(Registries.ENTITY_TYPE);
				if (!registry.containsKey(id) || !registry.getValue(id).canSummon())
					throw new IllegalArgumentException("Unknown or unsummonable entity reward: " + id);
			}
			case "random" -> {
				var options = grant.getAsJsonArray("options");
				int draws = number(grant, "draws", 1);
				if (draws < 1 || options.isEmpty() ||
				    grant.has("unique") && grant.get("unique").getAsBoolean() && draws > options.size())
					throw new IllegalArgumentException("Invalid reward draws");
				validateOptions(options, registries);
			}
			case "loot_table" -> {
				Identifier.parse(grant.get("id").getAsString());
				if (number(grant, "rolls", 1) < 1)
					throw new IllegalArgumentException("Invalid loot rolls");
			}
			}
		}
	}
	public static void validateOptions(JsonArray options, RegistryAccess registries) {
		if (options.isEmpty())
			throw new IllegalArgumentException("Empty reward pool");
		for (var option : objects(options)) {
			if (!Set.of("name", "weight", "grants").containsAll(option.keySet()) || number(option, "weight", 1) < 1)
				throw new IllegalArgumentException("Invalid reward option");
			if (!option.has("name") || option.get("name").getAsString().isBlank())
				throw new IllegalArgumentException("Reward option needs name");
			validate(objects(option.getAsJsonArray("grants")), registries);
		}
	}
}
