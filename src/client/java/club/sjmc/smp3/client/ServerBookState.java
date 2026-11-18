package club.sjmc.smp3.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
	static final float FLOAT_SECONDS = 1.4F;
	private ItemStack coin, stamp, book;
	final Counter coins = new Counter(), stamps = new Counter();
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
		if (coin != null)
			return;
		coin = new ItemStack(Items.IRON_PICKAXE);
		stamp = new ItemStack(Items.PAPER);
		book = new ItemStack(Items.BOOK);
	}

	void clear() {
		messages = List.of();
		coins.clear();
		stamps.clear();
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

	int currencyWidth() { return coins.width() + 12 + stamps.width(); }

	void renderCurrency(GuiGraphicsExtractor graphics, int right, int top, int availableWidth, float seconds) {
		icons();
		float scale = Math.min(1, (float)availableWidth / currencyWidth());
		graphics.pose().pushMatrix();
		graphics.pose().translate(right - currencyWidth() * scale, top);
		graphics.pose().scale(scale, scale);
		coins.render(graphics, coin, 0, seconds);
		stamps.render(graphics, stamp, coins.width() + 12, seconds);
		graphics.pose().popMatrix();
	}

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

	static final class Counter {
		long value, change;
		boolean received;
		float age = FLOAT_SECONDS;
		String text = "—";

		void clear() {
			received = false;
			value = change = 0;
			age = FLOAT_SECONDS;
			text = "—";
		}

		void set(long value) {
			if (received && value != this.value) {
				change = (age < FLOAT_SECONDS ? change : 0) + value - this.value;
				age = 0;
			}
			this.value = value;
			text = String.format(Locale.ROOT, "%,d", value);
			received = true;
		}

		int width() { return 22 + Minecraft.getInstance().font.width(text); }

		void render(GuiGraphicsExtractor graphics, ItemStack icon, int x, float seconds) {
			var font = Minecraft.getInstance().font;
			graphics.item(icon, x, 2);
			graphics.text(font, text, x + 22, 6, 0xFF443323, false);
			age = Math.min(FLOAT_SECONDS, age + seconds);
			if (change == 0 || age >= FLOAT_SECONDS)
				return;
			int alpha = Math.round(255 * Math.min(1, (FLOAT_SECONDS - age) / .6F));
			if (alpha < 4)
				return;
			int color = (alpha << 24) | (change > 0 ? 0x387341 : 0xAD4434);
			String delta = (change > 0 ? "+" : "") + change;
			graphics.text(font, delta, x + width() - font.width(delta), -8 - Math.round(6 * age / FLOAT_SECONDS), color,
			              false);
		}
	}
}
