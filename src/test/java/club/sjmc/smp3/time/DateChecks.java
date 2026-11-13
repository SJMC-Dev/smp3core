package club.sjmc.smp3.time;

import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import club.sjmc.smp3.config.ServerConfig;

public final class DateChecks {
	public static void run() throws Exception {
		var previous = ServerDates.clock;
		var previousResetTime = ServerDates.resetTime;
		var dir = Files.createTempDirectory("smp3-config-");
		try {
			var file = dir.resolve("server.json");
			var defaults = ServerConfig.load(file);
			assert defaults.timezone().equals(ZoneId.of("Asia/Shanghai"));
			assert defaults.resetTime().equals(LocalTime.MIDNIGHT);
			assert ServerConfig.parse("{\"timezone\":\"UTC\"}").resetTime().equals(LocalTime.MIDNIGHT);
			ServerDates.configure(defaults);
			for (String invalid : new String[] {"{}", "{\"timezone\":1}", "{\"timezone\":\"invalid\"}",
			                                    "{\"timezone\":\"UTC\",\"typo\":true}"}) {
				Files.writeString(file, invalid);
				try {
					ServerConfig.load(file);
					throw new AssertionError("Invalid configuration accepted");
				} catch (IllegalArgumentException expected) {
				}
				assert Files.readString(file).equals(invalid);
			}
			for (String invalid : new String[] {"null", "4", "\"4:00\"", "\"24:00\"", "\"04:60\"", "\"04:00:00\""}) {
				try {
					ServerConfig.parse("{\"timezone\":\"UTC\",\"resetTime\":" + invalid + "}");
					throw new AssertionError("Invalid reset time accepted: " + invalid);
				} catch (IllegalArgumentException expected) {
				}
			}
			ServerDates.clock = Clock.fixed(Instant.parse("2026-03-28T23:30:00Z"), ZoneId.of("Europe/London"));
			assert ServerDates.today().equals(LocalDate.of(2026, 3, 28));
			ServerDates.clock = Clock.fixed(Instant.parse("2026-03-29T22:30:00Z"), ZoneId.of("Europe/London"));
			assert ServerDates.today().equals(LocalDate.of(2026, 3, 29));
			for (String instant : new String[] {"2026-10-25T00:30:00Z", "2026-10-25T01:30:00Z"}) {
				ServerDates.clock = Clock.fixed(Instant.parse(instant), ZoneId.of("Europe/London"));
				assert ServerDates.today().equals(LocalDate.of(2026, 10, 25));
			}
			ServerDates.configure(ServerConfig.parse("{\"timezone\":\"Asia/Shanghai\",\"resetTime\":\"04:00\"}"));
			Instant reset = Instant.parse("2030-01-31T20:00:00Z");
			for (int seconds : new int[] {-1, 0, 1}) {
				ServerDates.clock = Clock.fixed(reset.plusSeconds(seconds), ZoneId.of("Asia/Shanghai"));
				assert ServerDates.today().equals(seconds < 0 ? LocalDate.of(2030, 1, 31) : LocalDate.of(2030, 2, 1));
				assert ServerDates.nextReset().toInstant().equals(seconds < 0 ? reset : reset.plusSeconds(86400));
			}
			ServerDates.resetTime = LocalTime.of(1, 30);
			Instant spring = Instant.parse("2026-03-29T01:00:00Z");
			ServerDates.clock = Clock.fixed(spring.minusSeconds(1), ZoneId.of("Europe/London"));
			assert ServerDates.today().equals(LocalDate.of(2026, 3, 28));
			assert ServerDates.nextReset().toInstant().equals(spring);
			ServerDates.clock = Clock.fixed(spring, ZoneId.of("Europe/London"));
			assert ServerDates.today().equals(LocalDate.of(2026, 3, 29));
			Instant autumn = Instant.parse("2026-10-25T00:30:00Z");
			ServerDates.clock = Clock.fixed(autumn.minusSeconds(1), ZoneId.of("Europe/London"));
			assert ServerDates.today().equals(LocalDate.of(2026, 10, 24));
			assert ServerDates.nextReset().toInstant().equals(autumn);
			for (int minutes : new int[] {0, 30, 60}) {
				ServerDates.clock = Clock.fixed(autumn.plusSeconds(minutes * 60), ZoneId.of("Europe/London"));
				assert ServerDates.today().equals(LocalDate.of(2026, 10, 25));
				assert ServerDates.nextReset().toInstant().equals(Instant.parse("2026-10-26T01:30:00Z"));
			}
		} finally {
			ServerDates.clock = previous;
			ServerDates.resetTime = previousResetTime;
			Files.deleteIfExists(dir.resolve("server.json"));
			Files.delete(dir);
		}
	}
}
