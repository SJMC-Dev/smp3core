package club.sjmc.smp3.daily;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.random.RandomGenerator;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.reward.Reward;
import club.sjmc.smp3.reward.RewardItems;
import club.sjmc.smp3.reward.RewardService;
import club.sjmc.smp3.time.ServerDates;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

public final class DailyQuestService {
	public enum Status { STALE, COMPLETE, LIMIT_REACHED, NO_CANDIDATE, REROLLED }

	public record Outcome(Status status, String taskName) {
		Outcome(Status status) { this(status, null); }
		public boolean success() { return status == Status.REROLLED; }
	}
	public final QuestConfig config;
	private final DailyQuestData data;
	private final RandomGenerator random;
	private boolean failed;

	DailyQuestService(QuestConfig config, DailyQuestData data, RandomGenerator random) {
		this.config = config;
		this.data = data;
		this.random = random;
	}

	public QuestDay state(ServerPlayer player) { return state(player, ServerDates.today().toEpochDay()); }

	private QuestDay state(ServerPlayer player, long today) {
		if (failed)
			return null;
		try {
			QuestDay previous = data.players.get(player.getUUID());
			var progress = RewardService.progress(player);
			if (previous != null && player.level().getGameTime() < previous.retryAt)
				return previous;
			if (previous != null) {
				for (int slot = 0; slot < previous.assignments.size(); slot++) {
					var task = previous.assignments.get(slot);
					if (progress.completed(previous.date, slot))
						task.progress = task.target;
					if (task.complete() && !progress.completed(previous.date, slot)) {
						try {
							RewardService.complete(player, previous.date, slot, task.definition.name(), task.reward);
						} catch (RuntimeException e) {
							retry(player, previous, e);
							return previous;
						}
					}
				}
			}
			QuestDay day = previous;
			if (previous == null || today > previous.date) {
				var milestones = new HashSet<String>();
				config.milestones().forEach((name, id) -> {
					var advancement = player.level().getServer().getAdvancements().get(Identifier.parse(id));
					if (advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone())
						milestones.add(name);
				});
				day = QuestDay.forDate(previous, today, RewardService.progress(player).completed(),
				                       player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 72000.0,
				                       milestones, config, random);
			}
			if (day != previous) {
				data.players.put(player.getUUID(), day);
				data.setDirty();
			}
			for (int slot = 0; slot < day.assignments.size(); slot++) {
				QuestDay.Assignment assignment = day.assignments.get(slot);
				if (!assignment.claimed && progress.paid(day.date, slot)) {
					assignment.progress = assignment.target;
					assignment.claimed = true;
					day.revision++;
					data.setDirty();
				}
			}
			return day;
		} catch (RuntimeException e) {
			failed = true;
			Smp3Core.LOGGER.error("[SMP3] 每日任务存储不可用，停止任务操作", e);
			return null;
		}
	}

	public void record(ServerPlayer player, QuestDefinition.Event event) {
		long today = ServerDates.today().toEpochDay();
		QuestDay day = state(player, today);
		if (day != null && day.date == today && day.record(event)) {
			data.setDirty();
			if (player.level().getGameTime() >= day.retryAt) {
				try {
					for (int slot = 0; slot < day.assignments.size(); slot++) {
						var task = day.assignments.get(slot);
						if (task.complete())
							RewardService.complete(player, day.date, slot, task.definition.name(), task.reward);
					}
				} catch (RuntimeException e) {
					retry(player, day, e);
				}
			}
		}
	}

	public Outcome reroll(ServerPlayer player, long date, int slot, String token, boolean admin) {
		long today = ServerDates.today().toEpochDay();
		QuestDay day = state(player, today);
		if (!valid(day, today, date, slot, token))
			return new Outcome(Status.STALE);
		QuestDay.Assignment previous = day.assignments.get(slot);
		if (previous.complete())
			return new Outcome(Status.COMPLETE);
		if (!admin && day.remaining(config) == 0)
			return new Outcome(Status.LIMIT_REACHED);
		if (!day.reroll(slot, token, admin, config, random))
			return new Outcome(Status.NO_CANDIDATE);
		data.setDirty();
		Smp3Core.LOGGER.info("[SMP3] 任务刷新 player={} slot={} admin={} {} -> {}", player.getUUID(), slot + 1, admin,
		                     previous.definition.id(), day.assignments.get(slot).definition.id());
		return new Outcome(Status.REROLLED, day.assignments.get(slot).definition.name());
	}

	void settle(ServerPlayer player) {
		long today = ServerDates.today().toEpochDay();
		QuestDay day = state(player, today);
		long now = player.level().getGameTime();
		if (day == null || day.date > today || now < day.retryAt)
			return;
		try {
			for (var reward : RewardService.ready(player))
				pay(player, reward);
			var progress = RewardService.progress(player);
			for (int slot = 0; slot < day.assignments.size(); slot++) {
				var assignment = day.assignments.get(slot);
				if (!assignment.claimed && progress.paid(day.date, slot)) {
					assignment.claimed = true;
					day.revision++;
					data.setDirty();
				}
			}
			day.retryAt = 0;
			RewardService.deliver(player);
			day.rewardFailureReported = false;
		} catch (RuntimeException e) {
			retry(player, day, e);
		}
	}

	private static void retry(ServerPlayer player, QuestDay day, RuntimeException error) {
		day.retryAt = player.level().getGameTime() + 100;
		if (!day.rewardFailureReported) {
			day.rewardFailureReported = true;
			Smp3Core.LOGGER.error("[SMP3] 每日任务奖励提交失败，将自动重试 player={}", player.getUUID(), error);
			player.sendSystemMessage(Component.literal("任务奖励暂未到账，将自动重试"));
		}
	}

	private static void pay(ServerPlayer player, RewardService.DailyReward entitlement) {
		long date = entitlement.day();
		int slot = entitlement.slot();
		String name = entitlement.name();
		Reward reward = entitlement.reward();
		if (RewardService.progress(player).paid(date, slot))
			return;
		RewardService.grant(player, entitlement);
		Smp3Core.LOGGER.info("[SMP3] 任务奖励到账 player={} day={} slot={}", player.getUUID(), date, slot + 1);
		RewardService.deliver(player);
		var parts = new ArrayList<String>();
		if (reward.tongbao() > 0)
			parts.add("通宝 x " + reward.tongbao());
		if (reward.coffeeStamp() > 0)
			parts.add("咖啡印章 x " + reward.coffeeStamp());
		reward.items().forEach(item -> {
			var stack = RewardItems.stack(item, player.registryAccess());
			parts.add(stack.getHoverName().getString() + " x " + stack.getCount());
		});
		player.sendSystemMessage(Component.literal(
		    "已完成「" + name + "」" + (parts.isEmpty() ? "" : "，奖励已结算：" + String.join("，", parts)) +
		    (RewardService.pending(player).isEmpty() ? "" : "；背包不足，剩余物品保留待领取")));
	}

	private static boolean valid(QuestDay day, long today, long date, int slot, String token) {
		return day != null && date == today && day.date == date && slot >= 0 && slot < 4 &&
		    day.assignments.get(slot).token.equals(token);
	}
}
