package club.sjmc.smp3.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

import com.google.gson.JsonParser;

public record ServerConfig(ZoneId timezone, LocalTime resetTime) {
	public static ServerConfig load(Path file) throws IOException {
		if (!Files.exists(file)) {
			Files.createDirectories(file.getParent());
			Files.writeString(file, "{\n  \"timezone\": \"Asia/Shanghai\",\n  \"resetTime\": \"00:00\"\n}\n");
		}
		try {
			return parse(Files.readString(file));
		} catch (RuntimeException e) {
			throw new IllegalArgumentException(file + ": " + e.getMessage(), e);
		}
	}

	public static ServerConfig parse(String text) {
		var root = JsonParser.parseString(text).getAsJsonObject();
		if (!Set.of("timezone", "resetTime").containsAll(root.keySet()) || !root.has("timezone") ||
		    !root.get("timezone").isJsonPrimitive() || !root.getAsJsonPrimitive("timezone").isString()) {
			throw new IllegalArgumentException("expected timezone string and optional resetTime (HH:mm)");
		}
		ZoneId timezone;
		try {
			timezone = ZoneId.of(root.get("timezone").getAsString());
		} catch (DateTimeException e) {
			throw new IllegalArgumentException("timezone: " + e.getMessage(), e);
		}
		LocalTime resetTime = LocalTime.MIDNIGHT;
		if (root.has("resetTime")) {
			var value = root.get("resetTime");
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() ||
			    !value.getAsString().matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))
				throw new IllegalArgumentException("resetTime: expected HH:mm between 00:00 and 23:59");
			resetTime = LocalTime.parse(value.getAsString());
		}
		return new ServerConfig(timezone, resetTime);
	}
}
