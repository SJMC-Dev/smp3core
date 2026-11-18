package club.sjmc.smp3.daily;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import club.sjmc.smp3.item.ItemDefinitions;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

public record QuestConfig(int dailyRerollLimit, Progression progression, Map<String, String> milestones,
                          Map<String, QuestReward> rewards, List<QuestDefinition> tasks) {
	public QuestConfig {
		milestones = Map.copyOf(milestones);
		rewards = Map.copyOf(rewards);
		tasks = List.copyOf(tasks);
	}

	static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
	public static final List<String> TIERS = List.of("A", "B", "C", "C");

	public record Band(double minScore, int tier) {}
	public record Progression(double questWeight, double timeWeight, long questCap, double hourCap, List<Band> bands) {
		public Progression { bands = List.copyOf(bands); }
		public double score(long completed, double hours) {
			return (questWeight * Math.min(completed / (double)questCap, 1) +
			        timeWeight * Math.min(hours / hourCap, 1)) /
			    (questWeight + timeWeight) * 100;
		}
		public int tier(double score) {
			int tier = bands.getFirst().tier;
			for (Band band : bands)
				if (score >= band.minScore)
					tier = band.tier;
			return tier;
		}
	}

	public static QuestConfig load(Path path, RegistryAccess registries, Set<String> dimensions) throws IOException {
		if (!Files.exists(path)) {
			Files.createDirectories(path.getParent());
			try (InputStream defaults = QuestConfig.class.getResourceAsStream("/dailyquests.json")) {
				if (defaults == null)
					throw new IOException("缺少默认任务配置");
				Files.copy(defaults, path);
			}
		}
		try {
			QuestConfig config = parse(Files.readString(path));
			config.validateRegistries(registries, dimensions);
			return config;
		} catch (RuntimeException e) {
			throw new IllegalArgumentException(path + ": " + e.getMessage(), e);
		}
	}

	public static QuestConfig parse(String text) {
		JsonObject root = JsonParser.parseString(text).getAsJsonObject();
		fields(root, "配置", "dailyRerollLimit", "progression", "milestones", "rewards", "tasks");
		integer(root.get("dailyRerollLimit"), "dailyRerollLimit");
		var progression = root.getAsJsonObject("progression");
		fields(progression, "progression", "questWeight", "timeWeight", "questCap", "hourCap", "bands");
		integer(progression.get("questCap"), "progression.questCap");
		for (var entry : progression.getAsJsonArray("bands")) {
			var band = entry.getAsJsonObject();
			fields(band, "progression.bands", "minScore", "tier");
			integer(band.get("tier"), "progression.bands.tier");
		}
		root.getAsJsonObject("milestones").entrySet().forEach(e -> {
			try {
				Identifier.parse(e.getValue().getAsString());
			} catch (RuntimeException error) {
				throw new IllegalArgumentException("milestones." + e.getKey() + ": invalid advancement ID", error);
			}
		});
		fields(root.getAsJsonObject("rewards"), "奖励", "A", "B", "C");
		for (var reward : root.getAsJsonObject("rewards").entrySet()) {
			fields(reward.getValue().getAsJsonObject(), "奖励." + reward.getKey(), "tongbao", "coffeeStamp");
		}
		for (var element : root.getAsJsonArray("tasks")) {
			var task = element.getAsJsonObject();
			String at = "任务 " + task.get("id");
			shape(task, at, "id name category difficulty icon",
			      "description requires rewards objective objectives completion choices count tiers");
			int kinds =
			    (task.has("objective") ? 1 : 0) + (task.has("objectives") ? 1 : 0) + (task.has("choices") ? 1 : 0);
			require(kinds == 1, at + ": specify exactly one of objective, objectives, choices");
			require(!task.has("completion") || task.has("objectives"), at + ": completion requires objectives");
			if (task.has("objective"))
				objective(task.getAsJsonObject("objective"), at + ".objective");
			if (task.has("objectives")) {
				require(!task.getAsJsonArray("objectives").isEmpty(), at + ".objectives is empty");
				require(!task.has("count"), at + ": use each objective's count");
				for (int i = 0; i < task.getAsJsonArray("objectives").size(); i++) {
					var goal = task.getAsJsonArray("objectives").get(i).getAsJsonObject();
					String where = at + ".objectives[" + i + "]";
					shape(goal, where, "name objective", "count tiers");
					require(!goal.get("name").getAsString().isBlank(), where + ": empty name");
					objective(goal.getAsJsonObject("objective"), where + ".objective");
					counts(goal, where, false, false);
				}
			}
			if (task.has("choices")) {
				require(!task.getAsJsonArray("choices").isEmpty(), at + ".choices is empty");
				for (int i = 0; i < task.getAsJsonArray("choices").size(); i++) {
					var choice = task.getAsJsonArray("choices").get(i).getAsJsonObject();
					String where = at + ".choices[" + i + "]";
					shape(choice, where, "name objective", "description icon rewards");
					objective(choice.getAsJsonObject("objective"), where + ".objective");
					if (choice.has("rewards"))
						reward(choice.getAsJsonObject("rewards"), where + ".rewards", true);
				}
			}
			counts(task, at, task.has("objectives"), true);
			if (task.has("rewards"))
				reward(task.getAsJsonObject("rewards"), at + ".rewards", true);
		}
		QuestConfig config = JSON.fromJson(root, QuestConfig.class);
		config.validate();
		return config;
	}

	private void validate() {
		require(dailyRerollLimit >= 0, "dailyRerollLimit 必须非负");
		require(Double.isFinite(progression.questWeight) && Double.isFinite(progression.timeWeight) &&
		            progression.questWeight >= 0 && progression.timeWeight >= 0 &&
		            Double.isFinite(progression.questWeight + progression.timeWeight) &&
		            progression.questWeight + progression.timeWeight > 0,
		        "progression weights must be finite, nonnegative and have a positive sum");
		require(progression.questCap > 0 && Double.isFinite(progression.hourCap) && progression.hourCap > 0,
		        "progression caps must be positive");
		require(!progression.bands.isEmpty() && progression.bands.getFirst().minScore == 0,
		        "progression bands must start at zero");
		double previous = -1;
		for (Band band : progression.bands) {
			require(Double.isFinite(band.minScore) && band.minScore > previous && band.minScore <= 100 && band.tier > 0,
			        "Invalid progression band");
			previous = band.minScore;
		}
		for (var reward : rewards.values())
			require(reward.tongbao() >= 0 && reward.coffeeStamp() >= 0, "奖励不能为负数");
		Set<String> ids = new HashSet<>();
		for (QuestDefinition task : tasks) {
			require(task.id() != null && task.id().matches("[a-z0-9_./-]+") && ids.add(task.id()),
			        "无效或重复任务 ID: " + task.id());
			require(milestones.keySet().containsAll(task.requires()),
			        task.id() + ".requires: unknown milestone reference " + task.requires());
			require(rewards.containsKey(task.difficulty()), task.id() + ": 难度必须为 A/B/C");
			require(Set.of("all", "any").contains(task.completion()), task.id() + ": completion must be all/any");
			Set<Integer> tiers = new HashSet<>();
			for (var tier : task.tiers()) {
				require(tiers.add(tier.tier()), task.id() + ": duplicate tier");
				validateReward(tier.rewards(), task.id() + ".tiers[" + tier.tier() + "].rewards",
				               rewards.get(task.difficulty()));
			}
			validateReward(task.rewards(), task.id() + ".rewards", rewards.get(task.difficulty()));
			List<QuestDefinition> variants =
			    task.choices().isEmpty() ? List.of(task) : task.choices().stream().map(task::select).toList();
			for (var variant : variants) {
				validateReward(variant.rewards(), task.id() + ".choices.rewards", rewards.get(task.difficulty()));
				if (variant.description() != null) {
					var placeholders = QuestDefinition.DESCRIPTION_FIELD.matcher(variant.description());
					while (placeholders.find()) {
						String field = placeholders.group(1);
						require(variant.objectives().isEmpty() &&
						            (field.equals("count") ||
						             variant.objective().conditions().has(field) && !field.equals("block_state")),
						        task.id() + ": invalid description placeholder " + field);
					}
				}
				for (var goal : variant.objectives()) {
					var defined = new HashSet<Integer>();
					for (var tier : goal.tiers())
						require(defined.add(tier.tier()), task.id() + ": duplicate objective tier");
					for (var band : progression.bands)
						if (task.supports(band.tier))
							require(goal.tiers().isEmpty() || defined.contains(band.tier),
							        task.id() + ": objective missing tier " + band.tier);
				}
			}
		}
		for (var band : progression.bands) {
			for (String difficulty : List.of("A", "B", "C")) {
				long minimum = (difficulty.equals("C") ? 2 : 1) + (dailyRerollLimit > 0 ? 1 : 0);
				require(tasks.stream()
				                .filter(t
				                        -> t.difficulty().equals(difficulty) && t.supports(band.tier) &&
				                               t.requires().isEmpty())
				                .count() >= minimum,
				        "tier " + band.tier + " / " + difficulty + ": needs " + minimum + " ungated tasks");
			}
		}
	}

	private void validateReward(QuestRewardSpec reward, String at, QuestReward category) {
		if (reward == null)
			return;
		for (var branch : reward.when()) {
			require(!branch.requires().isEmpty() && milestones.keySet().containsAll(branch.requires()),
			        at + ": invalid milestone references");
			validateReward(branch.rewards(), at + ".when.rewards", category);
		}
		Math.addExact(Boolean.FALSE.equals(reward.base()) ? 0 : category.tongbao(), reward.tongbao());
		Math.addExact(Boolean.FALSE.equals(reward.base()) ? 0 : category.coffeeStamp(), reward.coffeeStamp());
	}

	public void validateRegistries(RegistryAccess registries, Set<String> dimensions) {
		for (QuestDefinition task : tasks) {
			try {
				List<QuestDefinition> variants =
				    task.choices().isEmpty() ? List.of(task) : task.choices().stream().map(task::select).toList();
				for (var variant : variants) {
					require(registries.lookupOrThrow(Registries.ITEM).containsKey(Identifier.parse(variant.icon())),
					        "icon does not exist: " + variant.icon());
					for (int i = 0; i < variant.goals().size(); i++) {
						try {
							validateObjective(variant.goals().get(i).objective(), registries, dimensions);
						} catch (RuntimeException e) {
							throw new IllegalArgumentException("objectives[" + i + "]: " + e.getMessage(), e);
						}
					}
					validateItems(variant.rewards(), registries, "rewards/choices");
				}
				validateItems(task.rewards(), registries, "rewards");
				for (var tier : task.tiers())
					validateItems(tier.rewards(), registries, "tiers[" + tier.tier() + "].rewards");
			} catch (RuntimeException e) {
				throw new IllegalArgumentException("任务 " + task.id() + ": " + e.getMessage(), e);
			}
		}
	}

	private static void validateObjective(QuestDefinition.Objective objective, RegistryAccess registries,
	                                      Set<String> dimensions) {
		for (var condition : objective.conditions().entrySet()) {
			String key = condition.getKey();
			if (key.equals("block_state") || key.equals("source"))
				continue;
			for (String value : QuestDefinition.values(condition.getValue())) {
				if (key.equals("dimension")) {
					require(dimensions.contains(value), "dimension does not exist: " + value);
				} else {
					Registry<?> registry = QuestField.find(key).registry(registries);
					Identifier id = Identifier.parse(value.startsWith("#") ? value.substring(1) : value);
					require(value.startsWith("#")
					            ? registry.getTags().anyMatch(t -> t.key().location().equals(id) && t.size() > 0)
								: registry.containsKey(id),
					        key + " missing or empty tag: " + value);
				}
			}
		}
		validateStates(objective, registries.lookupOrThrow(Registries.BLOCK));
	}

	private static void validateItems(QuestRewardSpec reward, RegistryAccess registries, String at) {
		if (reward == null)
			return;
		try {
			for (int i = 0; i < reward.items().size(); i++) {
				try {
					ItemDefinitions.validate(reward.items().get(i), registries);
				} catch (RuntimeException e) {
					throw new IllegalArgumentException("items[" + i + "]: " + e.getMessage(), e);
				}
			}
			for (var draw : reward.random()) {
				var identities = new HashSet<JsonObject>();
				for (var entry : draw.entries()) {
					ItemDefinitions.validate(entry.item(), registries);
					var identity = ItemDefinitions.json(
					    ItemDefinitions.stack(entry.item(), registries).copyWithCount(1), registries);
					require(!draw.unique() || identities.add(identity), "random.entries: duplicate unique item");
				}
			}
			for (var branch : reward.when())
				validateItems(branch.rewards(), registries, "when.rewards");
		} catch (RuntimeException e) {
			throw new IllegalArgumentException(at + ": " + e.getMessage(), e);
		}
	}

	private static void counts(JsonObject object, String at, boolean multi, boolean rewards) {
		require(!(object.has("count") && object.has("tiers")), at + ": count and tiers are mutually exclusive");
		if (object.has("count"))
			require(integer(object.get("count"), at + ".count") > 0, at + ": count must be positive");
		if (!object.has("tiers"))
			return;
		require(!object.getAsJsonArray("tiers").isEmpty(), at + ": tiers is empty");
		for (var value : object.getAsJsonArray("tiers")) {
			var tier = value.getAsJsonObject();
			shape(tier, at + ".tiers", multi ? "tier" : "tier count", rewards ? "rewards" : "");
			require(integer(tier.get("tier"), at + ".tiers.tier") > 0, at + ": tier must be positive");
			if (!multi)
				require(integer(tier.get("count"), at + ".tiers.count") > 0, at + ": count must be positive");
			if (tier.has("rewards"))
				reward(tier.getAsJsonObject("rewards"), at + ".tiers.rewards", true);
		}
	}

	private static void objective(JsonObject objective, String at) {
		fields(objective, at, "event", "conditions");
		var event = QuestEventType.find(objective.get("event").getAsString());
		require(event != null, at + ": unknown event");
		for (var condition : objective.getAsJsonObject("conditions").entrySet()) {
			String key = condition.getKey();
			require(event.allows(key), at + ": unsupported condition " + key);
			if (key.equals("block_state")) {
				for (var property : condition.getValue().getAsJsonObject().entrySet())
					require(property.getValue().isJsonPrimitive() &&
					            property.getValue().getAsJsonPrimitive().isString(),
					        at + ": block state must be a string");
				continue;
			}
			var values = condition.getValue().isJsonArray() ? condition.getValue().getAsJsonArray().asList()
			                                                : List.of(condition.getValue());
			require(!values.isEmpty(), at + ": empty condition");
			var normalized = new JsonArray();
			for (var value : values) {
				require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(),
				        at + ": condition must be string(s)");
				String id = value.getAsString();
				if (key.equals("source"))
					require(Set.of("crafting", "smelting", "brewing", "smithing", "stonecutting", "loom").contains(id),
					        at + ": unknown source " + id);
				else
					id = id.startsWith("#") ? "#" + Identifier.parse(id.substring(1)) : Identifier.parse(id).toString();
				normalized.add(id);
			}
			condition.setValue(normalized);
		}
	}

	private static void reward(JsonObject reward, String at, boolean branches) {
		shape(reward, at, "",
		      branches ? "base tongbao coffeeStamp items random when" : "base tongbao coffeeStamp items random");
		if (reward.has("base"))
			require(reward.get("base").isJsonPrimitive() && reward.get("base").getAsJsonPrimitive().isBoolean(),
			        at + ": base must be boolean");
		for (String field : List.of("tongbao", "coffeeStamp"))
			if (reward.has(field))
				require(integer(reward.get(field), at + "." + field) >= 0, at + ": negative currency");
		if (reward.has("random"))
			for (var value : reward.getAsJsonArray("random")) {
				var draw = value.getAsJsonObject();
				shape(draw, at + ".random", "entries", "draws unique");
				if (!draw.has("draws"))
					draw.addProperty("draws", 1);
				int draws = integer(draw.get("draws"), at + ".random.draws");
				if (draw.has("unique"))
					require(draw.get("unique").isJsonPrimitive() && draw.get("unique").getAsJsonPrimitive().isBoolean(),
					        at + ": unique must be boolean");
				boolean unique = draw.has("unique") && draw.get("unique").getAsBoolean();
				var entries = draw.getAsJsonArray("entries");
				require(draws > 0 && !entries.isEmpty() && (!unique || draws <= entries.size()),
				        at + ": insufficient random candidates or invalid draws");
				var identities = new HashSet<JsonObject>();
				for (var candidate : entries) {
					var entry = candidate.getAsJsonObject();
					shape(entry, at + ".random.entries", "item", "weight");
					if (!entry.has("weight"))
						entry.addProperty("weight", 1);
					require(integer(entry.get("weight"), at + ".weight") > 0, at + ": weight must be positive");
					var identity = entry.getAsJsonObject("item").deepCopy();
					identity.remove("count");
					identity.addProperty("id", Identifier.parse(identity.get("id").getAsString()).toString());
					require(!unique || identities.add(identity), at + ": duplicate unique candidate");
				}
			}
		if (reward.has("when"))
			for (var value : reward.getAsJsonArray("when")) {
				var branch = value.getAsJsonObject();
				shape(branch, at + ".when", "requires rewards", "");
				reward(branch.getAsJsonObject("rewards"), at + ".when.rewards", false);
			}
	}

	private static void shape(JsonObject object, String at, String required, String optional) {
		var mandatory = new HashSet<>(Arrays.asList(required.split(" ")));
		mandatory.remove("");
		var allowed = new HashSet<>(mandatory);
		allowed.addAll(Arrays.asList(optional.split(" ")));
		require(object.keySet().containsAll(mandatory) && allowed.containsAll(object.keySet()),
		        at + ": fields required=" + mandatory + ", allowed=" + allowed);
		for (String field : List.of("id", "name", "category", "difficulty", "icon", "description", "completion")) {
			if (!object.has(field))
				continue;
			var value = object.get(field);
			require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && !value.getAsString().isBlank(),
			        at + "." + field + ": must be a nonempty string");
		}
		if (object.has("requires")) {
			require(object.get("requires").isJsonArray(), at + ".requires: must be an array");
			for (var ref : object.getAsJsonArray("requires"))
				require(ref.isJsonPrimitive() && ref.getAsJsonPrimitive().isString(),
				        at + ".requires: must contain milestone names");
		}
	}

	private static void validateStates(QuestDefinition.Objective objective, Registry<Block> blocks) {
		var conditions = objective.conditions();
		if (!conditions.has("block_state"))
			return;
		require(conditions.has("block"), "block_state 必须同时指定 block");
		for (var property : conditions.getAsJsonObject("block_state").entrySet()) {
			boolean supported =
			    blocks.listElements()
			        .filter(holder
					        -> QuestDefinition.values(conditions.get("block"))
					               .stream()
					               .anyMatch(
			                           value
			                           -> value.startsWith("#")
			                                  ? holder.tags().anyMatch(
			                                        tag -> tag.location().equals(Identifier.parse(value.substring(1))))
			                                  : holder.is(Identifier.parse(value))))
			        .anyMatch(holder -> {
				        var field = holder.value().getStateDefinition().getProperty(property.getKey());
				        return field != null && field.getValue(property.getValue().getAsString()).isPresent();
			        });
			require(supported, "无效方块状态: " + property.getKey() + "=" + property.getValue());
		}
	}

	private static int integer(JsonElement value, String at) {
		require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(), at + ": 须为整数");
		try {
			return value.getAsBigDecimal().intValueExact();
		} catch (ArithmeticException e) {
			throw new IllegalArgumentException(at + ": 须为有效整数", e);
		}
	}

	private static void fields(JsonObject object, String at, String... names) {
		Set<String> expected = new HashSet<>(Arrays.asList(names));
		expected.remove(null);
		require(object != null && object.keySet().equals(expected), at + ": 字段必须为 " + expected);
	}

	private static void require(boolean condition, String message) {
		if (!condition)
			throw new IllegalArgumentException(message);
	}
}
