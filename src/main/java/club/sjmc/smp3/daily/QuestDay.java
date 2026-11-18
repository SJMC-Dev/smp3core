package club.sjmc.smp3.daily;

import java.util.ArrayList;
import java.util.Arrays;
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
			assignments.add(
			    new Assignment(definition, this.tier, config.rewards().get(difficulty), milestones, random));
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

		private final int[] goalProgress;

		Assignment(QuestDefinition definition, int tier, QuestReward base, Set<String> milestones,
		           RandomGenerator random) {
			this.tier =
			    definition.tiered() || definition.objectives().stream().anyMatch(g -> !g.tiers().isEmpty()) ? tier : 0;
			this.definition = definition.resolve(tier, random);
			this.target = this.definition.objectives().isEmpty()       ? this.definition.count()
			              : this.definition.completion().equals("any") ? 1
			                                                           : this.definition.objectives().size();
			this.goalProgress = new int[this.definition.objectives().size()];
			this.reward =
			    this.definition.rewards() == null ? base : this.definition.rewards().resolve(base, milestones, random);
		}

		void reset() {
			progress = 0;
			claimed = false;
			Arrays.fill(goalProgress, 0);
		}

		public int goalProgress(int index) {
			return definition.objectives().isEmpty() ? progress : goalProgress[index];
		}

		boolean tracks(String event) {
			return definition.goals().stream().anyMatch(g -> g.objective().event().equals(event));
		}

		private boolean update(List<QuestDefinition.Event> events, boolean inventory) {
			if (complete())
				return false;
			boolean changed = false;
			var goals = definition.goals();
			for (int i = 0; i < goals.size(); i++) {
				var goal = goals.get(i);
				if (goal.objective().event().equals(QuestEventType.INVENTORY_CHANGED.id()) != inventory)
					continue;
				int value = inventory ? 0 : goalProgress(i);
				for (var event : events)
					if (goal.objective().matches(event))
						value += Math.min(event.amount(), goal.count() - value);
				if (value != goalProgress(i)) {
					if (definition.objectives().isEmpty())
						progress = value;
					else
						goalProgress[i] = value;
					changed = true;
				}
			}
			// Inventory goals settle at tick end, after crafting has consumed its inputs.
			if (!definition.objectives().isEmpty() && (inventory || !tracks(QuestEventType.INVENTORY_CHANGED.id()))) {
				int done = 0;
				for (int i = 0; i < goals.size(); i++)
					if (goalProgress[i] >= goals.get(i).count())
						done++;
				int updated = Math.min(done, target);
				changed |= progress != updated;
				progress = updated;
			}
			return changed;
		}

		public int tier() { return tier; }

		public int progress() { return progress; }

		public boolean claimed() { return claimed; }

		public boolean complete() { return progress >= target; }
	}

	public int revision() { return revision; }

	public List<Assignment> assignments() { return List.copyOf(assignments); }

	public int remaining(QuestConfig config) { return Math.max(0, config.dailyRerollLimit() - rerollsUsed); }

	boolean record(QuestDefinition.Event event) { return event.amount() > 0 && update(List.of(event), false); }

	boolean tracksInventory() {
		return assignments.stream().anyMatch(a -> !a.complete() && a.tracks(QuestEventType.INVENTORY_CHANGED.id()));
	}

	boolean updateInventory(List<QuestDefinition.Event> contents) { return update(contents, true); }

	private boolean update(List<QuestDefinition.Event> events, boolean inventory) {
		boolean changed = false;
		for (var assignment : assignments)
			changed |= assignment.update(events, inventory);
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
		assignments.set(slot, new Assignment(next, tier, config.rewards().get(next.difficulty()), milestones, random));
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
