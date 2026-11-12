package club.sjmc.smp3.time;

import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import club.sjmc.smp3.config.ServerConfig;

public final class DateChecks {
	public static void run() throws Exception {
		var previous = ServerDates.clock;
		var dir = Files.createTempDirectory("smp3-config-");
		try {
			var file = dir.resolve("server.json");
			assert ServerConfig.load(file).timezone().equals(ZoneId.of("Asia/Shanghai"));
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
			ServerDates.clock = Clock.fixed(Instant.parse("2026-03-28T23:30:00Z"), ZoneId.of("Europe/London"));
			assert ServerDates.today().equals(LocalDate.of(2026, 3, 28));
			ServerDates.clock = Clock.fixed(Instant.parse("2026-03-29T22:30:00Z"), ZoneId.of("Europe/London"));
			assert ServerDates.today().equals(LocalDate.of(2026, 3, 29));
			for (String instant : new String[] {"2026-10-25T00:30:00Z", "2026-10-25T01:30:00Z"}) {
				ServerDates.clock = Clock.fixed(Instant.parse(instant), ZoneId.of("Europe/London"));
				assert ServerDates.today().equals(LocalDate.of(2026, 10, 25));
			}
		} finally {
			ServerDates.clock = previous;
			Files.deleteIfExists(dir.resolve("server.json"));
			Files.delete(dir);
		}
	}
}
