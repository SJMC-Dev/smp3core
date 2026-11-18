package club.sjmc.smp3.daily;

import java.util.Set;

public enum QuestEventType {
	ENTITY_KILLED("entity_killed", "entity"),
	ITEM_CRAFTED("item_crafted", "item", "potion", "effect", "pattern", "template", "source"),
	INVENTORY_CHANGED("inventory_changed", "item", "potion", "effect", "pattern"),
	BLOCK_BROKEN("block_broken", "block", "block_state"),
	CROP_HARVESTED("crop_harvested", "item", "block", "block_state"),
	BLOCK_PLACED("block_placed", "block", "block_state"),
	FISH_CAUGHT("fish_caught", "item", "loot"),
	BIOME_VISITED("biome_visited"),
	STRUCTURE_VISITED("structure_visited"),
	VILLAGER_TRADE("villager_trade", "entity", "item", "profession"),
	PIGLIN_BARTER("piglin_barter", "entity", "item"),
	ENTITY_BRED("entity_bred", "entity"),
	ENTITY_TAMED("entity_tamed", "entity"),
	ENTITY_TRUSTED("entity_trusted", "entity"),
	ENTITY_BUILT("entity_built", "entity"),
	ENTITY_INTERACTED("entity_interacted", "entity", "profession", "action"),
	ENTITY_CONVERTED("entity_converted", "entity", "result", "variant"),
	PORTAL_ACTIVATED("portal_activated"),
	PROJECTILE_BLOCKED("projectile_blocked", "entity"),
	ARCHAEOLOGY_COMPLETED("archaeology_completed", "item"),
	DISTANCE_TRAVELED("distance_traveled", "movement"),
	RAID_WON("raid_won"),
	CONDUIT_ACTIVATED("conduit_activated"),
	REACTOR_ACTIVATED("reactor_activated");

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
