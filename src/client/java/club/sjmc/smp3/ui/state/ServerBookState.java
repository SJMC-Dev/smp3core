package club.sjmc.smp3.ui.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import club.sjmc.smp3.notice.NoticeCatalog;
import club.sjmc.smp3.notice.NoticePayloads;
import club.sjmc.smp3.ui.Smp3Client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ServerBookState {
	public static final int NOTICE_HEIGHT = 24;
	public String title = NoticeCatalog.EMPTY.title();
	public List<NoticeCatalog.Category> categories = List.of(new NoticeCatalog.Category("all", "全部"));
	public List<NoticeCatalog.Entry> messages = List.of();
	public final Map<String, NoticePayloads.Body> bodies = new HashMap<>();
	private final Set<String> requested = new HashSet<>(), receipts = new HashSet<>();
	public int selected = -1, category;
	public String pendingOpen;
	private final List<Listener> listeners = new CopyOnWriteArrayList<>();

	public interface Listener {
		default void noticeCatalogChanged() {}

		default void noticeBodyChanged() {}
	}

	public void addListener(Listener listener) {
		listeners.remove(listener);
		listeners.add(listener);
	}

	public void removeListener(Listener listener) { listeners.remove(listener); }

	private void notifyNoticeCatalogChanged() { listeners.forEach(Listener::noticeCatalogChanged); }

	private void notifyNoticeBodyChanged() { listeners.forEach(Listener::noticeBodyChanged); }

	public void accept(NoticePayloads.Catalog catalog) {
		String selectedId = selected >= 0 ? messages.get(selected).id() : "";
		String categoryId = categories.get(category).id();
		title = catalog.title();
		var all = new ArrayList<NoticeCatalog.Category>();
		all.add(new NoticeCatalog.Category("all", "全部"));
		all.addAll(catalog.categories());
		categories = List.copyOf(all);
		category = 0;
		for (int i = 0; i < categories.size(); i++)
			if (categories.get(i).id().equals(categoryId))
				category = i;
		messages = catalog.entries();
		selected = -1;
		for (int i = 0; i < messages.size(); i++)
			if (messages.get(i).id().equals(selectedId))
				selected = i;
		bodies.entrySet().removeIf(
		    cached
		    -> messages.stream().noneMatch(
		        entry -> entry.id().equals(cached.getKey()) && entry.revision().equals(cached.getValue().revision())));
		requested.clear();
		receipts.clear();
		if (!catalog.openId().isEmpty())
			pendingOpen = catalog.openId();
		if (pendingOpen != null && !pendingOpen.equals("*") &&
		    messages.stream().noneMatch(entry -> entry.id().equals(pendingOpen)))
			pendingOpen = null;
		notifyNoticeCatalogChanged();
	}

	public void accept(NoticePayloads.Body body) {
		if (messages.stream().anyMatch(
		        entry -> entry.id().equals(body.id()) && entry.revision().equals(body.revision()))) {
			bodies.put(body.id(), body);
			notifyNoticeBodyChanged();
		}
	}

	public void request(NoticePayloads.Action action, String id, String revision) {
		if (ClientPlayNetworking.canSend(NoticePayloads.Request.TYPE))
			ClientPlayNetworking.send(new NoticePayloads.Request(action, id, revision));
	}

	public void view(int index) {
		var entry = messages.get(index);
		if (requested.add(entry.id()))
			request(NoticePayloads.Action.BODY, entry.id(), entry.revision());
	}

	public void displayed(int index, boolean confirmed) {
		var entry = messages.get(index);
		if (entry.read() || !bodies.containsKey(entry.id()) || entry.acknowledgement() && !confirmed)
			return;
		if (receipts.add(entry.id()))
			request(confirmed ? NoticePayloads.Action.ACKNOWLEDGE : NoticePayloads.Action.READ, entry.id(),
			        entry.revision());
	}

	public String categoryName(String id) {
		return categories.stream()
		    .filter(c -> c.id().equals(id))
		    .map(NoticeCatalog.Category::name)
		    .findFirst()
		    .orElse(id);
	}

	public void clear() {
		messages = List.of();
		bodies.clear();
		requested.clear();
		receipts.clear();
		categories = List.of(new NoticeCatalog.Category("all", "全部"));
		title = NoticeCatalog.EMPTY.title();
		pendingOpen = null;
		selected = -1;
		category = 0;
	}

	public boolean unread(int index) { return !messages.get(index).read(); }

	public int unreadCount() {
		int count = 0;
		for (int i = 0; i < messages.size(); i++)
			if (unread(i) && messages.get(i).notifyUnread())
				count++;
		return count;
	}

	public boolean noticeVisible() { return Smp3Client.hud().visible && unreadCount() > 0; }

}
