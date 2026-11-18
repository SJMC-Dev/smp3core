package club.sjmc.smp3.sign;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import net.minecraft.nbt.CompoundTag;

public final class PlayerSignData {

	public static final long NEVER_SIGNED = Long.MIN_VALUE;

	private long lastSignEpochDay = NEVER_SIGNED;
	private int streak;
	private String monthKey = "";
	private int monthTotal;
	private int serverTotal;
	private final Set<Integer> monthClaimed = new HashSet<>();
	private final Set<Integer> serverClaimed = new HashSet<>();
	private final Set<Integer> monthSignedDays = new HashSet<>();

	final List<Claim> pending = new ArrayList<>();
	transient JsonObject receipt;

	static final class Claim {
		final String id;
		final String month;
		final int days;
		final List<Delivery> deliveries;

		Claim(String month, SignConfig.Milestone milestone) {
			this.month = month;
			days = milestone.days();
			id = (month.isEmpty() ? "server" : month) + ":" + days;
			deliveries = milestone.rewards().stream().map(Delivery::new).toList();
		}
	}

	static final class Delivery {
		final SignReward reward;
		Status status = Status.READY;

		Delivery(SignReward reward) { this.reward = reward; }
	}

	enum Status { READY, IN_FLIGHT, DONE }

	public boolean hasPendingRewards() { return !pending.isEmpty(); }

	boolean rotateMonth(LocalDate today) {
		String key = YearMonth.from(today).toString();
		if (!monthKey.isEmpty() && key.compareTo(monthKey) <= 0) {
			return false;
		}
		monthKey = key;
		monthTotal = 0;
		monthClaimed.clear();
		monthSignedDays.clear();
		return true;
	}

	void sign(LocalDate today) {
		streak = lastSignEpochDay == today.minusDays(1).toEpochDay() ? streak + 1 : 1;
		lastSignEpochDay = today.toEpochDay();
		monthTotal++;
		serverTotal++;
		monthSignedDays.add(today.getDayOfMonth());
		queueMilestones(SignModule.config());
	}

	boolean queueMilestones(SignConfig config) {
		int previous = pending.size();
		for (var milestone : config.monthly()) {
			if (monthTotal >= milestone.days() && !monthClaimed.contains(milestone.days())) {
				queue(monthKey, milestone);
			}
		}
		for (var milestone : config.lifetime()) {
			if (serverTotal >= milestone.days() && !serverClaimed.contains(milestone.days())) {
				queue("", milestone);
			}
		}
		return pending.size() != previous;
	}

	private void queue(String month, SignConfig.Milestone milestone) {
		Claim claim = new Claim(month, milestone);
		if (pending.stream().noneMatch(existing -> existing.id.equals(claim.id))) {
			pending.add(claim);
		}
	}

	void finish(Claim claim) {
		if (claim.month.isEmpty()) {
			serverClaimed.add(claim.days);
		} else if (claim.month.equals(monthKey)) {
			monthClaimed.add(claim.days);
		}
		pending.remove(claim);
	}

	public long lastSignEpochDay() { return lastSignEpochDay; }

	public int streak() { return streak; }

	public String monthKey() { return monthKey; }

	public int monthTotal() { return monthTotal; }

	public int serverTotal() { return serverTotal; }

	public boolean isMonthClaimed(int days) { return monthClaimed.contains(days); }

	public boolean isMonthSigned(int dayOfMonth) { return monthSignedDays.contains(dayOfMonth); }

	public boolean isServerClaimed(int days) { return serverClaimed.contains(days); }

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("lastSignEpochDay", lastSignEpochDay);
		tag.putInt("streak", streak);
		tag.putString("monthKey", monthKey);
		tag.putInt("monthTotal", monthTotal);
		tag.putInt("serverTotal", serverTotal);
		tag.putIntArray("monthClaimed", toArray(monthClaimed));
		tag.putIntArray("serverClaimed", toArray(serverClaimed));
		tag.putIntArray("monthSignedDays", toArray(monthSignedDays));
		tag.putString("pendingRewards", SignData.JSON.toJson(pending));
		return tag;
	}

	public static PlayerSignData load(CompoundTag tag) {
		PlayerSignData data = new PlayerSignData();
		data.lastSignEpochDay = tag.getLongOr("lastSignEpochDay", NEVER_SIGNED);
		data.streak = tag.getIntOr("streak", 0);
		data.monthKey = tag.getStringOr("monthKey", "");
		data.monthTotal = tag.getIntOr("monthTotal", 0);
		data.serverTotal = tag.getIntOr("serverTotal", 0);
		fill(data.monthClaimed, tag.getIntArray("monthClaimed").orElse(new int[0]));
		fill(data.serverClaimed, tag.getIntArray("serverClaimed").orElse(new int[0]));
		fill(data.monthSignedDays, tag.getIntArray("monthSignedDays").orElse(new int[0]));
		List<Claim> pending =
		    SignData.JSON.fromJson(tag.getStringOr("pendingRewards", "[]"), new TypeToken<List<Claim>>() {}.getType());
		data.pending.addAll(pending);
		return data;
	}

	private static int[] toArray(Set<Integer> values) {
		int[] array = new int[values.size()];
		int index = 0;
		for (int value : values) {
			array[index++] = value;
		}
		return array;
	}

	private static void fill(Set<Integer> target, int[] values) {
		for (int value : values) {
			target.add(value);
		}
	}
}
