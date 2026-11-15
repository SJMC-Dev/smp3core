package club.sjmc.smp3.daily;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import club.sjmc.smp3.reward.Reward;

public record
    QuestDefinition(String id, String name, String description, String category, String difficulty, String icon,
	                Objective objective, Integer count, List<Tier> tiers, List<String> requires, Extras rewards) {
	public static final Pattern DESCRIPTION_FIELD = Pattern.compile("\\{([^{}]*)}");

	public QuestDefinition {
		tiers = tiers == null ? List.of() : List.copyOf(tiers);
		requires = requires == null ? List.of() : List.copyOf(requires);
	}
	public record Extras(Boolean base, int tongbao, int coffeeStamp, List<JsonObject> items) {
		public Extras { items = items == null ? List.of() : items.stream().map(JsonObject::deepCopy).toList(); }
	}
	public Reward reward(Reward base) {
		if (rewards == null)
			return base;
		boolean include = !Boolean.FALSE.equals(rewards.base);
		return new Reward(Math.addExact(include ? base.tongbao() : 0, rewards.tongbao),
		                  Math.addExact(include ? base.coffeeStamp() : 0, rewards.coffeeStamp), rewards.items);
	}

	public record Tier(int tier, int count) {}

	public boolean tiered() { return !tiers.isEmpty(); }

	boolean supports(int tier) { return !tiered() || tiers.stream().anyMatch(value -> value.tier == tier); }

	int target(int tier) {
		if (!tiered())
			return count == null ? 1 : count;
		return tiers.stream().filter(value -> value.tier == tier).findFirst().orElseThrow().count;
	}

	QuestDefinition snapshot(int target) {
		return new QuestDefinition(id, name, description, category, difficulty, icon, objective, target, List.of(),
		                           requires, rewards);
	}

	public record Event(String type, Map<String, Set<String>> facts, int amount) {
		public Event(String type, Map<String, Set<String>> facts) { this(type, facts, 1); }
	}

	public record Objective(String event, JsonObject conditions) {
		public Objective { conditions = conditions.deepCopy(); }

		@Override
		public JsonObject conditions() {
			return conditions.deepCopy();
		}

		public boolean matches(Event action) {
			if (!event.equals(action.type()))
				return false;
			for (var condition : conditions.entrySet()) {
				if (condition.getKey().equals("block_state")) {
					for (var property : condition.getValue().getAsJsonObject().entrySet()) {
						if (!action.facts()
						         .getOrDefault("state." + property.getKey(), Set.of())
						         .contains(property.getValue().getAsString()))
							return false;
					}
				} else if (values(condition.getValue())
				               .stream()
				               .noneMatch(
				                   value
				                   -> action.facts().getOrDefault(condition.getKey(), Set.of()).contains(value))) {
					return false;
				}
			}
			return true;
		}
	}

	public static List<String> values(JsonElement value) {
		return value.isJsonArray() ? value.getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList()
		                           : List.of(value.getAsString());
	}
}
