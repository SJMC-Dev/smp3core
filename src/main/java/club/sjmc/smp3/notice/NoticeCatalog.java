package club.sjmc.smp3.notice;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import club.sjmc.smp3.config.JsonConfig;

public record NoticeCatalog(String title, List<Category> categories, List<Document> documents) {
	private static final int ACKNOWLEDGEMENT = 1, NOTIFY = 2, PINNED = 4, READ = 8;

	public static final NoticeCatalog EMPTY = new NoticeCatalog("SMP3 服务器手册", List.of(), List.of());
	public record Category(String id, String name) {}
	public record Entry(String id, String category, String title, String date, String revision, int flags) {
		public boolean acknowledgement() { return (flags & ACKNOWLEDGEMENT) != 0; }
		public boolean notifyUnread() { return (flags & (ACKNOWLEDGEMENT | NOTIFY)) != 0; }
		public boolean pinned() { return (flags & PINNED) != 0; }
		public boolean read() { return (flags & READ) != 0; }
		public Entry withRead(boolean read) {
			return new Entry(id, category, title, date, revision, (flags & ~READ) | (read ? READ : 0));
		}
	}
	public record Document(Entry entry, String body, boolean markdown) {}

	public Document find(String id) {
		return documents.stream().filter(doc -> doc.entry.id.equals(id)).findFirst().orElse(null);
	}

	public static NoticeCatalog load(Path index) throws IOException {
		Files.createDirectories(index.getParent());
		Path root = index.getParent().resolve("notices");
		Files.createDirectories(root);
		root = root.toRealPath();
		JsonConfig.copyDefault(index, "/notices.json");
		JsonObject json = JsonParser.parseString(read(index, 256 * 1024)).getAsJsonObject();
		if (!json.has("categories") || !json.get("categories").isJsonArray() || !json.has("entries") ||
		    !json.get("entries").isJsonArray())
			throw new IllegalArgumentException("notices.json: categories and entries must be arrays");
		if (json.getAsJsonArray("categories").size() > 16 || json.getAsJsonArray("entries").size() > 128)
			throw new IllegalArgumentException("notices.json: maximum 16 categories and 128 entries");
		String title = json.has("title") ? text(json, "title", 128) : EMPTY.title;
		var categories = new ArrayList<Category>();
		var categoryIds = new HashSet<String>();
		for (var value : json.getAsJsonArray("categories")) {
			var category = value.getAsJsonObject();
			String id = text(category, "id", 64);
			if (id.equals("all") || !categoryIds.add(id))
				throw new IllegalArgumentException("categories: duplicate or reserved id " + id);
			categories.add(new Category(id, text(category, "name", 32)));
		}
		var documents = new ArrayList<Document>();
		var ids = new HashSet<String>();
		for (var value : json.getAsJsonArray("entries")) {
			var entry = value.getAsJsonObject();
			String id = text(entry, "id", 64);
			try {
				if (id.equals("*") || !ids.add(id))
					throw new IllegalArgumentException("duplicate or reserved id");
				String category = text(entry, "category", 64);
				if (!categoryIds.contains(category))
					throw new IllegalArgumentException("unknown category " + category);
				String name = text(entry, "title", 128);
				String date = entry.has("date") ? text(entry, "date", 10) : "";
				if (!date.isEmpty())
					LocalDate.parse(date);
				String file = text(entry, "file", 256);
				boolean markdown = file.endsWith(".md");
				Path relative = Path.of(file);
				if (relative.isAbsolute() || !(markdown || file.endsWith(".txt")) ||
				    !root.resolve(relative).normalize().startsWith(root))
					throw new IllegalArgumentException("invalid document path " + file);
				Path path = root.resolve(relative).toRealPath();
				if (!path.startsWith(root))
					throw new IllegalArgumentException("document outside notices directory: " + file);
				String body = read(path, 32768).replace("\r\n", "\n").replace('\r', '\n');
				boolean acknowledgement = flag(entry, "acknowledgement", false);
				int flags = (acknowledgement ? ACKNOWLEDGEMENT : 0) | (flag(entry, "notify", true) ? NOTIFY : 0) |
				            (flag(entry, "pinned", false) ? PINNED : 0);
				String revision = digest(name + "\0" + markdown + "\0" + acknowledgement + "\0" + body);
				documents.add(new Document(new Entry(id, category, name, date, revision, flags), body, markdown));
			} catch (IOException | RuntimeException e) {
				throw new IllegalArgumentException("entries[" + id + "]: " + e.getMessage(), e);
			}
		}
		documents.sort(Comparator.<Document, Boolean>comparing(doc -> !doc.entry.pinned())
		                   .thenComparing(doc -> doc.entry.pinned() ? doc.entry.date : "", Comparator.reverseOrder()));
		return new NoticeCatalog(title, List.copyOf(categories), List.copyOf(documents));
	}

	private static String read(Path path, int limit) throws IOException {
		try (var input = Files.newInputStream(path)) {
			byte[] bytes = input.readNBytes(limit + 1);
			if (bytes.length > limit)
				throw new IllegalArgumentException(path + ": exceeds " + limit + " UTF-8 bytes");
			return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
		}
	}

	private static String text(JsonObject object, String key, int limit) {
		var value = object.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
			throw new IllegalArgumentException(key + ": expected string");
		String text = value.getAsString();
		if (text.isBlank() || text.length() > limit)
			throw new IllegalArgumentException(key + ": expected 1–" + limit + " characters");
		return text;
	}

	private static boolean flag(JsonObject object, String key, boolean fallback) {
		if (!object.has(key))
			return fallback;
		var value = object.get(key);
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
			throw new IllegalArgumentException(key + ": expected boolean");
		return value.getAsBoolean();
	}

	static String digest(String text) {
		try {
			return HexFormat.of().formatHex(
			    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
