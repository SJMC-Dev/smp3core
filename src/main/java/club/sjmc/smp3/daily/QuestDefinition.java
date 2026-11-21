package club.sjmc.smp3.daily;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;

import club.sjmc.smp3.item.ItemDefinitions;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;

public record QuestDefinition(String id, String name, String description, String category, String difficulty,
                              JsonElement icon, List<Goal> objectives, String completion, List<Tier> tiers,
                              List<String> requires, QuestRewardSpec rewards, int weight) {
	public static final Pattern DESCRIPTION_FIELD = Pattern.compile("\\{([^{}]*)}");
	public static final int ICON_CYCLE_TICKS = 20;
	public static final int MAX_ICONS = 8;

	public QuestDefinition {
		tiers = tiers == null ? List.of() : List.copyOf(tiers);
		requires = requires == null ? List.of() : List.copyOf(requires);
		objectives = List.copyOf(objectives);
		completion = completion == null ? "all" : completion;
	}

	public List<JsonObject> icons() { return normalize(icon); }

	public List<ItemStack> iconStacks(RegistryAccess registries) {
		return icons().stream().map(icon -> ItemDefinitions.stack(icon, registries).copyWithCount(1)).toList();
	}

	static List<JsonObject> normalize(JsonElement icon) {
		if (icon == null || icon.isJsonNull())
			throw new IllegalArgumentException("icon must be an item id, item object, or array");
		var icons = new ArrayList<JsonObject>();
		if (icon.isJsonArray()) {
			for (var element : icon.getAsJsonArray())
				addIcon(icons, element);
		} else {
			addIcon(icons, icon);
		}
		if (icons.isEmpty() || icons.size() > MAX_ICONS)
			throw new IllegalArgumentException("icon must contain 1 to " + MAX_ICONS + " items");
		return List.copyOf(icons);
	}

	private static void addIcon(List<JsonObject> icons, JsonElement element) {
		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
			var item = new JsonObject();
			item.add("id", element);
			icons.add(item);
			return;
		}
		if (!element.isJsonObject())
			throw new IllegalArgumentException("icon must be an item id or an item object");
		icons.add(element.getAsJsonObject());
	}

	public record Tier(int tier, JsonElement count, QuestRewardSpec rewards) {}
	public record Candidate(String id, String name, String description, JsonElement icon, JsonObject conditions,
	                        QuestRewardSpec rewards) {}
	public record Target(String field, JsonElement values, List<Candidate> candidates) {
		public Target { candidates = candidates == null ? List.of() : List.copyOf(candidates); }
		public Target(String field, JsonElement values) { this(field, values, List.of()); }
	}

	public record
	    Goal(String name, String event, Target target, JsonObject conditions,
		     @SerializedName("count") JsonElement quantity, List<Tier> tiers, String countBy, String selection) {
		public Goal {
			tiers = tiers == null ? List.of() : List.copyOf(tiers);
			conditions = conditions == null ? new JsonObject() : conditions.deepCopy();
			countBy = countBy == null ? "amount" : countBy;
			selection = selection == null ? "any" : selection;
		}
		public Objective objective() {
			var filters = conditions.deepCopy();
			if (target != null)
				filters.add(target.field, target.values);
			return new Objective(event, filters);
		}
		boolean hasCondition(String field) {
			return conditions.has(field) || target != null && target.field.equals(field);
		}
		boolean supports(int tier) { return tiers.isEmpty() || tiers.stream().anyMatch(t -> t.tier == tier); }
		JsonElement target(int tier) {
			return tiers.isEmpty() ? (quantity == null ? new JsonPrimitive(1) : quantity)
			                       : tiers.stream().filter(t -> t.tier == tier).findFirst().orElseThrow().count;
		}
		public int count() { return quantity == null ? 1 : quantity.getAsInt(); }
		int maximum(int tier) {
			var count = target(tier);
			return count.isJsonObject() ? count.getAsJsonObject().get("max").getAsInt() : count.getAsInt();
		}
		Goal resolve(int tier, RandomGenerator random) {
			var count = target(tier);
			int quantity = count.isJsonObject()
			                   ? (int)random.nextLong(count.getAsJsonObject().get("min").getAsInt(),
			                                          (long)count.getAsJsonObject().get("max").getAsInt() + 1)
			                   : count.getAsInt();
			Target chosen = target;
			var filters = conditions.deepCopy();
			if (target != null && !target.candidates.isEmpty()) {
				var candidate = target.candidates.get(random.nextInt(target.candidates.size()));
				var ids = new JsonArray();
				ids.add(candidate.id);
				chosen = new Target(target.field, ids, List.of(candidate));
				if (candidate.conditions != null)
					candidate.conditions.entrySet().forEach(e -> filters.add(e.getKey(), e.getValue()));
			} else if (selection.equals("random")) {
				var candidates =
				    new ArrayList<>(values(target.values)
					                    .stream()
					                    .map(id -> countBy.equals("distinct") ? identity(target.field, id) : id)
					                    .distinct()
					                    .toList());
				var selected = new JsonArray();
				int size = countBy.equals("distinct") ? quantity : 1;
				for (int i = 0; i < size; i++)
					selected.add(candidates.remove(random.nextInt(candidates.size())));
				if (countBy.equals("distinct")) {
					var identities = values(selected);
					selected = new JsonArray();
					for (var id : values(target.values))
						if (identities.contains(identity(target.field, id)))
							selected.add(id);
				}
				chosen = new Target(target.field, selected);
			}
			return new Goal(name, event, chosen, filters, new JsonPrimitive(quantity), List.of(), countBy, selection);
		}
		public Set<String> identities(Event action) {
			if (target == null)
				return Set.of();
			var accepted = values(target.values);
			var found = new HashSet<String>();
			for (String id : action.facts.getOrDefault(target.field, Set.of())) {
				if (id.startsWith("#") || !accepted.contains(id))
					continue;
				found.add(identity(target.field, id));
			}
			return found;
		}
	}

	QuestDefinition resolve(int tier, RandomGenerator random) {
		QuestRewardSpec selected = rewards;
		if (!tiers.isEmpty()) {
			var band = tiers.stream().filter(t -> t.tier == tier).findFirst().orElseThrow();
			if (band.rewards != null)
				selected = band.rewards;
		}
		var goals = objectives.stream().map(g -> g.resolve(tier, random)).toList();
		String resolvedName = name, resolvedDescription = description;
		JsonElement resolvedIcon = icon;
		if (goals.size() == 1 && goals.getFirst().target != null && !goals.getFirst().target.candidates.isEmpty()) {
			var candidate = goals.getFirst().target.candidates.getFirst();
			if (candidate.name != null)
				resolvedName = candidate.name;
			if (candidate.description != null)
				resolvedDescription = candidate.description;
			if (candidate.icon != null)
				resolvedIcon = candidate.icon;
			if (candidate.rewards != null)
				selected = candidate.rewards;
		}
		return new QuestDefinition(id, resolvedName, resolvedDescription, category, difficulty, resolvedIcon, goals,
		                           completion, List.of(), requires, selected, weight);
	}

	public boolean tiered() { return !tiers.isEmpty() || objectives.stream().anyMatch(g -> !g.tiers.isEmpty()); }
	boolean supports(int tier) {
		return (tiers.isEmpty() || tiers.stream().anyMatch(t -> t.tier == tier)) &&
		    objectives.stream().allMatch(g -> g.supports(tier));
	}

	public static String identity(String field, String id) {
		if (!field.equals("block") || !id.startsWith("minecraft:"))
			return id;
		String normalized = id.replace("_wall_coral_fan", "_coral_fan");
		for (String suffix : List.of("_coral_fan", "_coral_block")) {
			if (normalized.endsWith(suffix))
				return normalized.substring(0, normalized.length() - suffix.length()) + "_coral";
		}
		return normalized;
	}

	public record Event(String type, Map<String, Set<String>> facts, int amount) {
		public Event(String type, Map<String, Set<String>> facts) { this(type, facts, 1); }
	}

	public static final class Objective {
		private final String event;
		private final JsonObject conditions;
		private final Map<String, Set<String>> filters = new HashMap<>();
		public Objective(String event, JsonObject conditions) {
			this.event = event;
			this.conditions = conditions.deepCopy();
			conditions.entrySet().forEach(condition -> {
				if (condition.getKey().equals("block_state"))
					condition.getValue().getAsJsonObject().entrySet().forEach(
					    property
					    -> filters.put("state." + property.getKey(), Set.of(property.getValue().getAsString())));
				else
					filters.put(condition.getKey(), Set.copyOf(values(condition.getValue())));
			});
		}
		public String event() { return event; }
		public JsonObject conditions() { return conditions.deepCopy(); }
		public boolean matches(Event action) {
			if (!event.equals(action.type()))
				return false;
			for (var filter : filters.entrySet()) {
				var actual = action.facts().getOrDefault(filter.getKey(), Set.of());
				if (Collections.disjoint(filter.getValue(), actual))
					return false;
			}
			return true;
		}
	}

	public static List<String> values(JsonElement value) {
		return value.isJsonArray() ? value.getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList()
		                           : List.of(value.getAsString());
	}
}
