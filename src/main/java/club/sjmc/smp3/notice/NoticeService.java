package club.sjmc.smp3.notice;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.economy.BalanceSync;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class NoticeService implements AutoCloseable {
	private final MinecraftServer server;
	private final Path path;
	private final NoticeData data;
	private final ExecutorService loader =
	    Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("smp3-notices").factory());
	private final Set<UUID> pendingJoins = new HashSet<>();
	private final Map<UUID, Map<String, String>> delivered = new HashMap<>();
	private static final class EditSession {
		final String token = UUID.randomUUID().toString();
		final String id, fingerprint;
		final StringBuilder upload = new StringBuilder();
		int part, total;
		EditSession(String id, String fingerprint) {
			this.id = id;
			this.fingerprint = fingerprint;
		}
		void clearUpload() {
			upload.setLength(0);
			part = total = 0;
		}
	}
	private final Map<UUID, EditSession> editors = new HashMap<>();
	private NoticeCatalog catalog = NoticeCatalog.EMPTY;
	private boolean loading, ready, closed;

	NoticeService(MinecraftServer server, Path path) {
		this.server = server;
		this.path = path;
		data = NoticeData.get(server);
	}

	public boolean reload(Consumer<String> result) {
		if (loading)
			return false;
		loading = true;
		loader.execute(() -> {
			NoticeCatalog loaded = null;
			String error = null;
			try {
				loaded = NoticeCatalog.load(path);
			} catch (Exception e) {
				error = e.getMessage();
				Smp3Core.LOGGER.error("[SMP3] 公告加载失败: {}", path, e);
			}
			NoticeCatalog next = loaded;
			String failure = error;
			server.execute(() -> {
				if (closed)
					return;
				loading = false;
				ready = true;
				applyCatalog(next);
				result.accept(next == null ? "公告加载失败，保留原内容：" + failure
				                           : "已加载 " + catalog.documents().size() + " 篇文档");
			});
		});
		return true;
	}

	private void applyCatalog(NoticeCatalog next) {
		if (next != null) {
			catalog = next;
			delivered.clear();
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean joined = pendingJoins.remove(player.getUUID());
			if (next != null || joined)
				sync(player, joined ? prompt(player) : "");
		}
	}

	public List<String> ids() { return catalog.documents().stream().map(doc -> doc.entry().id()).toList(); }

	public void edit(ServerPlayer player, String id) {
		if (!NoticeModule.mayEdit(player))
			return;
		if (!ServerPlayNetworking.canSend(player, NoticePayloads.Editor.TYPE)) {
			player.sendSystemMessage(Component.literal("请安装 SMP3 客户端以编辑公告"));
			return;
		}
		if (loading) {
			player.sendSystemMessage(Component.literal("公告正在加载或发布，请稍候"));
			return;
		}
		loading = true;
		loader.execute(() -> {
			try {
				var snapshot = NoticeEditing.read(path);
				String draft = NoticeEditing.JSON.toJson(NoticeEditing.draft(snapshot, id));
				var session = new EditSession(id, snapshot.fingerprint());
				server.execute(() -> {
					loading = false;
					if (closed || player.hasDisconnected() || !NoticeModule.mayEdit(player))
						return;
					editors.put(player.getUUID(), session);
					ServerPlayNetworking.send(
					    player, new NoticePayloads.Editor(session.token, draft, "", snapshot.catalog().categories()));
				});
			} catch (Exception error) {
				server.execute(() -> {
					loading = false;
					if (!closed && !player.hasDisconnected())
						player.sendSystemMessage(Component.literal("打开编辑器失败：" + error.getMessage()));
				});
			}
		});
	}

	private EditSession editSession(ServerPlayer player, String token) {
		var session = editors.get(player.getUUID());
		if (!NoticeModule.mayEdit(player)) {
			editors.remove(player.getUUID());
			editorResult(player, token, "权限不足，公告未修改");
			return null;
		}
		if (session == null || !session.token.equals(token)) {
			editorResult(player, token, "编辑会话已失效，请重新打开编辑器");
			return null;
		}
		if (loading) {
			session.clearUpload();
			editorResult(player, token, "公告正在加载或更新，请稍后重试");
			return null;
		}
		return session;
	}

	void delete(ServerPlayer player, String token) {
		var session = editSession(player, token);
		if (session == null)
			return;
		if (session.id.isEmpty()) {
			editorResult(player, token, "新建草稿尚未发布");
			return;
		}
		session.clearUpload();
		applyEdit(player, session, "", true);
	}

	void publish(ServerPlayer player, NoticePayloads.Publish request) {
		var session = editSession(player, request.session());
		if (session == null)
			return;
		if (request.part() == 0) {
			session.clearUpload();
			session.total = request.total();
		}
		if (request.total() < 1 || request.total() > 32 || request.total() != session.total ||
		    request.part() != session.part || session.upload.length() + request.text().length() > 262144) {
			session.clearUpload();
			editorResult(player, session.token, "发布内容分段无效");
			return;
		}
		session.upload.append(request.text());
		if (++session.part != session.total)
			return;
		String text = session.upload.toString();
		session.clearUpload();
		applyEdit(player, session, text, false);
	}

	private void applyEdit(ServerPlayer player, EditSession session, String text, boolean deleting) {
		loading = true;
		loader.execute(() -> {
			NoticeCatalog next = null;
			String failure = "";
			try {
				next = deleting ? NoticeEditing.delete(path, session.fingerprint, session.id)
				                : NoticeEditing.publish(path, session.fingerprint, session.id,
				                                        NoticeEditing.JSON.fromJson(text, NoticeEditing.Draft.class));
			} catch (Exception error) {
				failure = (deleting ? "删除失败：" : "发布失败：") + error.getMessage();
				Smp3Core.LOGGER.warn("Notice update failed player={}: {}", player.getUUID(), error.toString());
			}
			var published = next;
			String message = failure;
			server.execute(() -> {
				loading = false;
				if (closed)
					return;
				if (published != null) {
					applyCatalog(published);
					editors.remove(player.getUUID());
					if (!player.hasDisconnected())
						player.sendSystemMessage(Component.literal(deleting ? "公告已删除" : "公告已发布"));
				}
				if (!player.hasDisconnected())
					editorResult(player, session.token, message);
			});
		});
	}

	private static void editorResult(ServerPlayer player, String session, String message) {
		if (ServerPlayNetworking.canSend(player, NoticePayloads.Editor.TYPE))
			ServerPlayNetworking.send(
			    player, new NoticePayloads.Editor(
			                session, "", message.length() > 2048 ? message.substring(0, 2048) : message, List.of()));
	}

	void join(ServerPlayer player) {
		if (ready)
			sync(player, prompt(player));
		else
			pendingJoins.add(player.getUUID());
	}

	void leave(UUID player) {
		pendingJoins.remove(player);
		delivered.remove(player);
		editors.remove(player);
	}

	private String prompt(ServerPlayer player) {
		return catalog.documents()
		    .stream()
		    .map(NoticeCatalog.Document::entry)
		    .filter(entry -> entry.acknowledgement() && !data.read(player.getUUID(), entry))
		    .map(NoticeCatalog.Entry::id)
		    .findFirst()
		    .orElse("");
	}

	public boolean open(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, NoticePayloads.Catalog.TYPE))
			return false;
		sync(player, "*");
		return true;
	}

	private void sync(ServerPlayer player, String openId) {
		if (ServerPlayNetworking.canSend(player, NoticePayloads.Catalog.TYPE))
			ServerPlayNetworking.send(
			    player, new NoticePayloads.Catalog(
			                catalog.title(), catalog.categories(),
			                catalog.documents()
			                    .stream()
			                    .map(doc -> doc.entry().withRead(data.read(player.getUUID(), doc.entry())))
			                    .toList(),
			                openId));
	}

	void receive(ServerPlayer player, NoticePayloads.Request request) {
		switch (request.action()) {
		case OPEN -> {
			sync(player, "");
			BalanceSync.subscribe(player, BalanceSync.View.NOTICE, true);
		}
		case CLOSE -> {
			BalanceSync.subscribe(player, BalanceSync.View.NOTICE, false);
			delivered.remove(player.getUUID());
		}
		case BODY, READ, ACKNOWLEDGE -> {
			var document = catalog.find(request.id());
			if (document == null || !document.entry().revision().equals(request.revision())) {
				sync(player, "");
				return;
			}
			var entry = document.entry();
			if (request.action() == NoticePayloads.Action.BODY) {
				if (ServerPlayNetworking.canSend(player, NoticePayloads.Body.TYPE)) {
					delivered.computeIfAbsent(player.getUUID(), key -> new HashMap<>())
					    .put(entry.id(), entry.revision());
					ServerPlayNetworking.send(player, new NoticePayloads.Body(entry.id(), entry.revision(),
					                                                          document.body(), document.markdown()));
				}
			} else if (entry.revision().equals(delivered.getOrDefault(player.getUUID(), Map.of()).get(entry.id())) &&
			           data.complete(player.getUUID(), entry, request.revision(),
			                         request.action() == NoticePayloads.Action.ACKNOWLEDGE)) {
				sync(player, "");
			}
		}
		}
	}

	@Override
	public void close() {
		closed = true;
		loader.shutdownNow();
	}
}
