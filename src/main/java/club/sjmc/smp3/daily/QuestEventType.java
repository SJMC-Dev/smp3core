package club.sjmc.smp3.daily;

import java.util.Set;

public enum QuestEventType {
	ENTITY_KILLED("entity_killed", "entity"),
	ITEM_CRAFTED("item_crafted", "item", "potion", "effect", "pattern", "template", "source"),
	INVENTORY_CHANGED("inventory_changed", "item", "potion", "effect", "pattern"),
	BLOCK_BROKEN("block_broken", "block", "block_state"),
	CROP_HARVESTED("crop_harvested", "block", "block_state"),
	BLOCK_PLACED("block_placed", "block", "block_state"),
	FISH_CAUGHT("fish_caught", "item"),
	BIOME_VISITED("biome_visited"),
	STRUCTURE_VISITED("structure_visited"),
	VILLAGER_TRADE("villager_trade", "entity", "item"),
	PIGLIN_BARTER("piglin_barter", "entity", "item");

	private final String id;
	private final Set<String> fields;

	QuestEventType(String id, String... fields) {
		this.id = id;
		this.fields = Set.of(fields);
	}

	public String id() { return id; }

	public boolean allows(String field) {
		return field.equals("biome") || field.equals("dimension") || field.equals("structure") ||
		    fields.contains(field);
	}

	public static QuestEventType find(String id) {
		for (var type : values()) {
			if (type.id.equals(id)) {
				return type;
			}
		}
		return null;
	}
}
