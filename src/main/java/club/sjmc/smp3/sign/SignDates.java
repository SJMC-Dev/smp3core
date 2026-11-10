package club.sjmc.smp3.sign;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 签到日期工具：统一使用服务器本地时区 Asia/Shanghai。
 */
public final class SignDates {

	public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

	private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

	private SignDates() {
	}

	public static LocalDate today() {
		return LocalDate.now(ZONE);
	}

	public static String monthKey(LocalDate date) {
		return date.format(MONTH_FORMAT);
	}

	/** 惰性跨月重置：月份变化时清空本月累计与已领取档位。 */
	public static void rotateMonthIfNeeded(PlayerSignData data, LocalDate today) {
		String key = monthKey(today);
		if (!key.equals(data.monthKey())) {
			data.setMonthKey(key);
			data.setMonthTotal(0);
			data.clearMonthClaimed();
			data.clearMonthSigned();
		}
	}
}
