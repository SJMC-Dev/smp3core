package club.sjmc.smp3.client;

import java.util.List;
import java.util.stream.IntStream;

import com.mojang.blaze3d.platform.InputConstants;

import club.sjmc.smp3.notice.NoticePayloads;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class ServerBookScreen extends Screen {
	private static final int INK = 0xFF443323, MUTED = 0xFF827058;
	private final ServerBookState state = Smp3Client.BOOK;
	private int x, y, bookWidth, bookHeight, sidebar, bodyTop, bodyBottom, listScroll, textScroll, headerExtra;
	private boolean narrow, detail, subscribed;
	private int categoryPage;
	private NoticePayloads.Body renderedBody;
	private int renderedWidth;
	private List<FormattedCharSequence> lines = List.of();
	private List<Integer> entries;
	private Button acknowledge;
	private long lastFrame = System.nanoTime();

	ServerBookScreen() { this(false); }
	ServerBookScreen(boolean detail) {
		super(Component.literal("服务器手册"));
		this.detail = detail;
		categoryPage = state.category / 4;
	}

	void contentChanged() {
		if (minecraft != null)
			rebuildWidgets();
	}

	@Override
	public void removed() {
		state.request(NoticePayloads.Action.CLOSE, "", "");
		subscribed = false;
	}

	@Override
	protected void init() {
		if (!subscribed) {
			state.coins.clear();
			state.stamps.clear();
			state.request(NoticePayloads.Action.OPEN, "", "");
			subscribed = true;
		}
		x = Math.max(8, (width - 600) / 2);
		y = Math.max(8, (height - 354) / 2);
		bookWidth = width - 2 * x;
		bookHeight = height - 2 * y;
		narrow = bookWidth < 400;
		sidebar = narrow ? bookWidth - 28 : Math.min(174, bookWidth / 3);
		headerExtra = bookWidth - 44 < font.width(state.title) + 24 + state.currencyWidth() ? 22 : 0;
		bodyTop = y + 73 + headerExtra;
		bodyBottom = y + bookHeight - 20;
		entries = IntStream.range(0, state.messages.size())
		              .filter(i
		                      -> state.category == 0 ||
		                             state.messages.get(i).category().equals(state.categories.get(state.category).id()))
		              .boxed()
		              .toList();
		if (!entries.contains(state.selected)) {
			state.selected = entries.isEmpty() ? -1 : entries.getFirst();
			textScroll = 0;
		}
		listScroll = Math.clamp(listScroll, 0, Math.max(0, entries.size() * 40 - (bodyBottom - bodyTop)));
		int pages = (state.categories.size() + 3) / 4;
		categoryPage = Math.min(categoryPage, pages - 1);
		int tabWidth = (sidebar - 8 - (pages > 1 ? 20 : 0)) / Math.min(4, state.categories.size());
		if (!narrow || !detail) {
			for (int i = categoryPage * 4; i < Math.min(state.categories.size(), categoryPage * 4 + 4); i++) {
				int category = i;
				String label = font.plainSubstrByWidth(state.categories.get(i).name(), tabWidth - 6);
				var button = new PaperButton(x + 14 + (i % 4) * tabWidth, y + 46 + headerExtra, tabWidth - 2, label,
				                             true, () -> {
					                             state.category = category;
					                             listScroll = 0;
					                             textScroll = 0;
					                             rebuildWidgets();
				                             });
				button.active = state.category != i;
				addRenderableWidget(button);
			}
			if (pages > 1)
				addRenderableWidget(new PaperButton(x + sidebar - 14, y + 46 + headerExtra, 18, "›", true, () -> {
					categoryPage = (categoryPage + 1) % pages;
					rebuildWidgets();
				}));
		}

		if (narrow && detail)
			addRenderableWidget(new PaperButton(x + 14, y + 46 + headerExtra, 78, "返回目录", false, () -> {
				detail = false;
				rebuildWidgets();
			}));
		acknowledge = new PaperButton(contentLeft(), bodyBottom - 22, Math.min(134, contentWidth()), "我已阅读并确认",
		                              false, () -> {
			                              state.displayed(state.selected, true);
			                              updateAcknowledgement();
		                              });
		addRenderableWidget(acknowledge);
		if (!entries.isEmpty() && (!narrow || detail))
			state.view(state.selected);
		updateAcknowledgement();
	}

	private int contentLeft() { return narrow ? x + 22 : x + sidebar + 30; }
	private int contentWidth() { return x + bookWidth - 24 - contentLeft(); }
	private int contentTop() { return (narrow ? bodyTop : y + 50 + headerExtra) + 18; }

	private void updateAcknowledgement() {
		acknowledge.visible =
		    !entries.isEmpty() && (!narrow || detail) && state.messages.get(state.selected).acknowledgement();
		acknowledge.active = state.selected >= 0 && state.unread(state.selected) &&
		                     state.bodies.containsKey(state.messages.get(state.selected).id());
		acknowledge.setMessage(
		    Component.literal(state.selected >= 0 && !state.unread(state.selected) ? "已确认" : "我已阅读并确认"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float seconds = Math.min(.05F, (now - lastFrame) / 1e9F);
		lastFrame = now;
		graphics.fill(0, 0, width, height, 0x66000000);
		graphics.fill(x + 4, y + 5, x + bookWidth + 4, y + bookHeight + 5, 0x77000000);
		graphics.fillGradient(x, y, x + bookWidth, y + bookHeight, 0xFF79523A, 0xFF3F2A20);
		graphics.outline(x + 1, y + 1, bookWidth - 2, bookHeight - 2, 0xFFAD8053);
		graphics.fill(x + 5, y + 4, x + 9, y + bookHeight - 4, 0xFF39271F);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BookViewScreen.BOOK_LOCATION, x + 11, y + 8, 32, 20, bookWidth - 20,
		              bookHeight - 16, 128, 140, 256, 256);
		graphics.text(font, font.plainSubstrByWidth(state.title, bookWidth - 44), x + 22, y + 28, INK, false);
		state.renderCurrency(graphics, x + bookWidth - 22, y + (headerExtra == 0 ? 22 : 44),
		                     bookWidth - 44 - (headerExtra == 0 ? font.width(state.title) + 24 : 0), seconds);
		graphics.horizontalLine(x + 20, x + bookWidth - 20, y + 41 + headerExtra, 0xFFB7A17D);
		if (!narrow) {
			graphics.fill(x + sidebar + 14, y + 47 + headerExtra, x + sidebar + 16, bodyBottom, 0x66836B48);
			graphics.fill(x + sidebar + 16, y + 47 + headerExtra, x + sidebar + 19, bodyBottom, 0x22765A37);
		}
		if (!narrow || !detail)
			drawList(graphics, mouseX, mouseY);
		if (!narrow || detail)
			drawMessage(graphics);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private void drawList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int left = x + 16, right = x + sidebar + 7;
		if (entries.isEmpty()) {
			graphics.text(font, "暂无内容", left + 7, bodyTop + 7, MUTED, false);
			return;
		}
		graphics.enableScissor(left, bodyTop, right, bodyBottom);
		for (int row = 0; row < entries.size(); row++) {
			int index = entries.get(row), top = bodyTop + row * 40 - listScroll;
			var message = state.messages.get(index);
			boolean selected = index == state.selected;
			if (selected || mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + 37)
				graphics.fill(left, top, right - 4, top + 37, selected ? 0x447F5936 : 0x227F5936);
			if (selected)
				graphics.fill(left, top + 3, left + 2, top + 34, 0xFF8C4B30);
			if (state.unread(index))
				graphics.fill(right - 13, top + 9, right - 9, top + 13, 0xFFB54536);
			graphics.text(font, font.plainSubstrByWidth(message.title(), right - left - (message.pinned() ? 34 : 23)),
			              left + (message.pinned() ? 18 : 7), top + 7, INK, false);
			if (message.pinned()) {
				graphics.fill(left + 8, top + 7, left + 13, top + 9, INK);
				graphics.fill(left + 9, top + 9, left + 12, top + 12, INK);
				graphics.fill(left + 7, top + 12, left + 14, top + 13, INK);
				graphics.fill(left + 10, top + 13, left + 11, top + 17, INK);
			}
			graphics.text(font, state.categoryName(message.category()) + (state.unread(index) ? "  未读" : "  已读"),
			              left + 7, top + 23, MUTED, false);
		}
		graphics.disableScissor();
		scrollbar(graphics, right - 2, bodyTop, bodyBottom, entries.size() * 40, listScroll);
	}

	private void drawMessage(GuiGraphicsExtractor graphics) {
		if (entries.isEmpty())
			return;
		var message = state.messages.get(state.selected);
		int left = contentLeft();
		int top = contentTop(), bottom = bodyBottom - (message.acknowledgement() ? 30 : 0);
		int dateWidth = font.width(message.date());
		int titleWidth = contentWidth() - (dateWidth == 0 ? 0 : dateWidth + 12);
		graphics.text(font, font.plainSubstrByWidth(message.title(), titleWidth), left, top - 18, INK, false);
		graphics.text(font, message.date(), left + contentWidth() - dateWidth, top - 18, MUTED, false);
		var body = state.bodies.get(message.id());
		if (body == null) {
			graphics.text(font, "正在加载…", left, top, MUTED, false);
			return;
		}
		if (!body.equals(renderedBody) || renderedWidth != contentWidth()) {
			renderedBody = body;
			renderedWidth = contentWidth();
			lines = BookText.lines(body, font, contentWidth() - 10);
			textScroll = 0;
		}
		state.displayed(state.selected, false);
		textScroll = Math.clamp(textScroll, 0, Math.max(0, lines.size() * 14 - (bottom - top)));
		graphics.enableScissor(left, top, left + contentWidth(), bottom);
		for (int i = textScroll / 14; i < Math.min(lines.size(), (textScroll + bottom - top) / 14 + 1); i++)
			graphics.text(font, lines.get(i), left, top + i * 14 - textScroll, INK, false);
		graphics.disableScissor();
		scrollbar(graphics, left + contentWidth() - 3, top, bottom, lines.size() * 14, textScroll);
	}

	private static void scrollbar(GuiGraphicsExtractor graphics, int x, int top, int bottom, int content, int scroll) {
		int height = bottom - top;
		if (content <= height || height <= 0)
			return;
		int thumb = Math.max(12, height * height / content);
		int y = top + (height - thumb) * scroll / (content - height);
		graphics.fill(x, top, x + 2, bottom, 0x44765A37);
		graphics.fill(x, y, x + 2, y + thumb, 0xFF9B7B51);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick))
			return true;
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && (!narrow || !detail) && event.x() >= x + 16 &&
		    event.x() < x + sidebar + 7 && event.y() >= bodyTop && event.y() < bodyBottom) {
			int row = ((int)event.y() - bodyTop + listScroll) / 40;
			if (row < entries.size()) {
				state.selected = entries.get(row);
				state.view(state.selected);
				textScroll = 0;
				detail = true;
				rebuildWidgets();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (mouseY > bodyBottom)
			return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
		if ((!narrow || !detail) && mouseY >= bodyTop && mouseX >= x + 16 && mouseX < x + sidebar + 7)
			listScroll = Math.clamp(listScroll - (int)(vertical * 28), 0,
			                        Math.max(0, entries.size() * 40 - (bodyBottom - bodyTop)));
		else if ((!narrow || detail) && mouseY >= contentTop() && mouseX >= contentLeft() &&
		         mouseX < contentLeft() + contentWidth())
			textScroll = Math.max(0, textScroll - (int)(vertical * 28));
		else
			return false;
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {}
}
