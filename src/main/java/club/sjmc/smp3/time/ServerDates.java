package club.sjmc.smp3.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import club.sjmc.smp3.config.ServerConfig;

public final class ServerDates {
	static Clock clock;

	private ServerDates() {}

	public static void configure(ServerConfig config) { clock = Clock.system(config.timezone()); }

	public static LocalDate today() {
		if (clock == null) {
			throw new IllegalStateException("Server timezone is not configured");
		}
		return LocalDate.now(clock);
	}

	public static ZoneId zone() { return clock.getZone(); }
}
