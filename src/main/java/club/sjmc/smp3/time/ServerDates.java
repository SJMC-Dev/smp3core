package club.sjmc.smp3.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

import club.sjmc.smp3.config.ServerConfig;

public final class ServerDates {
	static Clock clock;
	static LocalTime resetTime = LocalTime.MIDNIGHT;

	private ServerDates() {}

	public static void configure(ServerConfig config) {
		clock = Clock.system(config.timezone());
		resetTime = config.resetTime();
	}

	public static LocalDate today() { return dayAt(ZonedDateTime.now(clock)); }

	public static ZonedDateTime nextReset() {
		ZonedDateTime now = ZonedDateTime.now(clock);
		return resetAt(dayAt(now).plusDays(1));
	}

	private static LocalDate dayAt(ZonedDateTime now) {
		LocalDate date = now.toLocalDate();
		return now.isBefore(resetAt(date)) ? date.minusDays(1) : date;
	}

	public static ZonedDateTime resetAt(LocalDate date) {
		var local = date.atTime(resetTime);
		var zone = clock.getZone();
		var transition = zone.getRules().getTransition(local);
		// Skip nonexistent times to the end of the gap; use the first occurrence of repeated times.
		if (transition != null && transition.isGap())
			return transition.getDateTimeAfter().atZone(zone);
		return local.atZone(zone);
	}
}
