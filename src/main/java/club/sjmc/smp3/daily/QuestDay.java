package club.sjmc.smp3.daily;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class QuestDay {
	public final long date;
	public final int xpLevel;
	final int tier;
	int rerollsUsed;
	final List<Assignment> assignments = new ArrayList<>();
	List<PendingReward> pendingRewards = new ArrayList<>();
	transient int revision;
	transient long retryAt;
	transient boolean rewardFailureReported;

	record PendingReward(long date, int slot, String name, QuestDefinition.Reward reward) {}

	QuestDay(long date, int xpLevel, QuestConfig config, RandomGenerator random) {
		this.date = date;
		this.xpLevel = xpLevel;
		this.tier = config.tier(xpLevel);
		for (String difficulty : QuestConfig.TIERS) {
			QuestDefinition definition = choose(config, difficulty, random);
			assignments.add(new Assignment(definition, this.tier, config.rewards().get(difficulty)));
		}
	}

	static QuestDay forDate(QuestDay previous, long date, int level, QuestConfig config, RandomGenerator random) {
		if (previous == null)
			return new QuestDay(date, level, config, random);
		if (date <= previous.date)
			return previous;
		QuestDay next = new QuestDay(date, level, config, random);
		next.pendingRewards.addAll(previous.pendingRewards);
		for (int slot = 0; slot < previous.assignments.size(); slot++) {
			Assignment task = previous.assignments.get(slot);
			if (task.complete() && !task.claimed)
				next.pendingRewards.add(new PendingReward(previous.date, slot, task.definition.name(), task.reward));
		}
		next.retryAt = previous.retryAt;
		next.rewardFailureReported = previous.rewardFailureReported;
		return next;
	}

	public static final class Assignment {
		public final String token = UUID.randomUUID().toString();
		public final QuestDefinition definition;
		public final int target;
		private final int tier;
		public final QuestDefinition.Reward reward;
		int progress;
		boolean claimed;

		Assignment(QuestDefinition definition, int tier, QuestDefinition.Reward reward) {
			this.target = definition.target(tier);
			this.tier = definition.tiered() ? tier : 0;
			this.definition = definition.snapshot(target);
			this.reward = reward;
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

	boolean reroll(int slot, String token, boolean admin, QuestConfig config, RandomGenerator random) {
		Assignment old = assignments.get(slot);
		if (!old.token.equals(token) || old.complete() || (!admin && remaining(config) == 0))
			return false;
		QuestDefinition next = choose(config, old.definition.difficulty(), random);
		if (next == null)
			return false;
		assignments.set(slot, new Assignment(next, tier, old.reward));
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
		    .filter(t -> t.difficulty().equals(difficulty) && t.supports(this.tier) && !used.contains(t.id()))
		    .toList();
	}
}
