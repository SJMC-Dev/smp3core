package club.sjmc.smp3.item;

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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class ItemDefinitions {
	public static final DataComponentType<JsonObject> PACK = Registry.register(
	    BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath("smp3", "pack"),
	    DataComponentType.<JsonObject>builder()
	        .persistent(Codec.PASSTHROUGH.xmap(value
			                                   -> value.convert(JsonOps.INSTANCE).getValue().getAsJsonObject(),
			                                   value -> new Dynamic<>(JsonOps.INSTANCE, value)))
	        .networkSynchronized(ByteBufCodecs.STRING_UTF8.map(
	            value -> JsonParser.parseString(value).getAsJsonObject(), JsonObject::toString))
	        .build());
	private ItemDefinitions() {}
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
		if (!Set.of("options", "draws").containsAll(pack.keySet()) || !pack.has("options") ||
		    integer(pack, "draws", 1) < 1)
			throw new IllegalArgumentException("Pack needs options and positive draws");
		if (packItem.choice() && pack.has("draws"))
			throw new IllegalArgumentException("Choice pack selects one bundle");
		RewardBundle.validateOptions(pack.getAsJsonArray("options"), registries);
	}
	public static RewardBundle contents(ItemStack pack, int option, RandomGenerator random, ServerPlayer player) {
		JsonObject definition = pack.get(PACK);
		if (((PackItem)pack.getItem()).choice())
			return RewardBundle.resolve(
			    RewardBundle.objects(
			        definition.getAsJsonArray("options").get(option).getAsJsonObject().getAsJsonArray("grants")),
			    random, player);
		var draw = definition.deepCopy();
		draw.addProperty("type", "random");
		return RewardBundle.resolve(List.of(draw), random, player);
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
