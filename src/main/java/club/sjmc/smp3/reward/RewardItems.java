package club.sjmc.smp3.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class RewardItems {
	public static final DataComponentType<JsonObject> PACK = Registry.register(
	    BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath("smp3", "pack"),
	    DataComponentType.<JsonObject>builder()
	        .persistent(Codec.PASSTHROUGH.xmap(value
			                                   -> value.convert(JsonOps.INSTANCE).getValue().getAsJsonObject(),
			                                   value -> new Dynamic<>(JsonOps.INSTANCE, value)))
	        .networkSynchronized(ByteBufCodecs.STRING_UTF8.map(
	            value -> JsonParser.parseString(value).getAsJsonObject(), JsonObject::toString))
	        .build());
	private RewardItems() {}
	public static void register() {}

	public static ItemStack stack(JsonObject item, RegistryAccess registries) {
		int count = integer(item, "count", 1);
		if (count <= 0)
			throw new IllegalArgumentException("Item count must be positive");
		JsonObject single = item.deepCopy();
		single.addProperty("count", 1);
		ItemStack stack =
		    ItemStack.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), single).getOrThrow();
		stack.setCount(count);
		return stack;
	}
	public static JsonObject json(ItemStack stack, RegistryAccess registries) {
		var json =
		    ItemStack.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), stack.copyWithCount(1))
		        .getOrThrow()
		        .getAsJsonObject();
		json.addProperty("count", stack.getCount());
		return json;
	}
	public static void validate(JsonObject item, RegistryAccess registries) {
		if (!Set.of("id", "count", "components").containsAll(item.keySet()))
			throw new IllegalArgumentException("Unknown item field");
		ItemStack stack = stack(item, registries);
		if (!(stack.getItem() instanceof PackItem packItem))
			return;
		JsonObject pack = stack.get(PACK);
		if (pack == null)
			throw new IllegalArgumentException("Missing smp3:pack component");
		if (packItem.choice()) {
			if (!pack.keySet().equals(Set.of("options")) || pack.getAsJsonArray("options").isEmpty())
				throw new IllegalArgumentException("Choice pack needs options");
			for (var option : pack.getAsJsonArray("options")) {
				var entry = option.getAsJsonObject();
				if (!entry.keySet().equals(Set.of("name", "items")) || entry.get("name").getAsString().isBlank() ||
				    entry.getAsJsonArray("items").isEmpty())
					throw new IllegalArgumentException("Invalid choice option");
				for (var content : entry.getAsJsonArray("items"))
					validate(content.getAsJsonObject(), registries);
			}
		} else {
			if (!Set.of("draws", "entries").containsAll(pack.keySet()) || integer(pack, "draws", 1) < 1 ||
			    !pack.has("entries") || pack.getAsJsonArray("entries").isEmpty())
				throw new IllegalArgumentException("Random pack needs entries and positive draws");
			long total = 0;
			for (var value : pack.getAsJsonArray("entries")) {
				var entry = value.getAsJsonObject();
				if (!entry.keySet().equals(Set.of("item", "weight", "min", "max")))
					throw new IllegalArgumentException("Invalid random entry fields");
				int weight = integer(entry, "weight", 1), min = integer(entry, "min", 1),
				    max = integer(entry, "max", 1);
				if (weight <= 0 || min <= 0 || max < min)
					throw new IllegalArgumentException("Invalid weight or quantity range");
				total = Math.addExact(total, weight);
				if (integer(entry.getAsJsonObject("item"), "count", 1) != 1)
					throw new IllegalArgumentException("Use min/max for random quantities");
				validate(entry.getAsJsonObject("item"), registries);
			}
		}
	}
	public static List<JsonObject> contents(ItemStack pack, int option, RandomGenerator random) {
		JsonObject definition = pack.get(PACK);
		if (((PackItem)pack.getItem()).choice())
			return definition.getAsJsonArray("options")
			    .get(option)
			    .getAsJsonObject()
			    .getAsJsonArray("items")
			    .asList()
			    .stream()
			    .map(v -> v.getAsJsonObject().deepCopy())
			    .toList();
		var entries = definition.getAsJsonArray("entries");
		long total = entries.asList().stream().mapToLong(v -> v.getAsJsonObject().get("weight").getAsLong()).sum();
		List<JsonObject> result = new ArrayList<>();
		for (int i = 0; i < integer(definition, "draws", 1); i++) {
			long roll = random.nextLong(total);
			for (var value : entries) {
				var entry = value.getAsJsonObject();
				roll -= entry.get("weight").getAsLong();
				if (roll < 0) {
					JsonObject item = entry.getAsJsonObject("item").deepCopy();
					item.addProperty(
					    "count", random.nextLong(entry.get("min").getAsInt(), (long)entry.get("max").getAsInt() + 1));
					result.add(item);
					break;
				}
			}
		}
		return result;
	}
	private static int integer(JsonObject object, String key, int fallback) {
		if (!object.has(key))
			return fallback;
		var value = object.get(key);
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
			throw new IllegalArgumentException(key + " must be an integer");
		return value.getAsBigDecimal().intValueExact();
	}
}
