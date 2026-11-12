package club.sjmc.smp3.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Set;

import com.google.gson.JsonParser;

public record ServerConfig(ZoneId timezone) {
	public static ServerConfig load(Path file) throws IOException {
		if (!Files.exists(file)) {
			Files.createDirectories(file.getParent());
			Files.writeString(file, "{\n  \"timezone\": \"Asia/Shanghai\"\n}\n");
		}
		try {
			return parse(Files.readString(file));
		} catch (RuntimeException e) {
			throw new IllegalArgumentException(file + ": " + e.getMessage(), e);
		}
	}

	public static ServerConfig parse(String text) {
		var root = JsonParser.parseString(text).getAsJsonObject();
		if (!root.keySet().equals(Set.of("timezone")) || !root.get("timezone").isJsonPrimitive() ||
		    !root.getAsJsonPrimitive("timezone").isString()) {
			throw new IllegalArgumentException("timezone: expected a single timezone string");
		}
		try {
			return new ServerConfig(ZoneId.of(root.get("timezone").getAsString()));
		} catch (DateTimeException e) {
			throw new IllegalArgumentException("timezone: " + e.getMessage(), e);
		}
	}
}
