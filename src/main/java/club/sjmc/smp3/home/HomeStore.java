package club.sjmc.smp3.home;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

final class HomeStore {
	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type HOMES = new TypeToken<Map<String, Location>>() {}.getType();
	record Location(String dimension, double x, double y, double z, float yaw, float pitch) {}
	private final Path directory;
	private final Map<UUID, Map<String, Location>> cache = new HashMap<>();

	HomeStore(Path directory) { this.directory = directory; }

	Map<String, Location> homes(UUID owner) throws IOException {
		var homes = cache.get(owner);
		if (homes == null) {
			Path file = directory.resolve(owner + ".json");
			homes = Files.exists(file) ? Map.copyOf(JSON.fromJson(Files.readString(file), HOMES)) : Map.of();
			cache.put(owner, homes);
		}
		return homes;
	}

	void save(UUID owner, Map<String, Location> homes) throws IOException {
		var snapshot = Map.copyOf(homes);
		Files.createDirectories(directory);
		Path file = directory.resolve(owner + ".json");
		Path temporary = Files.createTempFile(directory, owner + "-", ".tmp");
		try {
			Files.writeString(temporary, JSON.toJson(snapshot, HOMES) + "\n");
			Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			cache.put(owner, snapshot);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	void forget(UUID owner) { cache.remove(owner); }
}
