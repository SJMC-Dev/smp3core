package club.sjmc.smp3.notice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

public final class NoticeEditing {
	public static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private NoticeEditing() {}

	public record
	    Draft(String id, String title, String category, String categoryName, String date, String body, boolean markdown,
		      boolean pinned, @SerializedName("notify") boolean notifyUnread, boolean acknowledgement) {}
	record Snapshot(JsonObject index, NoticeCatalog catalog, String fingerprint) {}

	static Snapshot read(Path path) throws IOException {
		NoticeCatalog catalog = NoticeCatalog.load(path);
		JsonObject index = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
		return new Snapshot(index, catalog, NoticeCatalog.digest(index + "\0" + catalog));
	}

	static Draft draft(Snapshot snapshot, String id) {
		if (id.isEmpty()) {
			var categories = snapshot.catalog.categories();
			var category = categories.isEmpty() ? new NoticeCatalog.Category("news", "公告") : categories.getFirst();
			return new Draft("", "", category.id(), category.name(), LocalDate.now().toString(), "", true, false, true,
			                 false);
		}
		var doc = snapshot.catalog.find(id);
		if (doc == null)
			throw new IllegalArgumentException("公告不存在：" + id);
		var entry = doc.entry();
		String categoryName = snapshot.catalog.categories()
		                          .stream()
		                          .filter(c -> c.id().equals(entry.category()))
		                          .findFirst()
		                          .orElseThrow()
		                          .name();
		return new Draft(id, entry.title(), entry.category(), categoryName, entry.date(), doc.body(), doc.markdown(),
		                 entry.pinned(),
		                 snapshot.index.getAsJsonArray("entries")
		                     .asList()
		                     .stream()
		                     .map(value -> value.getAsJsonObject())
		                     .filter(value -> value.get("id").getAsString().equals(id))
		                     .map(value -> !value.has("notify") || value.get("notify").getAsBoolean())
		                     .findFirst()
		                     .orElseThrow(),
		                 entry.acknowledgement());
	}

	static NoticeCatalog publish(Path path, String fingerprint, String originalId, Draft draft) throws IOException {
		Snapshot current = read(path);
		if (!current.fingerprint.equals(fingerprint))
			throw new IllegalStateException("公告文件已改变，请重新打开编辑器后保存");
		if (!originalId.isEmpty() && !originalId.equals(draft.id))
			throw new IllegalArgumentException("现有公告 ID 不能改变");
		if (draft.body.getBytes(StandardCharsets.UTF_8).length > 32768)
			throw new IllegalArgumentException("正文最多 32768 UTF-8 字节");
		JsonObject index = current.index.deepCopy();
		var entries = index.getAsJsonArray("entries");
		int position = -1;
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).getAsJsonObject().get("id").getAsString().equals(draft.id))
				position = i;
		}
		if (originalId.isEmpty() && position >= 0)
			throw new IllegalArgumentException("公告 ID 已存在");
		var categories = index.getAsJsonArray("categories");
		boolean known = false;
		for (var value : categories) {
			var category = value.getAsJsonObject();
			if (category.get("id").getAsString().equals(draft.category)) {
				known = true;
				if (!category.get("name").getAsString().equals(draft.categoryName))
					throw new IllegalArgumentException("分类 ID 已存在，请使用原分类名称");
			}
		}
		if (!known) {
			JsonObject category = new JsonObject();
			category.addProperty("id", draft.category);
			category.addProperty("name", draft.categoryName);
			categories.add(category);
		}
		Path root = path.getParent().resolve("notices").toRealPath();
		Path managed = root.resolve("managed");
		Files.createDirectories(managed);
		if (!managed.toRealPath().startsWith(root))
			throw new IllegalArgumentException("managed 目录必须位于 notices 内");
		Path body = Files.createTempFile(managed, "notice-", draft.markdown ? ".md" : ".txt");
		boolean committed = false;
		try {
			Files.writeString(body, draft.body);
			JsonObject entry = new JsonObject();
			entry.addProperty("id", draft.id);
			entry.addProperty("title", draft.title);
			entry.addProperty("category", draft.category);
			if (!draft.date.isBlank())
				entry.addProperty("date", draft.date);
			entry.addProperty("file", "managed/" + body.getFileName());
			entry.addProperty("pinned", draft.pinned);
			entry.addProperty("notify", draft.notifyUnread);
			entry.addProperty("acknowledgement", draft.acknowledgement);
			if (position < 0)
				entries.add(entry);
			else
				entries.set(position, entry);
			NoticeCatalog next = commitIndex(path, fingerprint, index);
			committed = true;
			return next;
		} finally {
			if (!committed)
				Files.deleteIfExists(body);
		}
	}

	static NoticeCatalog delete(Path path, String fingerprint, String id) throws IOException {
		Snapshot current = read(path);
		var entries = current.index.getAsJsonArray("entries");
		if (!entries.asList().removeIf(value -> value.getAsJsonObject().get("id").getAsString().equals(id)))
			throw new IllegalArgumentException("公告不存在：" + id);
		return commitIndex(path, fingerprint, current.index);
	}

	private static NoticeCatalog commitIndex(Path path, String fingerprint, JsonObject index) throws IOException {
		Path temporary = Files.createTempFile(path.getParent(), "notices-", ".json.tmp");
		try {
			Files.writeString(temporary, JSON.toJson(index));
			NoticeCatalog next = NoticeCatalog.load(temporary);
			if (!read(path).fingerprint.equals(fingerprint))
				throw new IllegalStateException("公告文件已改变，请重新打开编辑器后操作");
			Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			return next;
		} finally {
			Files.deleteIfExists(temporary);
		}
	}
}
