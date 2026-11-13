package club.sjmc.smp3.daily;

import java.util.random.RandomGenerator;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.time.ServerDates;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

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
			QuestDay day = QuestDay.forDate(previous, today, player.experienceLevel, config, random);
			if (day != previous) {
				data.players.put(player.getUUID(), day);
				data.setDirty();
			}
			for (int slot = 0; slot < day.assignments.size(); slot++) {
				QuestDay.Assignment assignment = day.assignments.get(slot);
				if (!assignment.claimed && Smp3AccountStore.dailyClaimed(player.getUUID(), day.date, slot)) {
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
		if (day != null && day.date == today && day.record(event))
			data.setDirty();
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
			// Settle older days first: the ledger keeps the latest paid day and its slot mask.
			var pending = day.pendingRewards.iterator();
			while (pending.hasNext()) {
				var reward = pending.next();
				pay(player, reward.date(), reward.slot(), reward.name(), reward.reward());
				pending.remove();
				data.setDirty();
			}
			for (int slot = 0; slot < day.assignments.size(); slot++) {
				var assignment = day.assignments.get(slot);
				if (!assignment.complete() || assignment.claimed)
					continue;
				pay(player, day.date, slot, assignment.definition.name(), assignment.reward);
				assignment.claimed = true;
				day.revision++;
				data.setDirty();
			}
			day.retryAt = 0;
			day.rewardFailureReported = false;
		} catch (RuntimeException e) {
			day.retryAt = now + 100;
			if (!day.rewardFailureReported) {
				day.rewardFailureReported = true;
				Smp3Core.LOGGER.error("[SMP3] 每日任务奖励提交失败，将自动重试 player={}", player.getUUID(), e);
				player.sendSystemMessage(Component.literal("任务奖励暂未到账，将自动重试"));
			}
		}
	}

	private static void pay(ServerPlayer player, long date, int slot, String name, QuestDefinition.Reward reward) {
		if (Smp3AccountStore.dailyClaimed(player.getUUID(), date, slot))
			return;
		Smp3AccountStore.grantDaily(player.getUUID(), date, slot, reward.tongbao(), reward.coffeeStamp());
		Smp3Core.LOGGER.info("[SMP3] 任务奖励到账 player={} day={} slot={}", player.getUUID(), date, slot + 1);
		player.sendSystemMessage(Component.literal("已完成「" + name + "」，奖励到账：通宝 x " + reward.tongbao() +
		                                           "，咖啡印章 x " + reward.coffeeStamp()));
	}

	private static boolean valid(QuestDay day, long today, long date, int slot, String token) {
		return day != null && date == today && day.date == date && slot >= 0 && slot < 4 &&
		    day.assignments.get(slot).token.equals(token);
	}
}
