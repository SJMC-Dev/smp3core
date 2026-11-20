package club.sjmc.smp3.shop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class ShopRotation {
	public record Pool(String id, int count, List<ShopEntry> entries) {}
	public record Definition(boolean enabled, ShopPeriod period, List<Pool> pools) {}
	record Selection(ShopCatalog catalog, JsonObject state) {}
	private final Path path;
	private JsonObject state;

	ShopRotation(Path path) throws IOException {
		this.path = path;
		state =
		    Files.exists(path) ? JsonParser.parseString(Files.readString(path)).getAsJsonObject() : new JsonObject();
	}
	Selection select(ShopCatalog source, LocalDate today, RandomGenerator random) {
		var next = state.deepCopy();
		var entries = new HashMap<String, ShopEntry>();
		var refreshes = new HashMap<String, LocalDate>();
		var nodes = source.shops().stream().map(node -> select(node, today, random, next, entries, refreshes)).toList();
		next.entrySet().removeIf(node -> {
			var pools = node.getValue().getAsJsonObject();
			pools.entrySet().removeIf(
			    pool -> !today.isBefore(LocalDate.parse(pool.getValue().getAsJsonObject().get("end").getAsString())));
			return pools.isEmpty();
		});
		return new Selection(new ShopCatalog(nodes, Map.copyOf(entries), Map.copyOf(refreshes)), next);
	}
	private static ShopNode select(ShopNode node, LocalDate today, RandomGenerator random, JsonObject state,
	                               Map<String, ShopEntry> entries, Map<String, LocalDate> refreshes) {
		var active = new ArrayList<>(node.entries());
		var rotation = node.rotation();
		if (rotation != null && rotation.enabled()) {
			var stored = state.has(node.id()) ? state.getAsJsonObject(node.id()) : new JsonObject();
			for (var pool : rotation.pools()) {
				var value = stored.has(pool.id()) ? stored.getAsJsonObject(pool.id()) : null;
				if (value == null || !today.isBefore(LocalDate.parse(value.get("end").getAsString()))) {
					value = new JsonObject();
					value.addProperty("end", rotation.period().end(today).toString());
					var selected = new JsonArray();
					draw(pool, random).forEach(selected::add);
					value.add("entries", selected);
					stored.add(pool.id(), value);
				}
				var selected = new HashSet<String>();
				value.getAsJsonArray("entries").forEach(id -> selected.add(id.getAsString()));
				LocalDate end = LocalDate.parse(value.get("end").getAsString());
				for (var entry : pool.entries())
					if (selected.contains(entry.id())) {
						active.add(entry);
						refreshes.put(entry.id(), end);
					}
			}
			state.add(node.id(), stored);
		}
		active.forEach(entry -> entries.put(entry.id(), entry));
		var children =
		    node.children().stream().map(child -> select(child, today, random, state, entries, refreshes)).toList();
		return new ShopNode(node.id(), node.name(), node.icon(), children, List.copyOf(active), rotation);
	}
	static List<String> draw(Pool pool, RandomGenerator random) {
		var remaining = new ArrayList<>(pool.entries());
		var selected = new ArrayList<String>();
		long total = remaining.stream().mapToLong(ShopEntry::weight).sum();
		for (int i = 0; i < pool.count(); i++) {
			long ticket = random.nextLong(total);
			for (int j = 0; j < remaining.size(); j++) {
				var entry = remaining.get(j);
				ticket -= entry.weight();
				if (ticket < 0) {
					selected.add(entry.id());
					remaining.remove(j);
					total -= entry.weight();
					break;
				}
			}
		}
		return List.copyOf(selected);
	}
	void commit(Selection selection) throws IOException {
		if (state.equals(selection.state()))
			return;
		Files.createDirectories(path.getParent());
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			Files.writeString(temporary, selection.state().toString());
			Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			state = selection.state();
		} finally {
			Files.deleteIfExists(temporary);
		}
	}
}
