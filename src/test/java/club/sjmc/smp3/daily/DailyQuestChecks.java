package club.sjmc.smp3.daily;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import club.sjmc.smp3.economy.EconomyChecks;
import club.sjmc.smp3.notice.NoticeChecks;
import club.sjmc.smp3.time.DateChecks;
import club.sjmc.smp3.watchdog.hotspot.HotspotChecks;

public final class DailyQuestChecks {
	public static void main(String[] args) throws Exception {
		JsonObject template;
		try (var input = DailyQuestChecks.class.getResourceAsStream("/dailyquests-fixture.json")) {
			template =
			    JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		}
		var config = QuestConfig.parse(template.toString());
		var random = new Random(42);
		var day = new QuestDay(100, 0, 0, Set.of(), config, random, null);
		assert day.assignments.stream().map(a -> a.definition.id()).distinct().count() == 4;
		assert day.assignments.stream().map(a -> a.definition.difficulty()).toList().equals(QuestConfig.TIERS);
		for (int i = 0; i < 4; i++)
			assert day.reroll(0, day.assignments.getFirst().token, false, config, random, null);
		assert !day.reroll(0, day.assignments.getFirst().token, false, config, random, null);
		assert day.reroll(0, day.assignments.getFirst().token, true, config, random, null);
		assert day.rerollsUsed == 4;
		assert QuestDay.forDate(day, 99, 120, 100, Set.of(), config, random, null) == day;
		assert QuestDay.forDate(day, 100, 120, 100, Set.of(), config, random, null) == day;
		var next = QuestDay.forDate(day, 101, 120, 100, Set.of(), config, random, null);
		assert next.tier == 3 && next.rerollsUsed == 0 && next.assignments.stream().allMatch(a -> a.progress == 0);
		var invalid = template.deepCopy();
		invalid.addProperty("dailyRerollLimit", -1);
		reject(invalid);
		invalid = template.deepCopy();
		invalid.getAsJsonArray("tasks").asList().removeIf(
		    v -> v.getAsJsonObject().get("difficulty").getAsString().equals("A"));
		reject(invalid);
		var task = template.getAsJsonArray("tasks").get(0).getAsJsonObject();
		task.remove("tiers");
		task.remove("description");
		task.remove("rewards");
		task.add("objectives", JsonParser.parseString("""
		    [{"event":"block_broken","target":{"field":"block","values":["minecraft:poppy","minecraft:dandelion","minecraft:allium"]},"countBy":"distinct","selection":"random","count":2}]
		    """));
		config = QuestConfig.parse(template.toString());
		var assignment = assign(day, config);
		var chosen = QuestDefinition.values(assignment.definition.objectives().getFirst().target().values());
		assert chosen.size() == 2 && Set.copyOf(chosen).size() == 2;
		var event = new QuestDefinition.Event("block_broken",
		                                      Map.of("block", Set.of(chosen.getFirst(), "#minecraft:flowers")), 64);
		assert day.record(event) && assignment.progress == 1;
		assert !day.record(event);
		var restored = QuestConfig.JSON.fromJson(QuestConfig.JSON.toJson(day), QuestDay.class);
		assert restored.assignments.getFirst().definition.equals(assignment.definition);
		assert !restored.record(event);
		assert restored.record(new QuestDefinition.Event("block_broken", Map.of("block", Set.of(chosen.getLast()))));
		assert restored.assignments.getFirst().complete();
		assignment.reset();
		assert assignment.progress == 0;
		assert day.record(event) && assignment.progress == 1;
		task.add("objectives", JsonParser.parseString("""
		    [{"event":"inventory_changed","conditions":{"item":"minecraft:bread"},"count":2},
		     {"event":"item_crafted","conditions":{"item":"minecraft:apple"},"count":3}]
		    """));
		config = QuestConfig.parse(template.toString());
		assignment = assign(day, config);
		var bread = new QuestDefinition.Event("inventory_changed", Map.of("item", Set.of("minecraft:bread")), 2);
		var apple = new QuestDefinition.Event("item_crafted", Map.of("item", Set.of("minecraft:apple")), 3);
		day.updateInventory(List.of(bread));
		day.record(apple);
		assert !assignment.complete();
		day.updateInventory(List.of());
		assert !assignment.complete() && assignment.goalProgress(0) == 0;
		day.updateInventory(List.of(bread));
		assert assignment.complete();
		assert !day.updateInventory(List.of()) && assignment.complete();
		task.addProperty("completion", "any");
		config = QuestConfig.parse(template.toString());
		assignment = assign(day, config);
		day.record(apple);
		day.updateInventory(List.of());
		assert assignment.complete();
		task.add("objectives", JsonParser.parseString("""
		    [{"event":"item_crafted","target":{"field":"item","values":["minecraft:bread","minecraft:apple"]},"selection":"random","tiers":[{"tier":2,"count":4}]}]
		    """));
		task.add("rewards", JsonParser.parseString("""
		    {"base":false,"grants":[{"type":"random","draws":2,"unique":true,"pool":"food"}]}
		    """));
		template.add("rewardPools", JsonParser.parseString("""
		    {"food":[{"name":"Bread","grants":[{"type":"item","id":"minecraft:bread","count":2}]},
		    {"name":"Apple","grants":[{"type":"item","id":"minecraft:apple","count":{"min":3,"max":3}}]}]}
		    """));
		config = QuestConfig.parse(template.toString());
		var definition = config.tasks().getFirst();
		assert !definition.supports(1) && definition.supports(2) && !definition.supports(3);
		assignment = new QuestDay.Assignment(definition, 2, new QuestReward(10, 1), Set.of(), random, null);
		assert assignment.target == 4 && assignment.reward.tongbao() == 0 && assignment.reward.items().size() == 2;
		assert assignment.reward.items().stream().map(v -> v.get("id").getAsString()).distinct().count() == 2;
		var saved = QuestConfig.JSON.fromJson(QuestConfig.JSON.toJson(assignment), QuestDay.Assignment.class);
		assert saved.reward.equals(assignment.reward);
		task.remove("rewards");
		task.add("objectives", JsonParser.parseString("""
		    [{"event":"block_broken","conditions":{"block":"minecraft:emerald_ore"},"count":{"min":4,"max":8}},
		     {"event":"block_broken","conditions":{"block":"minecraft:ancient_debris"},"tiers":[{"tier":1,"count":{"min":1,"max":2}}]}]
		    """));
		config = QuestConfig.parse(template.toString());
		var observed = new HashSet<Integer>();
		for (int i = 0; i < 32; i++) {
			assignment =
			    new QuestDay.Assignment(config.tasks().getFirst(), 1, new QuestReward(0, 0), Set.of(), random, null);
			int count = assignment.definition.objectives().getFirst().count();
			int second = assignment.definition.objectives().getLast().count();
			assert count >= 4 && count <= 8 && second >= 1 && second <= 2;
			observed.add(count);
		}
		assert observed.containsAll(Set.of(4, 5, 6, 7, 8));
		saved = QuestConfig.JSON.fromJson(QuestConfig.JSON.toJson(assignment), QuestDay.Assignment.class);
		saved.reset();
		assert saved.definition.equals(assignment.definition);
		var firstGoal = task.getAsJsonArray("objectives").get(0).getAsJsonObject();
		for (String bad :
		     List.of("{\"min\":0,\"max\":8}", "{\"min\":8,\"max\":4}", "{\"min\":4,\"max\":4.5}", "{\"min\":4}")) {
			firstGoal.add("count", JsonParser.parseString(bad));
			reject(template);
		}
		firstGoal.addProperty("count", 4);

		task.add("rewards", JsonParser.parseString("{\"grants\":[{\"type\":\"random\",\"pool\":\"food\"}]}"));
		template.getAsJsonObject("rewardPools")
		    .getAsJsonArray("food")
		    .get(0)
		    .getAsJsonObject()
		    .add("grants", JsonParser.parseString("[{\"type\":\"random\",\"pool\":\"food\"}]"));
		reject(template);
		EconomyChecks.run();
		HotspotChecks.run();
		DateChecks.run();
		NoticeChecks.run();
		System.out.println("Quest checks passed: assignment, reset/day boundary, target snapshots, distinct/all/any, "
		                   + "inventory latch and reward bundles.");
	}
	private static QuestDay.Assignment assign(QuestDay day, QuestConfig config) {
		var task =
		    new QuestDay.Assignment(config.tasks().getFirst(), 1, new QuestReward(0, 0), Set.of(), new Random(1), null);
		day.assignments.clear();
		day.assignments.add(task);
		return task;
	}
	private static void reject(JsonObject json) {
		try {
			QuestConfig.parse(json.toString());
			throw new AssertionError("Invalid config accepted");
		} catch (IllegalArgumentException expected) {
		}
	}
}
