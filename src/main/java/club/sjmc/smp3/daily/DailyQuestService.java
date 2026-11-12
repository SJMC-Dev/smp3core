package club.sjmc.smp3.daily;

import java.util.random.RandomGenerator;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.time.ServerDates;

import net.minecraft.server.level.ServerPlayer;

public final class DailyQuestService {
	public enum Status {
		STALE,
		COMPLETE,
		LIMIT_REACHED,
		NO_CANDIDATE,
		REROLLED,
		ALREADY_CLAIMED,
		INCOMPLETE,
		CLAIMED,
		DELIVERY_FAILED
	}

	public record Outcome(Status status, String taskName, QuestDefinition.Reward reward) {
		Outcome(Status status) { this(status, null, null); }
		public boolean success() { return status == Status.REROLLED || status == Status.CLAIMED; }
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

	public QuestDay state(ServerPlayer player) {
		if (failed)
			return null;
		try {
			long today = ServerDates.today().toEpochDay();
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
		QuestDay day = state(player);
		if (day != null && day.date == ServerDates.today().toEpochDay() && day.record(event))
			data.setDirty();
	}

	public Outcome reroll(ServerPlayer player, long date, int slot, String token, boolean admin) {
		QuestDay day = state(player);
		if (!valid(day, date, slot, token))
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
		return new Outcome(Status.REROLLED, day.assignments.get(slot).definition.name(), null);
	}

	public Outcome claim(ServerPlayer player, long date, int slot, String token) {
		QuestDay day = state(player);
		if (!valid(day, date, slot, token))
			return new Outcome(Status.STALE);
		QuestDay.Assignment assignment = day.assignments.get(slot);
		if (assignment.claimed)
			return new Outcome(Status.ALREADY_CLAIMED);
		if (!assignment.complete())
			return new Outcome(Status.INCOMPLETE);
		try {
			Smp3AccountStore.grantDaily(player.getUUID(), day.date, slot, assignment.reward.tongbao(),
			                            assignment.reward.coffeeStamp());
			assignment.claimed = true;
			day.revision++;
			data.setDirty();
			Smp3Core.LOGGER.info("[SMP3] 任务领奖 player={} day={} slot={}", player.getUUID(), day.date, slot + 1);
			return new Outcome(Status.CLAIMED, null, assignment.reward);
		} catch (RuntimeException e) {
			Smp3Core.LOGGER.error("[SMP3] 每日任务奖励提交失败 player={}", player.getUUID(), e);
			return new Outcome(Status.DELIVERY_FAILED);
		}
	}

	private static boolean valid(QuestDay day, long date, int slot, String token) {
		return day != null && date == ServerDates.today().toEpochDay() && day.date == date && slot >= 0 && slot < 4 &&
		    day.assignments.get(slot).token.equals(token);
	}
}
