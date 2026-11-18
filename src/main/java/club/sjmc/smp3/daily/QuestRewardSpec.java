package club.sjmc.smp3.daily;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

import com.google.gson.JsonObject;

public record QuestRewardSpec(Boolean base, int tongbao, int coffeeStamp, List<JsonObject> items, List<Draw> random,
                              List<Branch> when) {
	public QuestRewardSpec {
		items = items == null ? List.of() : items.stream().map(JsonObject::deepCopy).toList();
		random = random == null ? List.of() : List.copyOf(random);
		when = when == null ? List.of() : List.copyOf(when);
	}

	record Entry(JsonObject item, int weight) {}
	record Draw(int draws, boolean unique, List<Entry> entries) {}
	record Branch(List<String> requires, QuestRewardSpec rewards) {}

	QuestReward resolve(QuestReward category, Set<String> milestones, RandomGenerator generator) {
		for (var branch : when)
			if (milestones.containsAll(branch.requires))
				return branch.rewards.resolve(category, milestones, generator);
		var contents = new ArrayList<>(items);
		for (var draw : random) {
			var pool = new ArrayList<>(draw.entries);
			long weight = pool.stream().mapToLong(Entry::weight).sum();
			for (int i = 0; i < draw.draws; i++) {
				long roll = generator.nextLong(weight);
				for (int j = 0; j < pool.size(); j++) {
					var entry = pool.get(j);
					roll -= entry.weight;
					if (roll < 0) {
						contents.add(entry.item);
						if (draw.unique) {
							weight -= entry.weight;
							pool.remove(j);
						}
						break;
					}
				}
			}
		}
		boolean include = !Boolean.FALSE.equals(base);
		return new QuestReward(Math.addExact(include ? category.tongbao() : 0, tongbao),
		                       Math.addExact(include ? category.coffeeStamp() : 0, coffeeStamp), contents);
	}
}
