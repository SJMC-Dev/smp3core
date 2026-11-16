package club.sjmc.smp3.daily;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

import net.minecraft.core.GlobalPos;

public final class QuestDay {
	transient GlobalPos inventoryLocation;
	public final long date;
	public final long completedQuests;
	public final double playtimeHours;
	final Set<String> milestones;
	final int tier;
	int rerollsUsed;
	final List<Assignment> assignments = new ArrayList<>();
	transient int revision;
	transient long retryAt;
	transient boolean rewardFailureReported;

	QuestDay(long date, long completedQuests, double playtimeHours, Set<String> milestones, QuestConfig config,
	         RandomGenerator random) {
		this.date = date;
		this.completedQuests = completedQuests;
		this.playtimeHours = playtimeHours;
		this.milestones = Set.copyOf(milestones);
		this.tier = config.progression().tier(config.progression().score(completedQuests, playtimeHours));
		for (String difficulty : QuestConfig.TIERS) {
			QuestDefinition definition = choose(config, difficulty, random);
			assignments.add(new Assignment(definition, this.tier, config.rewards().get(difficulty)));
		}
	}

	static QuestDay forDate(QuestDay previous, long date, long completed, double hours, Set<String> milestones,
	                        QuestConfig config, RandomGenerator random) {
		if (previous == null)
			return new QuestDay(date, completed, hours, milestones, config, random);
		if (date <= previous.date)
			return previous;
		QuestDay next = new QuestDay(date, completed, hours, milestones, config, random);
		next.retryAt = previous.retryAt;
		next.rewardFailureReported = previous.rewardFailureReported;
		return next;
	}

	public static final class Assignment {
		public final String token = UUID.randomUUID().toString();
		public final QuestDefinition definition;
		public final int target;
		private final int tier;
		public final QuestReward reward;
		int progress;
		boolean claimed;

		Assignment(QuestDefinition definition, int tier, QuestReward reward) {
			this.target = definition.target(tier);
			this.tier = definition.tiered() ? tier : 0;
			this.definition = definition.snapshot(target);
			this.reward = definition.reward(reward);
		}

		public int tier() { return tier; }

		public int progress() { return progress; }

		public boolean claimed() { return claimed; }

		public boolean complete() { return progress >= target; }
	}

	public int revision() { return revision; }

	public List<Assignment> assignments() { return List.copyOf(assignments); }

	public int remaining(QuestConfig config) { return Math.max(0, config.dailyRerollLimit() - rerollsUsed); }

	boolean record(QuestDefinition.Event event) {
		if (event.amount() <= 0)
			return false;
		boolean changed = false;
		for (Assignment assignment : assignments) {
			if (!assignment.complete() && assignment.definition.objective().matches(event)) {
				assignment.progress += Math.min(event.amount(), assignment.target - assignment.progress);
				changed = true;
			}
		}
		if (changed)
			revision++;
		return changed;
	}

	boolean tracksInventory() {
		return assignments.stream().anyMatch(
		    a -> !a.complete() && a.definition.objective().event().equals(QuestEventType.INVENTORY_CHANGED.id()));
	}

	boolean updateInventory(List<QuestDefinition.Event> contents) {
		boolean changed = false;
		for (Assignment assignment : assignments) {
			if (assignment.complete() ||
			    !assignment.definition.objective().event().equals(QuestEventType.INVENTORY_CHANGED.id()))
				continue;
			int count = 0;
			for (var stack : contents)
				if (assignment.definition.objective().matches(stack))
					count += Math.min(stack.amount(), assignment.target - count);
			if (assignment.progress != count) {
				assignment.progress = count;
				changed = true;
			}
		}
		if (changed)
			revision++;
		return changed;
	}

	boolean reroll(int slot, String token, boolean admin, QuestConfig config, RandomGenerator random) {
		Assignment old = assignments.get(slot);
		if (!old.token.equals(token) || old.complete() || (!admin && remaining(config) == 0))
			return false;
		QuestDefinition next = choose(config, old.definition.difficulty(), random);
		if (next == null)
			return false;
		assignments.set(slot, new Assignment(next, tier, config.rewards().get(next.difficulty())));
		if (!admin)
			rerollsUsed++;
		revision++;
		return true;
	}

	public boolean canReroll(int slot, QuestConfig config) {
		Assignment assignment = assignments.get(slot);
		return !assignment.complete() && remaining(config) > 0 &&
		    !candidates(config, assignment.definition.difficulty()).isEmpty();
	}

	private QuestDefinition choose(QuestConfig config, String difficulty, RandomGenerator random) {
		List<QuestDefinition> choices = candidates(config, difficulty);
		if (choices.isEmpty())
			return null;
		Set<String> categories = new HashSet<>();
		assignments.forEach(a -> categories.add(a.definition.category()));
		List<QuestDefinition> varied = choices.stream().filter(t -> !categories.contains(t.category())).toList();
		if (!varied.isEmpty())
			choices = varied;
		return choices.get(random.nextInt(choices.size()));
	}

	private List<QuestDefinition> candidates(QuestConfig config, String difficulty) {
		Set<String> used = new HashSet<>();
		assignments.forEach(a -> used.add(a.definition.id()));
		return config.tasks()
		    .stream()
		    .filter(t
		            -> t.difficulty().equals(difficulty) && t.supports(this.tier) &&
		                   milestones.containsAll(t.requires()) && !used.contains(t.id()))
		    .toList();
	}
}
