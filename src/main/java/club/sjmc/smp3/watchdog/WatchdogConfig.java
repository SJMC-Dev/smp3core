package club.sjmc.smp3.watchdog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public record WatchdogConfig(boolean enabled, List<String> blacklist, List<String> whitelist, List<Rule> rules) {
	public static final String DEFAULTS = """
		{
		  "enabled": false,
		  "blacklist": [],
		  "whitelist": [],
		  "rules": [
		    { "entitiesPerChunk": 128, "minimumAgeSeconds": 240 },
		    { "entitiesPerChunk": 256, "minimumAgeSeconds": 120 }
		  ]
		}
		""";
	public record Rule(int entitiesPerChunk, int minimumAgeSeconds) {}
	public record Policy(boolean enabled, Set<Item> blacklist, Set<Item> whitelist, List<Rule> rules) {
		public boolean eligible(Item item) { return whitelist.contains(item) || !blacklist.contains(item); }
		public int minimumAge(int count) {
			for (Rule rule : rules)
				if (count >= rule.entitiesPerChunk())
					return rule.minimumAgeSeconds() * 20;
			return Integer.MAX_VALUE;
		}
	}

	public static WatchdogConfig parse(String text) {
		JsonObject root = JsonParser.parseString(text).getAsJsonObject();
		fields(root, Set.of("enabled", "blacklist", "whitelist", "rules"));
		if (!root.get("enabled").isJsonPrimitive() || !root.getAsJsonPrimitive("enabled").isBoolean())
			throw new IllegalArgumentException("enabled: expected boolean");
		List<Rule> rules = new ArrayList<>();
		Set<Integer> thresholds = new HashSet<>();
		if (!root.get("rules").isJsonArray())
			throw new IllegalArgumentException("rules: expected array");
		for (var value : root.getAsJsonArray("rules")) {
			if (!value.isJsonObject())
				throw new IllegalArgumentException("rules: expected rule objects");
			var rule = value.getAsJsonObject();
			fields(rule, Set.of("entitiesPerChunk", "minimumAgeSeconds"));
			int count = integer(rule.get("entitiesPerChunk"), "entitiesPerChunk", 1, 1_000_000);
			int age = integer(rule.get("minimumAgeSeconds"), "minimumAgeSeconds", 0, Integer.MAX_VALUE / 20);
			if (!thresholds.add(count))
				throw new IllegalArgumentException("Duplicate entitiesPerChunk: " + count);
			rules.add(new Rule(count, age));
		}
		if (rules.isEmpty() || rules.size() > 64)
			throw new IllegalArgumentException("rules: expected 1–64 entries");
		rules.sort(Comparator.comparingInt(Rule::entitiesPerChunk).reversed());
		return new WatchdogConfig(root.get("enabled").getAsBoolean(), list(root, "blacklist"), list(root, "whitelist"),
		                          List.copyOf(rules));
	}

	public Policy resolve() {
		return new Policy(enabled, resolve(blacklist, "blacklist"), resolve(whitelist, "whitelist"), rules);
	}

	private static Set<Item> resolve(List<String> entries, String field) {
		Set<Item> result = new HashSet<>();
		for (String entry : entries) {
			boolean tag = entry.startsWith("#");
			Identifier id = Identifier.parse(tag ? entry.substring(1) : entry);
			if (tag) {
				var values = BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, id));
				if (values.isEmpty())
					throw new IllegalArgumentException(field + ": unknown tag " + entry);
				values.get().forEach(holder -> result.add(holder.value()));
			} else {
				if (!BuiltInRegistries.ITEM.containsKey(id))
					throw new IllegalArgumentException(field + ": unknown item " + entry);
				result.add(BuiltInRegistries.ITEM.getValue(id));
			}
		}
		return Set.copyOf(result);
	}

	private static List<String> list(JsonObject root, String field) {
		if (!root.get(field).isJsonArray())
			throw new IllegalArgumentException(field + ": expected array of item IDs or #tags");
		List<String> result = new ArrayList<>();
		for (var value : root.getAsJsonArray(field)) {
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
				throw new IllegalArgumentException(field + ": expected item IDs or #tags");
			String entry = value.getAsString();
			if (Identifier.tryParse(entry.startsWith("#") ? entry.substring(1) : entry) == null)
				throw new IllegalArgumentException(field + ": invalid item ID or #tag " + entry);
			result.add(entry);
		}
		if (result.size() > 512)
			throw new IllegalArgumentException(field + ": maximum 512 entries");
		return List.copyOf(result);
	}

	private static int integer(JsonElement value, String field, int min, int max) {
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
			throw new IllegalArgumentException(field + ": expected integer");
		int number;
		try {
			number = value.getAsBigDecimal().intValueExact();
		} catch (ArithmeticException e) {
			throw new IllegalArgumentException(field + ": expected integer", e);
		}
		if (number < min || number > max)
			throw new IllegalArgumentException(field + ": expected " + min + "–" + max);
		return number;
	}

	private static void fields(JsonObject object, Set<String> expected) {
		if (!object.keySet().equals(expected))
			throw new IllegalArgumentException("Expected fields: " + expected);
	}
}
