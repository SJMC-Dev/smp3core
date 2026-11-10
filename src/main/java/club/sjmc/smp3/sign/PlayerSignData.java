package club.sjmc.smp3.sign;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;

/**
 * 单个玩家的签到数据。
 *
 * <p>日期统一用 {@link java.time.LocalDate#toEpochDay()} 保存，避免时区/格式问题。</p>
 */
public final class PlayerSignData {

	/** 从未签到过的哨兵值。 */
	public static final long NEVER_SIGNED = Long.MIN_VALUE;

	private long lastSignEpochDay = NEVER_SIGNED;
	private int streak;
	private String monthKey = "";
	private int monthTotal;
	private int serverTotal;
	private final Set<Integer> monthClaimed = new HashSet<>();
	private final Set<Integer> serverClaimed = new HashSet<>();
	private final Set<Integer> monthSignedDays = new HashSet<>();

	public long lastSignEpochDay() {
		return lastSignEpochDay;
	}

	public void setLastSignEpochDay(long epochDay) {
		this.lastSignEpochDay = epochDay;
	}

	public int streak() {
		return streak;
	}

	public void setStreak(int streak) {
		this.streak = streak;
	}

	public String monthKey() {
		return monthKey;
	}

	public void setMonthKey(String monthKey) {
		this.monthKey = monthKey;
	}

	public int monthTotal() {
		return monthTotal;
	}

	public void setMonthTotal(int monthTotal) {
		this.monthTotal = monthTotal;
	}

	public int serverTotal() {
		return serverTotal;
	}

	public void setServerTotal(int serverTotal) {
		this.serverTotal = serverTotal;
	}

	public boolean isMonthClaimed(int days) {
		return monthClaimed.contains(days);
	}

	public void markMonthClaimed(int days) {
		monthClaimed.add(days);
	}

	/** 跨月时清空当月已领取档位。 */
	public void clearMonthClaimed() {
		monthClaimed.clear();
	}

	/** 本月某一天是否已签到（用于界面日历）。 */
	public boolean isMonthSigned(int dayOfMonth) {
		return monthSignedDays.contains(dayOfMonth);
	}

	public void markMonthSigned(int dayOfMonth) {
		monthSignedDays.add(dayOfMonth);
	}

	/** 跨月时清空本月签到记录。 */
	public void clearMonthSigned() {
		monthSignedDays.clear();
	}

	public boolean isServerClaimed(int days) {
		return serverClaimed.contains(days);
	}

	public void markServerClaimed(int days) {
		serverClaimed.add(days);
	}

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
