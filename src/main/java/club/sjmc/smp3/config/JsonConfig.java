package club.sjmc.smp3.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import club.sjmc.smp3.economy.Smp3Currency;

public final class JsonConfig {
	private JsonConfig() {}

	public static JsonObject read(Path path, String resource) throws IOException {
		copyDefault(path, resource);
		try (var reader = Files.newBufferedReader(path)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}

	public static void copyDefault(Path path, String resource) throws IOException {
		if (!Files.exists(path)) {
			Files.createDirectories(path.getParent());
			try (var input = JsonConfig.class.getResourceAsStream(resource)) {
				Files.copy(input, path);
			}
		}
	}

	public static void fields(JsonObject object, String required, String optional) {
		var mandatory = new HashSet<>(Arrays.asList(required.split(" ")));
		mandatory.remove("");
		var allowed = new HashSet<>(mandatory);
		allowed.addAll(Arrays.asList(optional.split(" ")));
		allowed.remove("");
		require(object.keySet().containsAll(mandatory), "required fields: " + required);
		require(allowed.containsAll(object.keySet()), "allowed fields: " + allowed);
	}

	public static JsonArray array(JsonObject object, String key) {
		var value = object.get(key);
		require(value != null && value.isJsonArray(), key + ": expected array");
		return value.getAsJsonArray();
	}

	public static JsonObject object(JsonObject object, String key) {
		var value = object.get(key);
		require(value != null && value.isJsonObject(), key + ": expected object");
		return value.getAsJsonObject();
	}

	public static String text(JsonObject object, String key) {
		var value = object.get(key);
		require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() &&
		            !value.getAsString().isBlank(),
		        key + ": expected nonempty string");
		return value.getAsString();
	}

	public static int positive(JsonElement value) {
		require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(),
		        "expected positive integer");
		int number = value.getAsBigDecimal().intValueExact();
		require(number > 0, "expected positive integer");
		return number;
	}

	public static Smp3Currency currency(String id) {
		var currency = Smp3Currency.byId(id);
		require(currency != null && currency.id().toString().equals(id), "unknown currency: " + id);
		return currency;
	}

	public static String title(JsonObject grant) {
		fields(grant, "type id", "");
		return text(grant, "id");
	}

	public static void require(boolean condition, String message) {
		if (!condition)
			throw new IllegalArgumentException(message);
	}
}
