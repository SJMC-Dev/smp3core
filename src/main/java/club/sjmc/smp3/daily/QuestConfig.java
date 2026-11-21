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
import java.util.TreeSet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import club.sjmc.smp3.item.ItemDefinitions;
import club.sjmc.smp3.item.RewardBundle;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
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
		shape(root, "配置", "dailyRerollLimit progression milestones rewards tasks", "rewardPools");
		var pools = root.has("rewardPools") ? root.getAsJsonObject("rewardPools") : new JsonObject();
		expandPools(root.get("tasks"), pools, new HashSet<>());
		root.remove("rewardPools");
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
			integer(reward.getValue().getAsJsonObject().get("tongbao"), "rewards." + reward.getKey() + ".tongbao");
			integer(reward.getValue().getAsJsonObject().get("coffeeStamp"),
			        "rewards." + reward.getKey() + ".coffeeStamp");
		}
		for (var element : root.getAsJsonArray("tasks")) {
			var task = element.getAsJsonObject();
			String at = "任务 " + task.get("id");
			shape(task, at, "id name category difficulty icon objectives",
			      "description requires rewards completion tiers weight");
			if (task.has("weight"))
				require(integer(task.get("weight"), at + ".weight") >= 1, at + ".weight must be at least 1");
			else
				task.addProperty("weight", 1);
			require(!task.getAsJsonArray("objectives").isEmpty(), at + ".objectives is empty");
			for (int i = 0; i < task.getAsJsonArray("objectives").size(); i++) {
				var goal = task.getAsJsonArray("objectives").get(i).getAsJsonObject();
				String where = at + ".objectives[" + i + "]";
				shape(goal, where, "event", "name target conditions count tiers countBy selection");
				if (!goal.has("name"))
					goal.addProperty("name", task.get("name").getAsString());
				if (!goal.has("conditions"))
					goal.add("conditions", new JsonObject());
				String countBy = goal.has("countBy") ? goal.get("countBy").getAsString() : "amount";
				String selection = goal.has("selection") ? goal.get("selection").getAsString() : "any";
				require(Set.of("amount", "distinct").contains(countBy), where + ": countBy must be amount/distinct");
				require(Set.of("any", "random").contains(selection), where + ": selection must be any/random");
				require(goal.has("target") || countBy.equals("amount") && selection.equals("any"),
				        where + ": target required");
				var filters = new JsonObject();
				filters.add("event", goal.get("event"));
				filters.add("conditions", goal.getAsJsonObject("conditions").deepCopy());
				if (goal.has("target")) {
					var target = goal.getAsJsonObject("target");
					shape(target, where + ".target", "field values", "");
					String field = target.get("field").getAsString();
					require(!field.equals("block_state") && !goal.getAsJsonObject("conditions").has(field),
					        where + ": duplicate or invalid target field");
					if (target.get("values").isJsonArray() &&
					    target.getAsJsonArray("values").asList().stream().anyMatch(JsonElement::isJsonObject)) {
						require(selection.equals("random") && countBy.equals("amount") &&
						            task.getAsJsonArray("objectives").size() == 1,
						        where + ": detailed candidates require a single random amount objective");
						var candidates = target.getAsJsonArray("values");
						var ids = new JsonArray();
						for (var entry : candidates) {
							var candidate = entry.getAsJsonObject();
							shape(candidate, where + ".target.values", "id",
							      "name description icon conditions rewards");
							candidate.addProperty("id", Identifier.parse(candidate.get("id").getAsString()).toString());
							ids.add(candidate.get("id"));
							if (candidate.has("rewards"))
								reward(candidate.getAsJsonObject("rewards"), where + ".candidate.rewards", true);
							var additional = new JsonObject();
							additional.add("event", goal.get("event"));
							additional.add("conditions", candidate.has("conditions") ? candidate.get("conditions")
							                                                         : new JsonObject());
							objective(additional, where + ".candidate");
						}
						target.add("candidates", candidates);
						target.add("values", ids);
					}
					filters.getAsJsonObject("conditions").add(field, target.get("values"));
					objective(filters, where);
					target.add("values", filters.getAsJsonObject("conditions").remove(field));
				} else
					objective(filters, where);
				goal.add("conditions", filters.get("conditions"));
				counts(goal, where, false, false);
			}
			counts(task, at, true, true);
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
				validateReward(tier.rewards(), task.id() + ".tiers[" + tier.tier() + "].rewards");
			}
			validateReward(task.rewards(), task.id() + ".rewards");
			validateDescription(task.description(),
			                    task.objectives().size() == 1 ? task.objectives().getFirst().objective().conditions()
			                                                  : null,
			                    task.id());
			for (var goal : task.objectives()) {
				if (goal.target() != null)
					for (var candidate : goal.target().candidates()) {
						validateReward(candidate.rewards(), task.id() + ".candidate.rewards");
						var filters = goal.objective().conditions();
						if (candidate.conditions() != null)
							candidate.conditions().entrySet().forEach(e -> filters.add(e.getKey(), e.getValue()));
						validateDescription(candidate.description() == null ? task.description()
						                                                    : candidate.description(),
						                    filters, task.id());
					}
				var defined = new HashSet<Integer>();
				for (var tier : goal.tiers())
					require(defined.add(tier.tier()), task.id() + ": duplicate objective tier");
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

	private static void validateDescription(String description, JsonObject conditions, String at) {
		if (description == null)
			return;
		var placeholders = QuestDefinition.DESCRIPTION_FIELD.matcher(description);
		while (placeholders.find()) {
			String field = placeholders.group(1);
			require(conditions != null &&
			            (field.equals("count") || conditions.has(field) && !field.equals("block_state")),
			        at + ": invalid description placeholder " + field);
		}
	}

	private void validateReward(QuestRewardSpec reward, String at) {
		if (reward == null)
			return;
		for (var branch : reward.when()) {
			require(!branch.requires().isEmpty() && milestones.keySet().containsAll(branch.requires()),
			        at + ": invalid milestone references");
			validateReward(branch.rewards(), at + ".when.rewards");
		}
	}

	public void validateRegistries(RegistryAccess registries, Set<String> dimensions) {
		for (QuestDefinition task : tasks) {
			try {
				for (var icon : task.icons())
					ItemDefinitions.stack(icon, registries);
				for (int i = 0; i < task.objectives().size(); i++) {
					var goal = task.objectives().get(i);
					if (goal.target() != null)
						for (var candidate : goal.target().candidates())
							if (candidate.icon() != null)
								for (var icon : QuestDefinition.normalize(candidate.icon()))
									ItemDefinitions.stack(icon, registries);
					validateObjective(goal.objective(), registries, dimensions);
					if (goal.target() != null) {
						var expanded = new TreeSet<String>();
						for (String value : QuestDefinition.values(goal.target().values())) {
							if (!value.startsWith("#"))
								expanded.add(value);
							else
								QuestField.find(goal.target().field())
								    .registry(registries)
								    .getTags()
								    .filter(t -> t.key().location().equals(Identifier.parse(value.substring(1))))
								    .forEach(
								        t
								        -> t.forEach(
								            h -> expanded.add(h.unwrapKey().orElseThrow().identifier().toString())));
						}
						if (goal.target().field().equals("block")) {
							for (String id : List.copyOf(expanded)) {
								if (id.startsWith("minecraft:") && id.endsWith("_coral_fan") && !id.contains("_wall_"))
									expanded.add(id.replace("_coral_fan", "_wall_coral_fan"));
							}
						}
						var values = goal.target().values().getAsJsonArray();
						values.asList().clear();
						expanded.forEach(values::add);
						if (goal.countBy().equals("distinct")) {
							long size = expanded.stream()
							                .map(id -> QuestDefinition.identity(goal.target().field(), id))
							                .distinct()
							                .count();
							for (var band : progression.bands)
								if (task.supports(band.tier))
									require(size >= goal.maximum(band.tier),
									        "objectives[" + i + "]: insufficient distinct candidates");
						}
					}
				}
				validateItems(task.rewards(), registries, "rewards");
				for (var tier : task.tiers())
					validateItems(tier.rewards(), registries, "tiers[" + tier.tier() + "].rewards");
			} catch (RuntimeException e) {
				throw new IllegalArgumentException("任务 " + task.id() + ": " + e.getMessage(), e);
			}
		}
	}

	public void validateLoot(MinecraftServer server) { validateLoot(JSON.toJsonTree(tasks), server); }
	private static void validateLoot(JsonElement value, MinecraftServer server) {
		if (value.isJsonArray()) {
			for (var child : value.getAsJsonArray())
				validateLoot(child, server);
		} else if (value.isJsonObject()) {
			var object = value.getAsJsonObject();
			if (object.has("type") && object.get("type").getAsString().equals("loot_table"))
				server.reloadableRegistries()
				    .lookup()
				    .lookupOrThrow(Registries.LOOT_TABLE)
				    .getOrThrow(
				        ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(object.get("id").getAsString())));
			for (var entry : object.entrySet())
				validateLoot(entry.getValue(), server);
		}
	}

	private static void validateObjective(QuestDefinition.Objective objective, RegistryAccess registries,
	                                      Set<String> dimensions) {
		for (var condition : objective.conditions().entrySet()) {
			String key = condition.getKey();
			if (key.equals("block_state") || literalValues(key) != null)
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
			RewardBundle.validate(reward.grants(), registries);
			for (var branch : reward.when())
				validateItems(branch.rewards(), registries, "when.rewards");
		} catch (RuntimeException e) {
			throw new IllegalArgumentException(at + ": " + e.getMessage(), e);
		}
	}

	private static void expandPools(JsonElement value, JsonObject pools, Set<String> visiting) {
		if (value.isJsonArray()) {
			for (var child : value.getAsJsonArray())
				expandPools(child, pools, visiting);
		} else if (value.isJsonObject()) {
			var object = value.getAsJsonObject();
			if (object.has("pool")) {
				String name = object.remove("pool").getAsString();
				require(pools.has(name) && visiting.add(name), "Unknown or cyclic reward pool: " + name);
				var options = pools.getAsJsonArray(name).deepCopy();
				expandPools(options, pools, visiting);
				visiting.remove(name);
				require(!object.has("options"), "pool and options are mutually exclusive");
				object.add("options", options);
			}
			for (var child : object.entrySet())
				expandPools(child.getValue(), pools, visiting);
		}
	}

	private static void count(JsonElement value, String at) {
		if (!value.isJsonObject()) {
			require(integer(value, at) > 0, at + ": count must be positive");
			return;
		}
		var range = value.getAsJsonObject();
		shape(range, at, "min max", "");
		int min = integer(range.get("min"), at + ".min");
		int max = integer(range.get("max"), at + ".max");
		require(min > 0 && max >= min, at + ": expected 0 < min <= max");
	}

	private static void counts(JsonObject object, String at, boolean multi, boolean rewards) {
		require(!(object.has("count") && object.has("tiers")), at + ": count and tiers are mutually exclusive");
		if (object.has("count"))
			count(object.get("count"), at + ".count");
		if (!object.has("tiers"))
			return;
		require(!object.getAsJsonArray("tiers").isEmpty(), at + ": tiers is empty");
		for (var value : object.getAsJsonArray("tiers")) {
			var tier = value.getAsJsonObject();
			shape(tier, at + ".tiers", multi ? "tier" : "tier count", rewards ? "rewards" : "");
			require(integer(tier.get("tier"), at + ".tiers.tier") > 0, at + ": tier must be positive");
			if (!multi)
				count(tier.get("count"), at + ".tiers.count");
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
				if (literalValues(key) != null)
					require(literalValues(key).contains(id), at + ": unknown " + key + " " + id);
				else
					id = id.startsWith("#") ? "#" + Identifier.parse(id.substring(1)) : Identifier.parse(id).toString();
				normalized.add(id);
			}
			condition.setValue(normalized);
		}
	}

	private static Set<String> literalValues(String key) {
		return switch (key) {
			case "source" -> Set.of("crafting", "smelting", "brewing", "smithing", "stonecutting", "loom");
			case "action" -> Set.of("talk", "repair", "brush");
			case "movement" -> Set.of("sprint", "swim", "elytra", "crawl");
			case "variant" -> Set.of("red", "brown");
			case "loot" -> Set.of("fish", "junk", "treasure");
			default -> null;
		};
	}

	private static void reward(JsonObject reward, String at, boolean branches) {
		shape(reward, at, "", branches ? "base grants when" : "base grants");
		if (reward.has("base"))
			require(reward.get("base").isJsonPrimitive() && reward.get("base").getAsJsonPrimitive().isBoolean(),
			        at + ": base must be boolean");
		if (reward.has("grants"))
			require(reward.get("grants").isJsonArray(), at + ": grants must be an array");
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
		for (String field : List.of("id", "name", "category", "difficulty", "description", "completion")) {
			if (!object.has(field))
				continue;
			var value = object.get(field);
			require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && !value.getAsString().isBlank(),
			        at + "." + field + ": must be a nonempty string");
		}
		if (object.has("icon"))
			QuestDefinition.normalize(object.get("icon"));
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
