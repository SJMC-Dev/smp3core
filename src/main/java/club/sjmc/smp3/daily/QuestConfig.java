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

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

public record QuestConfig(int dailyRerollLimit, List<XpBand> xpBands, Map<String, QuestDefinition.Reward> rewards,
                          List<QuestDefinition> tasks) {
	public QuestConfig {
		xpBands = List.copyOf(xpBands);
		rewards = Map.copyOf(rewards);
		tasks = List.copyOf(tasks);
	}

	static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
	public static final List<String> TIERS = List.of("A", "B", "C", "C");

	public record XpBand(int minLevel, int tier) {}

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
		fields(root, "配置", "dailyRerollLimit", "xpBands", "rewards", "tasks");
		integer(root.get("dailyRerollLimit"), "dailyRerollLimit");
		for (var element : root.getAsJsonArray("xpBands")) {
			var band = element.getAsJsonObject();
			fields(band, "经验档位", "minLevel", "tier");
			integer(band.get("minLevel"), "xpBands.minLevel");
			integer(band.get("tier"), "xpBands.tier");
		}
		fields(root.getAsJsonObject("rewards"), "奖励", "A", "B", "C");
		for (var reward : root.getAsJsonObject("rewards").entrySet()) {
			fields(reward.getValue().getAsJsonObject(), "奖励." + reward.getKey(), "tongbao", "coffeeStamp");
		}
		for (var element : root.getAsJsonArray("tasks")) {
			JsonObject task = element.getAsJsonObject();
			String at = "任务 " + task.get("id");
			require(!(task.has("count") && task.has("tiers")), at + ": count 与 tiers 不能同时存在");
			fields(task, at, "id", "name", "category", "difficulty", "icon", "objective",
			       task.has("description") ? "description" : null,
			       task.has("count")   ? "count"
			       : task.has("tiers") ? "tiers"
			                           : null);
			if (task.has("description")) {
				var description = task.get("description");
				require(description.isJsonPrimitive() && description.getAsJsonPrimitive().isString() &&
				            !description.getAsString().isBlank(),
				        at + ": description 须为非空字符串");
			}
			if (task.has("count"))
				integer(task.get("count"), at + ".count");
			if (task.has("tiers")) {
				require(task.get("tiers").isJsonArray() && !task.getAsJsonArray("tiers").isEmpty(),
				        at + ": tiers 不能为空");
				for (var entry : task.getAsJsonArray("tiers")) {
					var tier = entry.getAsJsonObject();
					fields(tier, at + ".tiers", "tier", "count");
					integer(tier.get("tier"), at + ".tiers.tier");
					integer(tier.get("count"), at + ".tiers.count");
				}
			}
			JsonObject objective = task.getAsJsonObject("objective");
			fields(objective, at + ".objective", "event", "conditions");
			String event = objective.get("event").getAsString();
			QuestEventType allowed = QuestEventType.find(event);
			require(allowed != null, at + ": 未知事件 " + event);
			for (var condition : objective.getAsJsonObject("conditions").entrySet()) {
				String key = condition.getKey();
				require(allowed.allows(key), at + ": 不支持条件 " + key);
				if (key.equals("block_state")) {
					condition.getValue().getAsJsonObject().entrySet().forEach(p -> {
						require(p.getValue().isJsonPrimitive() && p.getValue().getAsJsonPrimitive().isString(),
						        at + ": 状态值须为字符串");
					});
				} else {
					var values = condition.getValue().isJsonArray() ? condition.getValue().getAsJsonArray().asList()
					                                                : List.of(condition.getValue());
					require(!values.isEmpty(), at + ": 条件候选不能为空");
					for (var value : values)
						require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(),
						        at + ": 条件须为字符串或字符串数组");
					JsonArray normalized = new JsonArray();
					for (var value : values) {
						String id = value.getAsString();
						normalized.add(id.startsWith("#") ? "#" + Identifier.parse(id.substring(1))
						                                  : Identifier.parse(id).toString());
					}
					condition.setValue(normalized);
				}
			}
		}
		QuestConfig config = JSON.fromJson(root, QuestConfig.class);
		config.validate();
		return config;
	}

	private void validate() {
		require(dailyRerollLimit >= 0, "dailyRerollLimit 必须非负");
		require(!xpBands.isEmpty() && xpBands.getFirst().minLevel == 0, "经验档位必须从 0 开始");
		int previous = -1;
		for (XpBand band : xpBands) {
			require(band.minLevel > previous && band.tier > 0, "经验等级阈值必须递增且 tier 为正数");
			previous = band.minLevel;
		}
		for (var reward : rewards.values())
			require(reward.tongbao() >= 0 && reward.coffeeStamp() >= 0, "奖励不能为负数");
		Set<String> ids = new HashSet<>();
		for (QuestDefinition task : tasks) {
			require(task.id() != null && task.id().matches("[a-z0-9_./-]+") && ids.add(task.id()),
			        "无效或重复任务 ID: " + task.id());
			require(task.name() != null && !task.name().isBlank() && task.category() != null &&
			            !task.category().isBlank(),
			        task.id() + ": 名称和类别不能为空");
			require(rewards.containsKey(task.difficulty()), task.id() + ": 难度必须为 A/B/C");
			require(task.count() == null || task.count() > 0, task.id() + ": count 须为正整数");
			if (task.description() != null) {
				var fields = QuestDefinition.DESCRIPTION_FIELD.matcher(task.description());
				while (fields.find()) {
					String field = fields.group(1);
					require(field.equals("count") ||
					            Set.of("item", "block", "entity", "biome", "dimension").contains(field) &&
					                task.objective().conditions().has(field),
					        task.id() + ": description 占位符无效或缺少条件 " + field);
				}
			}
			Set<Integer> tiers = new HashSet<>();
			for (var tier : task.tiers()) {
				require(tier.tier() > 0 && tier.count() > 0 && tiers.add(tier.tier()),
				        task.id() + ": 档位重复或数量无效");
			}
			for (var band : xpBands) {
				require(task.supports(band.tier), task.id() + ": 缺少档位 " + band.tier);
			}
		}
		for (String tier : List.of("A", "B", "C")) {
			long minimum = (tier.equals("C") ? 2 : 1) + (dailyRerollLimit > 0 ? 1 : 0);
			require(tasks.stream().filter(t -> t.difficulty().equals(tier)).count() >= minimum,
			        tier + " 档任务不足 " + minimum + " 个");
		}
	}

	public void validateRegistries(RegistryAccess registries, Set<String> dimensions) {
		for (QuestDefinition task : tasks) {
			try {
				require(registries.lookupOrThrow(Registries.ITEM).containsKey(Identifier.parse(task.icon())),
				        "图标不存在: " + task.icon());
				for (var condition : task.objective().conditions().entrySet()) {
					String key = condition.getKey();
					if (key.equals("block_state"))
						continue;
					for (String value : QuestDefinition.values(condition.getValue())) {
						if (key.equals("dimension")) {
							require(dimensions.contains(Identifier.parse(value).toString()), "维度不存在: " + value);
						} else {
							Registry<?> registry = QuestField.find(key).registry(registries);
							Identifier id = Identifier.parse(value.startsWith("#") ? value.substring(1) : value);
							require(value.startsWith("#") ? registry.getTags().anyMatch(
							                                    t -> t.key().location().equals(id) && t.size() > 0)
							                              : registry.containsKey(id),
							        key + " 不存在或标签为空: " + value);
						}
					}
				}
				validateStates(task, registries.lookupOrThrow(Registries.BLOCK));
			} catch (RuntimeException e) {
				throw new IllegalArgumentException("任务 " + task.id() + ": " + e.getMessage(), e);
			}
		}
	}

	private static void validateStates(QuestDefinition task, Registry<Block> blocks) {
		var conditions = task.objective().conditions();
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

	public int tier(int level) {
		int result = xpBands.getFirst().tier;
		for (XpBand band : xpBands)
			if (level >= band.minLevel)
				result = band.tier;
		return result;
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
