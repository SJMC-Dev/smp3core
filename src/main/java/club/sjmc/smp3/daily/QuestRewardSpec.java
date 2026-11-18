package club.sjmc.smp3.daily;

import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

import com.google.gson.JsonObject;

import club.sjmc.smp3.item.RewardBundle;

import net.minecraft.server.level.ServerPlayer;

public record QuestRewardSpec(Boolean base, List<JsonObject> grants, List<Branch> when) {
	public QuestRewardSpec {
		grants = grants == null ? List.of() : grants.stream().map(JsonObject::deepCopy).toList();
		when = when == null ? List.of() : List.copyOf(when);
	}
	record Branch(List<String> requires, QuestRewardSpec rewards) {}
	QuestReward resolve(QuestReward category, Set<String> milestones, RandomGenerator generator, ServerPlayer player) {
		for (var branch : when)
			if (milestones.containsAll(branch.requires))
				return branch.rewards.resolve(category, milestones, generator, player);
		var result = RewardBundle.resolve(grants, generator, player);
		boolean include = !Boolean.FALSE.equals(base);
		return new QuestReward(Math.addExact(include ? category.tongbao() : 0, result.tongbao()),
		                       Math.addExact(include ? category.coffeeStamp() : 0, result.coffeeStamp()),
		                       result.items(), result.effects());
	}
}
