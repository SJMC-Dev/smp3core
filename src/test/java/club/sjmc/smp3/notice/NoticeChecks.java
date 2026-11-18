package club.sjmc.smp3.notice;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

public final class NoticeChecks {
	public static void run() throws Exception {
		Path root = Files.createTempDirectory("smp3-notices");
		try {
			Path index = root.resolve("notices.json");
			assert NoticeCatalog.load(index).documents().isEmpty();
			Path body = root.resolve("notices/rules.md");
			Files.writeString(body, "# Rules\r\nRespect others.");
			var json = JsonParser.parseString("""
                {"title":"Community Handbook","categories":[{"id":"rules","name":"Rules"}],"entries":[
                  {"id":"normal","category":"rules","title":"Help","file":"rules.md"},
                  {"id":"old","category":"rules","title":"Rules","file":"rules.md","date":"2026-01-01","pinned":true},
                  {"id":"new","category":"rules","title":"Rules","file":"rules.md","date":"2026-11-17","pinned":true,"acknowledgement":true,"notify":false}
                ]}
                """).getAsJsonObject();
			Files.writeString(index, json.toString());
			var catalog = NoticeCatalog.load(index);
			assert catalog.title().equals("Community Handbook");
			assert catalog.documents()
			    .stream()
			    .map(doc -> doc.entry().id())
			    .toList()
			    .equals(List.of("new", "old", "normal"));
			var required = catalog.find("new").entry();
			assert required.notifyUnread();
			var data = new NoticeData();
			UUID player = UUID.randomUUID();
			assert !data.complete(player, required, required.revision(), false);
			assert !data.complete(player, required, "stale", true);
			assert data.complete(player, required, required.revision(), true);
			var encoded = NoticeData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
			data = NoticeData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
			assert data.read(player, required) && !data.read(UUID.randomUUID(), required);
			var entry = json.getAsJsonArray("entries").get(2).getAsJsonObject();
			entry.addProperty("pinned", false);
			entry.addProperty("date", "2026-11-18");
			Files.writeString(index, json.toString());
			Files.writeString(body, "# Rules\nRespect others.");
			assert data.read(player, NoticeCatalog.load(index).find("new").entry());
			Files.writeString(body, "Revised rules");
			assert !data.read(player, NoticeCatalog.load(index).find("new").entry());
			for (String file : List.of("../outside.md", "/outside.md", "missing.md", "rules.html")) {
				entry.addProperty("file", file);
				Files.writeString(index, json.toString());
				reject(index);
			}
			Files.writeString(root.resolve("outside.md"), "outside");
			Files.createSymbolicLink(root.resolve("notices/link.md"), root.resolve("outside.md"));
			entry.addProperty("file", "link.md");
			Files.writeString(index, json.toString());
			reject(index);
			entry.addProperty("file", "rules.md");
			Files.writeString(index, json.toString());
			Files.writeString(body, "中".repeat(11000));
			reject(index);
			Files.writeString(index, "{\"categories\":[],\"entries\":[]}");
			var snapshot = NoticeEditing.read(index);
			var draft = new NoticeEditing.Draft("first", "First notice", "news", "News", "2026-11-18", "# Hello\n世界",
			                                    true, true, false, true);
			var published = NoticeEditing.publish(index, snapshot.fingerprint(), "", draft);
			assert published.find("first").body().equals(draft.body());
			assert published.categories().size() == 1 && published.find("first").entry().pinned();
			snapshot = NoticeEditing.read(index);
			assert NoticeEditing.draft(snapshot, "first").equals(draft);
			String before = Files.readString(index);
			var invalid = new NoticeEditing.Draft("first", "First notice", "news", "News", "bad-date", "New body", true,
			                                      false, true, false);
			try {
				NoticeEditing.publish(index, snapshot.fingerprint(), "first", invalid);
				throw new AssertionError("Invalid publication accepted");
			} catch (IllegalArgumentException expected) {
				assert Files.readString(index).equals(before);
				assert NoticeCatalog.load(index).find("first").body().equals(draft.body());
			}
			var external = snapshot.index().deepCopy();
			external.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("pinned", false);
			Files.writeString(index, external.toString());
			try {
				NoticeEditing.publish(index, snapshot.fingerprint(), "first", draft);
				throw new AssertionError("Concurrent metadata edit overwritten");
			} catch (IllegalStateException expected) {
				assert !NoticeCatalog.load(index).find("first").entry().pinned();
			}
			try {
				NoticeEditing.delete(index, snapshot.fingerprint(), "first");
				throw new AssertionError("Stale editor deleted an updated announcement");
			} catch (IllegalStateException expected) {
				assert Files.readString(index).equals(external.toString());
			}
			var shared = external.getAsJsonArray("entries").get(0).getAsJsonObject().deepCopy();
			shared.addProperty("id", "shared");
			external.getAsJsonArray("entries").add(shared);
			Files.writeString(index, external.toString());
			snapshot = NoticeEditing.read(index);
			Path source = root.resolve("notices").resolve(shared.get("file").getAsString());
			var remaining = NoticeEditing.delete(index, snapshot.fingerprint(), "first");
			assert remaining.find("first") == null && remaining.find("shared") != null;
			assert NoticeCatalog.load(index).find("first") == null && Files.readString(source).equals(draft.body());
			assert remaining.categories().equals(snapshot.catalog().categories());
			snapshot = NoticeEditing.read(index);
			NoticeEditing.delete(index, snapshot.fingerprint(), "shared");
			assert NoticeCatalog.load(index).documents().isEmpty() && Files.exists(source);

		} finally {
			try (var paths = Files.walk(root)) {
				for (var path : paths.sorted(Comparator.reverseOrder()).toList())
					Files.delete(path);
			}
		}
	}

	private static void reject(Path index) throws Exception {
		try {
			NoticeCatalog.load(index);
			throw new AssertionError("Invalid notice accepted");
		} catch (IllegalArgumentException expected) {
			assert expected.getMessage().contains("entries[");
		}
	}
}
