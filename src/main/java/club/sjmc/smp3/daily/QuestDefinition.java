package club.sjmc.smp3.daily;

import java.util.List;
import java.util.random.RandomGenerator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public record QuestDefinition(String id, String name, String description, String category, String difficulty,
                              String icon, Objective objective, Integer count, List<Tier> tiers, List<String> requires,
                              QuestRewardSpec rewards, List<Goal> objectives, String completion, List<Choice> choices) {
	public static final Pattern DESCRIPTION_FIELD = Pattern.compile("\\{([^{}]*)}");

	public QuestDefinition {
		tiers = tiers == null ? List.of() : List.copyOf(tiers);
		requires = requires == null ? List.of() : List.copyOf(requires);
		objectives = objectives == null ? List.of() : List.copyOf(objectives);
		choices = choices == null ? List.of() : List.copyOf(choices);
		completion = completion == null ? "all" : completion;
	}
	public record Tier(int tier, Integer count, QuestRewardSpec rewards) {}

	public record Goal(String name, Objective objective, Integer count, List<Tier> tiers) {
		public Goal { tiers = tiers == null ? List.of() : List.copyOf(tiers); }
		int target(int tier) {
			return tiers.isEmpty() ? (count == null ? 1 : count)
			                       : tiers.stream().filter(t -> t.tier == tier).findFirst().orElseThrow().count;
		}
		Goal resolve(int tier) { return new Goal(name, objective, target(tier), List.of()); }
	}

	public record Choice(String name, String description, String icon, Objective objective, QuestRewardSpec rewards) {}

	public List<Goal> goals() {
		return objectives.isEmpty() ? List.of(new Goal(name, objective, count, tiers)) : objectives;
	}

	QuestDefinition select(Choice choice) {
		return new QuestDefinition(id, choice.name, choice.description == null ? description : choice.description,
		                           category, difficulty, choice.icon == null ? icon : choice.icon, choice.objective,
		                           count, tiers, requires, choice.rewards == null ? rewards : choice.rewards, List.of(),
		                           completion, List.of());
	}

	QuestDefinition resolve(int tier, RandomGenerator random) {
		QuestRewardSpec selected = rewards;
		if (tiered()) {
			var band = tiers.stream().filter(t -> t.tier == tier).findFirst().orElseThrow();
			if (band.rewards != null)
				selected = band.rewards;
		}
		var selectedTask = new QuestDefinition(id, name, description, category, difficulty, icon, objective, count,
		                                       tiers, requires, selected, objectives, completion, List.of());
		if (!choices.isEmpty())
			selectedTask = selectedTask.select(choices.get(random.nextInt(choices.size())));
		return new QuestDefinition(
		    id, selectedTask.name, selectedTask.description, category, difficulty, selectedTask.icon,
		    selectedTask.objective, objectives.isEmpty() ? target(tier) : null, List.of(), requires,
		    selectedTask.rewards, objectives.stream().map(g -> g.resolve(tier)).toList(), completion, List.of());
	}

	public boolean tiered() { return !tiers.isEmpty(); }

	boolean supports(int tier) { return !tiered() || tiers.stream().anyMatch(value -> value.tier == tier); }

	int target(int tier) {
		if (!tiered())
			return count == null ? 1 : count;
		return tiers.stream().filter(value -> value.tier == tier).findFirst().orElseThrow().count;
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
