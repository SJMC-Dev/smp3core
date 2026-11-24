package club.sjmc.smp3.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public record ServerConfig(ZoneId timezone, LocalTime resetTime, int autoSaveIntervalSeconds, int playerStackLimit,
                           int homeLimit) {
	public ServerConfig(ZoneId timezone, LocalTime resetTime) { this(timezone, resetTime, 300, 3, 2); }

	public static ServerConfig load(Path file) throws IOException {
		JsonConfig.copyDefault(file, "/server.json");
		try {
			return parse(Files.readString(file));
		} catch (RuntimeException e) {
			throw new IllegalArgumentException(file + ": " + e.getMessage(), e);
		}
	}

	public static ServerConfig parse(String text) {
		var root = JsonParser.parseString(text).getAsJsonObject();
		if (!Set.of("timezone", "resetTime", "autoSaveIntervalSeconds", "playerStackLimit", "homeLimit")
		         .containsAll(root.keySet()) ||
		    !root.has("timezone") || !root.get("timezone").isJsonPrimitive() ||
		    !root.getAsJsonPrimitive("timezone").isString()) {
			throw new IllegalArgumentException("expected timezone, optional resetTime (HH:mm), "
			                                   + "autoSaveIntervalSeconds, playerStackLimit and homeLimit");
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
		return new ServerConfig(timezone, resetTime, positiveInt(root, "autoSaveIntervalSeconds", 300),
		                        positiveInt(root, "playerStackLimit", 3), positiveInt(root, "homeLimit", 2));
	}

	private static int positiveInt(JsonObject root, String key, int fallback) {
		if (!root.has(key))
			return fallback;
		var value = root.get(key);
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
			throw new IllegalArgumentException(key + ": expected a positive integer");
		int number;
		try {
			number = value.getAsBigDecimal().intValueExact();
		} catch (ArithmeticException error) {
			throw new IllegalArgumentException(key + ": expected a positive integer", error);
		}
		if (number < 1)
			throw new IllegalArgumentException(key + ": expected a positive integer");
		return number;
	}
}
