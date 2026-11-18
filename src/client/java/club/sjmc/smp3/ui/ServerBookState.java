package club.sjmc.smp3.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import club.sjmc.smp3.notice.NoticeCatalog;
import club.sjmc.smp3.notice.NoticePayloads;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class ServerBookState {
	static final int NOTICE_HEIGHT = 24;
	private ItemStack book;
	String title = NoticeCatalog.EMPTY.title();
	List<NoticeCatalog.Category> categories = List.of(new NoticeCatalog.Category("all", "全部"));
	List<NoticeCatalog.Entry> messages = List.of();
	final Map<String, NoticePayloads.Body> bodies = new HashMap<>();
	private final Set<String> requested = new HashSet<>(), receipts = new HashSet<>();
	int selected = -1, category;
	String pendingOpen;

	void accept(NoticePayloads.Catalog catalog) {
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
		refreshScreen();
	}

	void accept(NoticePayloads.Body body) {
		if (messages.stream().anyMatch(
		        entry -> entry.id().equals(body.id()) && entry.revision().equals(body.revision()))) {
			bodies.put(body.id(), body);
			refreshScreen();
		}
	}

	private void refreshScreen() {
		if (Minecraft.getInstance().gui.screen() instanceof ServerBookScreen screen)
			screen.contentChanged();
	}

	void request(NoticePayloads.Action action, String id, String revision) {
		if (ClientPlayNetworking.canSend(NoticePayloads.Request.TYPE))
			ClientPlayNetworking.send(new NoticePayloads.Request(action, id, revision));
	}

	void view(int index) {
		var entry = messages.get(index);
		if (requested.add(entry.id()))
			request(NoticePayloads.Action.BODY, entry.id(), entry.revision());
	}

	void displayed(int index, boolean confirmed) {
		var entry = messages.get(index);
		if (entry.read() || !bodies.containsKey(entry.id()) || entry.acknowledgement() && !confirmed)
			return;
		if (receipts.add(entry.id()))
			request(confirmed ? NoticePayloads.Action.ACKNOWLEDGE : NoticePayloads.Action.READ, entry.id(),
			        entry.revision());
	}

	String categoryName(String id) {
		return categories.stream()
		    .filter(c -> c.id().equals(id))
		    .map(NoticeCatalog.Category::name)
		    .findFirst()
		    .orElse(id);
	}

	private void icons() {
		if (book != null)
			return;
		book = new ItemStack(Items.BOOK);
	}

	void clear() {
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

	boolean unread(int index) { return !messages.get(index).read(); }

	int unreadCount() {
		int count = 0;
		for (int i = 0; i < messages.size(); i++)
			if (unread(i) && messages.get(i).notifyUnread())
				count++;
		return count;
	}

	boolean noticeVisible() { return Smp3Client.HUD.visible && unreadCount() > 0; }

	private String noticeTitle() {
		int first = 0;
		while (!unread(first) || !messages.get(first).notifyUnread())
			first++;
		var font = Minecraft.getInstance().font;
		String title = messages.get(first).title();
		return font.width(title) <= 80 ? title : font.plainSubstrByWidth(title, 80 - font.width("…")) + "…";
	}

	int noticeWidth() {
		var font = Minecraft.getInstance().font;
		return 40 + font.width(noticeTitle()) + font.width(Integer.toString(unreadCount()));
	}

	void renderNotice(GuiGraphicsExtractor graphics, int right, int y) {
		if (!noticeVisible())
			return;
		icons();
		var font = Minecraft.getInstance().font;
		int x = right - noticeWidth();
		String count = Integer.toString(unreadCount());
		graphics.fill(x, y, right, y + NOTICE_HEIGHT, HudLayout.PANEL);
		graphics.item(book, x + 4, y + 4);
		graphics.fill(x + 17, y + 3, x + 21, y + 7, HudLayout.RED);
		graphics.text(font, noticeTitle(), x + 26, y + 8, HudLayout.TEXT);
		graphics.text(font, count, right - 6 - font.width(count), y + 8, HudLayout.RED);
	}
}
