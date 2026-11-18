package club.sjmc.smp3.shop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.item.ItemDefinitions;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record ShopCatalog(List<ShopNode> shops, Map<String, ShopEntry> entries) {
	public ShopCatalog {
		shops = List.copyOf(shops);
		entries = Map.copyOf(entries);
	}

	public static ShopCatalog parse(JsonObject root, RegistryAccess registries) {
		fields(root, "shops");
		var entries = new HashMap<String, ShopEntry>();
		var ids = new HashSet<String>();
		var shops = new ArrayList<ShopNode>();
		for (var value : GsonHelper.getAsJsonArray(root, "shops"))
			shops.add(node(value.getAsJsonObject(), "shops", 0, ids, entries, registries));
		return new ShopCatalog(shops, entries);
	}

	private static ShopNode node(JsonObject json, String parent, int depth, Set<String> ids,
	                             Map<String, ShopEntry> entries, RegistryAccess registries) {
		String path = parent + "/" + json.get("id");
		try {
			fields(json, "id", "name", "icon", "children", "entries");
			String id = unique(json, ids);
			if (depth >= 32 || ids.size() > 4096)
				throw new IllegalArgumentException("Shop tree exceeds 32 levels or 4096 nodes");
			var children = new ArrayList<ShopNode>();
			var items = new ArrayList<ShopEntry>();
			if (json.has("children"))
				for (var child : GsonHelper.getAsJsonArray(json, "children"))
					children.add(node(child.getAsJsonObject(), path, depth + 1, ids, entries, registries));
			if (json.has("entries"))
				for (var value : GsonHelper.getAsJsonArray(json, "entries")) {
					var definition = value.getAsJsonObject();
					try {
						ShopEntry entry = entry(definition, registries);
						if (entries.putIfAbsent(entry.id(), entry) != null)
							throw new IllegalArgumentException("Duplicate entry ID");
						items.add(entry);
					} catch (RuntimeException error) {
						throw new IllegalArgumentException("entry " + definition.get("id") + ": " + error.getMessage(),
						                                   error);
					}
				}
			return new ShopNode(id, string(json, "name"), icon(json), children, items);
		} catch (RuntimeException error) {
			throw new IllegalArgumentException(path + ": " + error.getMessage(), error);
		}
	}

	private static ShopEntry entry(JsonObject json, RegistryAccess registries) {
		fields(json, "id", "direction", "name", "description", "icon", "item", "accept", "currency", "price",
		       "dailyLimit", "advancedDailyLimit");
		String direction = string(json, "direction");
		if (!direction.equals("buy") && !direction.equals("sell"))
			throw new IllegalArgumentException("direction must be buy or sell");
		boolean buy = direction.equals("buy");
		if (json.has(buy ? "accept" : "item"))
			throw new IllegalArgumentException("Use item for buy, accept for sell");
		JsonObject definition = GsonHelper.getAsJsonObject(json, buy ? "item" : "accept");
		TagKey<Item> tag = null;
		JsonObject stack = definition.deepCopy();
		if (!buy) {
			fields(definition, "item", "count", "components");
			String selector = string(definition, "item");
			if (selector.startsWith("#")) {
				if (definition.has("components"))
					throw new IllegalArgumentException("Tag selectors cannot specify components");
				tag = TagKey.create(Registries.ITEM, Identifier.parse(selector.substring(1)));
				if (registries.lookupOrThrow(Registries.ITEM).get(tag).isEmpty())
					throw new IllegalArgumentException("Unknown item tag: " + selector);
			}
			stack.remove("item");
			stack.addProperty("id", selector);
		}
		int amount = integer(definition, "count", 1);
		int price = integer(json, "price", 0);
		int limit = integer(json, "dailyLimit", -1), advanced = integer(json, "advancedDailyLimit", limit);
		if (amount <= 0 || price <= 0 || limit < -1 || advanced < -1)
			throw new IllegalArgumentException("Positive count/price and limits >= -1 required");
		ItemStack template = ItemStack.EMPTY;
		if (tag == null) {
			ItemDefinitions.validate(stack, registries);
			template = ItemDefinitions.stack(stack, registries);
			if (template.isEmpty())
				throw new IllegalArgumentException("Empty item");
		}
		Item icon = json.has("icon") || tag != null ? icon(json) : template.getItem();
		Component name =
		    json.has("name") || tag != null ? Component.literal(string(json, "name")) : template.getHoverName();
		Smp3Currency currency = Smp3Currency.byId(json.has("currency") ? string(json, "currency") : "smp3:tongbao");
		if (currency == null)
			throw new IllegalArgumentException("Unknown currency");
		var description = new ArrayList<String>();
		if (json.has("description"))
			for (var line : GsonHelper.getAsJsonArray(json, "description"))
				description.add(text(line));
		return new ShopEntry(string(json, "id"), buy ? TradeDirection.BUY : TradeDirection.SELL, name, description,
		                     icon, template, tag, buy ? stack : null, currency, amount, price, limit, advanced);
	}

	private static Item icon(JsonObject json) {
		Identifier id = Identifier.parse(string(json, "icon"));
		Item item = BuiltInRegistries.ITEM.getValue(id);
		if (item == null || item == Items.AIR)
			throw new IllegalArgumentException("Unknown icon: " + id);
		return item;
	}
	private static String unique(JsonObject json, Set<String> ids) {
		String id = string(json, "id");
		if (!ids.add(id))
			throw new IllegalArgumentException("Duplicate ID: " + id);
		return id;
	}
	private static String string(JsonObject json, String key) {
		String value = text(json.get(key));
		if (value.isBlank() || key.equals("id") && value.length() > 128)
			throw new IllegalArgumentException(key + " must not be blank");
		return value;
	}
	private static String text(JsonElement value) {
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
			throw new IllegalArgumentException("Expected a string");
		return value.getAsString();
	}
	private static int integer(JsonObject json, String key, int fallback) {
		if (!json.has(key))
			return fallback;
		var value = json.get(key);
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
			throw new IllegalArgumentException(key + " must be an integer");
		return value.getAsBigDecimal().intValueExact();
	}
	private static void fields(JsonObject json, String... allowed) {
		var unknown = new HashSet<>(json.keySet());
		unknown.removeAll(Set.of(allowed));
		if (!unknown.isEmpty())
			throw new IllegalArgumentException("Unknown fields: " + unknown);
	}
}
