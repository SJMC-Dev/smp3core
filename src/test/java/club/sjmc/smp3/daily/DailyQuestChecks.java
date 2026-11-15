package club.sjmc.smp3.daily;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import club.sjmc.smp3.ArchitectureChecks;
import club.sjmc.smp3.economy.EconomyChecks;
import club.sjmc.smp3.time.DateChecks;
import club.sjmc.smp3.watchdog.hotspot.HotspotChecks;

public final class DailyQuestChecks {
	public static void main(String[] args) throws Exception {
		String text;
		try (var input = DailyQuestChecks.class.getResourceAsStream("/dailyquests.json")) {
			text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		QuestConfig config = QuestConfig.parse(text);
		var template = JsonParser.parseString(text).getAsJsonObject();
		for (int i = 0; i < config.progression().bands().size(); i++) {
			var band = config.progression().bands().get(i);
			assert config.progression().tier(band.minScore()) == band.tier();
			if (i > 0)
				assert config.progression().tier(band.minScore() - 1) == config.progression().bands().get(i - 1).tier();
		}
		reject(text.replace("\"dailyRerollLimit\": 4", "\"dailyRerollLimit\": -1"));
		reject(text.replace("\"entity\":", "\"enttiy\":"));
		reject(text.replace("entity_killed", "kill_zombie"));
		reject(text.replace("\"minScore\": 0", "\"minScore\": 1"));
		var invalid = template.deepCopy();
		invalid.getAsJsonObject("progression")
		    .getAsJsonArray("bands")
		    .get(1)
		    .getAsJsonObject()
		    .addProperty("minScore", 0);
		reject(invalid.toString());
		invalid = template.deepCopy();
		invalid.getAsJsonArray("tasks").asList().removeIf(
		    t -> t.getAsJsonObject().get("difficulty").getAsString().equals("A"));
		reject(invalid.toString());
		invalid = template.deepCopy();
		var invalidTask = invalid.getAsJsonArray("tasks").get(0).getAsJsonObject();
		for (String description : List.of("{missing}", "需要 {item}", "")) {
			invalidTask.addProperty("description", description);
			reject(invalid.toString());
		}
		invalidTask.remove("description");
		assert QuestConfig.parse(invalid.toString()).tasks().getFirst().description() == null;
		invalidTask.getAsJsonArray("tiers").get(0).getAsJsonObject().addProperty("count", 2147483648L);
		reject(invalid.toString());

		var custom = template.deepCopy();
		for (int tier = 4; tier <= 5; tier++) {
			custom.getAsJsonObject("progression")
			    .getAsJsonArray("bands")
			    .add(JsonParser.parseString("{\"minScore\":" + (tier == 4 ? 80 : 100) + ",\"tier\":" + tier + "}"));
			for (var entry : custom.getAsJsonArray("tasks")) {
				var definition = entry.getAsJsonObject();
				if (definition.has("tiers"))
					definition.getAsJsonArray("tiers").add(
					    JsonParser.parseString("{\"tier\":" + tier + ",\"count\":" + (tier * 10) + "}"));
			}
		}
		QuestConfig extended = QuestConfig.parse(custom.toString());
		assert extended.progression().tier(80) == 4 && extended.progression().tier(100) == 5;
		var highTier = new QuestDay.Assignment(extended.tasks().getFirst(), extended.progression().tier(100),
		                                       extended.rewards().get("A"));
		assert highTier.tier() == 5 && highTier.target == 50;
		custom = template.deepCopy();
		var task = custom.getAsJsonArray("tasks").get(0).getAsJsonObject();
		task.getAsJsonArray("tiers").get(1).getAsJsonObject().addProperty("count", 73);
		QuestDefinition tiered = QuestConfig.parse(custom.toString()).tasks().getFirst();
		assert tiered.target(2) == 73;
		var exploration = custom.deepCopy();
		exploration.getAsJsonArray("tasks").get(0).getAsJsonObject().remove("description");
		var explorationObjective =
		    exploration.getAsJsonArray("tasks").get(0).getAsJsonObject().getAsJsonObject("objective");
		explorationObjective.addProperty("event", "biome_visited");
		explorationObjective.getAsJsonObject("conditions").remove("entity");
		assert QuestConfig.parse(exploration.toString()).tasks().getFirst().target(2) == 73;
		task.addProperty("count", 11);
		reject(custom.toString());
		task.remove("tiers");
		QuestDefinition fixed = QuestConfig.parse(custom.toString()).tasks().getFirst();
		assert fixed.target(1) == 11 && fixed.target(3) == 11 && !fixed.tiered();
		task.addProperty("count", 1.5);
		reject(custom.toString());
		task.remove("count");
		QuestDefinition single = QuestConfig.parse(custom.toString()).tasks().getFirst();
		assert single.target(1) == 1 && single.target(3) == 1 && !single.tiered();
		task.add("tiers", new JsonArray());
		reject(custom.toString());
		custom = template.deepCopy();
		var tiers = custom.getAsJsonArray("tasks").get(0).getAsJsonObject().getAsJsonArray("tiers");
		tiers.get(1).getAsJsonObject().addProperty("tier", 1);
		reject(custom.toString());
		tiers.remove(1);
		reject(custom.toString());

		assert config.progression().score(0, 10000) == 20;
		assert config.progression().score(12000, 10000) == 100;
		assert config.progression().score(0, 0) == 0;
		var gatedJson = template.deepCopy();
		var gatedTask = gatedJson.getAsJsonArray("tasks").get(0).getAsJsonObject();
		gatedTask.add("requires", JsonParser.parseString("[\"diamonds\",\"nether_access\"]"));
		gatedTask.add("rewards",
		              JsonParser.parseString(
		                  "{\"base\":false,\"tongbao\":7,\"items\":[{\"id\":\"minecraft:bread\",\"count\":2}]}"));
		QuestDefinition gated = QuestConfig.parse(gatedJson.toString()).tasks().getFirst();
		QuestDefinition alternative =
		    config.tasks()
		        .stream()
		        .filter(t -> t.difficulty().equals("A") && !t.id().equals(gated.id()) && t.requires().isEmpty())
		        .findFirst()
		        .orElseThrow();
		var pool = new ArrayList<>(config.tasks().stream().filter(t -> !t.difficulty().equals("A")).toList());
		pool.add(gated);
		pool.add(alternative);
		var gatedConfig = new QuestConfig(4, config.progression(), config.milestones(), config.rewards(), pool);
		var met = new HashSet<>(Set.of("diamonds"));
		var gatedDay = new QuestDay(100, 0, 0, met, gatedConfig, new Random(1));
		assert gatedDay.assignments.getFirst().definition.id().equals(alternative.id());
		met.add("nether_access");
		assert !gatedDay.reroll(0, gatedDay.assignments.getFirst().token, true, gatedConfig, new Random(1));
		var eligible = QuestDay.forDate(gatedDay, 101, 0, 0, met, gatedConfig, new Random(1));
		if (!eligible.assignments.getFirst().definition.id().equals(gated.id()))
			assert eligible.reroll(0, eligible.assignments.getFirst().token, true, gatedConfig, new Random(1));
		assert eligible.assignments.getFirst().definition.id().equals(gated.id());
		var customReward = eligible.assignments.getFirst().reward;
		assert customReward.tongbao() == 7 && customReward.coffeeStamp() == 0 &&
		    customReward.items().getFirst().get("count").getAsInt() == 2;
		gatedTask.getAsJsonObject("rewards").addProperty("base", true);
		var withBase = QuestConfig.parse(gatedJson.toString()).tasks().getFirst().reward(config.rewards().get("A"));
		assert withBase.tongbao() == 7 + config.rewards().get("A").tongbao() &&
		    withBase.coffeeStamp() == config.rewards().get("A").coffeeStamp();
		assert customReward.tongbao() == 7;
		var unknown = template.deepCopy();
		unknown.getAsJsonArray("tasks").get(0).getAsJsonObject().add("requires",
		                                                             JsonParser.parseString("[\"missing\"]"));
		reject(unknown.toString());
		Random random = new Random(42);
		QuestDay day = QuestDay.forDate(null, 100, 14, 0, Set.of(), config, random);
		var snapshot = new QuestDay.Assignment(tiered, 2, config.rewards().get("A"));
		assert snapshot.target == 73 && snapshot.tier() == 2 && snapshot.definition.tiers().isEmpty();
		assert new QuestDay.Assignment(fixed, 3, config.rewards().get("A")).tier() == 0;
		assert day.assignments.stream().map(a -> a.definition.id()).distinct().count() == 4;
		assert day.assignments.stream().map(a -> a.definition.difficulty()).toList().equals(QuestConfig.TIERS);
		QuestDay other = new QuestDay(100, 40, 0, Set.of(), config, random);
		for (int i = 0; i < 4; i++)
			assert day.reroll(0, day.assignments.getFirst().token, false, config, random);
		assert day.remaining(config) == 0 && other.remaining(config) == 4;
		assert !day.reroll(0, day.assignments.getFirst().token, false, config, random);
		var changedBands =
		    new QuestConfig(4, new QuestConfig.Progression(.8, .2, 120, 100, List.of(new QuestConfig.Band(0, 3))),
			                config.milestones(), config.rewards(), config.tasks());
		assert day.reroll(0, day.assignments.getFirst().token, true, changedBands, random);
		assert day.tier == 1 && day.assignments.getFirst().tier() <= 1;
		String oldToken = day.assignments.getFirst().token;
		day.assignments.getFirst().progress = 1;
		assert day.reroll(0, oldToken, true, config, random);
		assert day.rerollsUsed == 4 && day.assignments.getFirst().progress == 0 && day.tier == 1;
		assert !day.reroll(0, oldToken, true, config, random);
		var completed = day.assignments.get(1);
		completed.progress = completed.target;
		assert !day.reroll(1, completed.token, true, config, random);
		completed.claimed = true;
		assert !day.reroll(1, completed.token, true, config, random);

		var unfinished = day.assignments.stream().filter(a -> !a.complete() && a.target > 1).findFirst().orElseThrow();
		unfinished.progress = unfinished.target - 1;
		assert QuestDay.forDate(day, 100, 40, 0, Set.of(), config, random) == day;
		assert QuestDay.forDate(day, 99, 40, 0, Set.of(), config, random) == day;
		QuestDay next = QuestDay.forDate(day, 101, 120, 100, Set.of(), config, random);
		assert next != day && next.rerollsUsed == 0 && next.tier == 3;
		assert next.assignments.stream().noneMatch(a -> a.claimed || a.progress != 0);
		QuestDay restored = QuestConfig.JSON.fromJson(QuestConfig.JSON.toJson(day), QuestDay.class);
		assert restored.tier == 1 && restored.rerollsUsed == 4 && restored.assignments.get(1).claimed;
		assert restored.assignments.getFirst().token.equals(day.assignments.getFirst().token);
		assert restored.assignments.getFirst().target == day.assignments.getFirst().target;
		assert restored.assignments.getFirst().definition.description().equals(
		    day.assignments.getFirst().definition.description());

		QuestConfig noRerolls =
		    new QuestConfig(0, config.progression(), config.milestones(), config.rewards(), config.tasks());
		assert !next.reroll(0, next.assignments.getFirst().token, false, noRerolls, random);
		assert next.reroll(0, next.assignments.getFirst().token, true, noRerolls, random);
		QuestConfig noAlternatives = new QuestConfig(4, config.progression(), config.milestones(), config.rewards(),
		                                             next.assignments.stream().map(a -> a.definition).toList());
		String unchanged = next.assignments.getFirst().token;
		assert !next.reroll(0, unchanged, true, noAlternatives, random);
		assert next.assignments.getFirst().token.equals(unchanged) && next.rerollsUsed == 0;

		for (QuestDefinition definition : config.tasks()) {
			QuestDay fixture = new QuestDay(100, 15, 0, Set.of(), config, random);
			fixture.assignments.clear();
			var assignment = new QuestDay.Assignment(definition, 1, config.rewards().get(definition.difficulty()));
			fixture.assignments.add(assignment);
			Map<String, Set<String>> facts = new HashMap<>();
			definition.objective().conditions().entrySet().forEach(c -> {
				if (c.getKey().equals("block_state"))
					c.getValue().getAsJsonObject().entrySet().forEach(
					    p -> facts.put("state." + p.getKey(), Set.of(p.getValue().getAsString())));
				else
					facts.put(c.getKey(), Set.of(QuestDefinition.values(c.getValue()).getFirst()));
			});
			assert !fixture.record(new QuestDefinition.Event("unrelated", facts));
			assert fixture.record(new QuestDefinition.Event(definition.objective().event(), facts));
			assert assignment.progress == 1;
			fixture.record(new QuestDefinition.Event(definition.objective().event(), facts, Integer.MAX_VALUE));
			assert assignment.progress == assignment.target;
			assert !fixture.record(new QuestDefinition.Event(definition.objective().event(), facts));
		}
		var objective = new QuestDefinition.Objective("block_broken", JsonParser.parseString("""
				{"block":["#minecraft:logs","minecraft:stone"],"dimension":"minecraft:overworld","block_state":{"axis":"y"}}
				""").getAsJsonObject());
		objective.conditions().getAsJsonObject("block_state").addProperty("axis", "z");
		assert objective.matches(new QuestDefinition.Event(
		    "block_broken", Map.of("block", Set.of("#minecraft:logs"), "dimension", Set.of("minecraft:overworld"),
			                       "state.axis", Set.of("y"))));
		assert !objective.matches(new QuestDefinition.Event(
		    "block_broken", Map.of("block", Set.of("#minecraft:logs"), "dimension", Set.of("minecraft:the_nether"),
			                       "state.axis", Set.of("y"))));
		assert !objective.matches(new QuestDefinition.Event(
		    "block_broken", Map.of("block", Set.of("minecraft:stone"), "dimension", Set.of("minecraft:overworld"))));
		EconomyChecks.run();
		ArchitectureChecks.run();
		HotspotChecks.run();
		DateChecks.run();
		System.out.println(
		    "每日任务验证通过：配置、事件匹配与合成数量、成长档位、跨日、刷新、快照、双货币去重及写入故障恢复。");
	}

	private static void reject(String text) {
		try {
			QuestConfig.parse(text);
			throw new AssertionError("错误配置被接受");
		} catch (IllegalArgumentException expected) {
		}
	}
}
