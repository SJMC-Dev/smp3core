package club.sjmc.smp3.ui;

import java.util.List;
import java.util.stream.IntStream;

import com.mojang.blaze3d.platform.InputConstants;

import club.sjmc.smp3.notice.NoticePayloads;
import club.sjmc.smp3.ui.state.CurrencyState;
import club.sjmc.smp3.ui.state.ServerBookState;
import club.sjmc.smp3.ui.themes.PaperStyle;
import club.sjmc.smp3.ui.widget.BookText;
import club.sjmc.smp3.ui.widget.PaperButton;
import club.sjmc.smp3.ui.widget.ScrollView;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ServerBookScreen extends Screen implements ServerBookState.Listener, CurrencyState.Listener {
	private static final int INK = PaperStyle.INK, MUTED = PaperStyle.MUTED;
	private static final int TWO_COLUMN_WIDTH = 360;
	private static final float TITLE_SCALE = BookText.TEXT_SCALE * 1.2F;
	private static final int TITLE_BODY_GAP = 4;
	private final ServerBookState state = Smp3Client.BOOK;
	private int x, y, bookWidth, bookHeight, sidebar, bodyTop, bodyBottom, headerExtra;
	private boolean narrow, detail, subscribed;
	private int categoryStart;
	private NoticePayloads.Body renderedBody;
	private int renderedWidth;
	private BookText.Layout textLayout = BookText.Layout.EMPTY;
	private List<Integer> entries;
	private Button acknowledge;
	private final ScrollView listScroll = new ScrollView();
	private final ScrollView textScroll = new ScrollView();
	private boolean embedded;
	private int originX, originY;
	private long lastFrame = System.nanoTime();

	public ServerBookScreen() { this(false); }
	public ServerBookScreen(boolean detail) {
		super(Component.literal("服务器手册"));
		this.detail = detail;
		categoryStart = state.category;
	}

	public void contentChanged() {
		if (minecraft != null)
			rebuildWidgets();
	}

	public void embed(int originX, int originY, int width, int height) {
		this.originX = originX;
		this.originY = originY;
		this.embedded = true;
		init(width, height);
	}

	@Override
	public void removed() {
		Smp3Client.book().removeListener(this);
		Smp3Client.balances().removeListener(this);
		state.request(NoticePayloads.Action.CLOSE, "", "");
		subscribed = false;
	}

	@Override
	public void noticeCatalogChanged() { contentChanged(); }

	@Override
	public void noticeBodyChanged() { contentChanged(); }

	@Override
	public void currencyChanged() { contentChanged(); }

	@Override
	protected void init() {
		Smp3Client.book().addListener(this);
		Smp3Client.balances().addListener(this);
		if (!subscribed) {
			Smp3Client.BALANCES.clear();
			state.request(NoticePayloads.Action.OPEN, "", "");
			subscribed = true;
		}
		if (embedded) {
			bookWidth = width;
			bookHeight = height;
			x = originX;
			y = originY;
		} else {
			int horizontalMargin = Math.clamp((width - TWO_COLUMN_WIDTH) / 2, 8, 16);
			int verticalMargin = Math.clamp((height - 240) / 2, 8, 16);
			bookWidth = Math.min(600, width - 2 * horizontalMargin);
			bookHeight = Math.min(354, height - 2 * verticalMargin);
			x = originX + (width - bookWidth) / 2;
			y = originY + (height - bookHeight) / 2;
		}
		narrow = !embedded && bookWidth < TWO_COLUMN_WIDTH;
		sidebar = narrow ? bookWidth - 28 : Math.min(174, bookWidth / 3);
		headerExtra = embedded ? 0 : bookWidth - 64 < font.width(state.title) + 24 + Smp3Client.currencyHud().width() ? 22 : 0;
		bodyTop = y + (embedded ? 34 : 73 + headerExtra);
		bodyBottom = y + bookHeight - (embedded ? 8 : 24);
		entries = IntStream.range(0, state.messages.size())
		              .filter(i
		                      -> state.category == 0 ||
		                             state.messages.get(i).category().equals(state.categories.get(state.category).id()))
		              .boxed()
		              .toList();
		if (!entries.contains(state.selected)) {
			state.selected = entries.isEmpty() ? -1 : entries.getFirst();
			textScroll.setScroll(0);
		}
		listScroll.layout(bodyTop, bodyBottom, entries.size() * 40);
		int minTabWidth = font.width("分类") + 12;
		int tabs = Math.clamp((sidebar - 16) / minTabWidth, 1, 4);
		if (state.categories.size() > tabs)
			tabs = Math.clamp((sidebar - 36) / minTabWidth, 1, 4);
		int tabsPerPage = Math.min(tabs, Math.max(1, state.categories.size()));
		int pages = (state.categories.size() + tabsPerPage - 1) / tabsPerPage;
		categoryStart = Math.min(categoryStart / tabsPerPage, Math.max(0, pages - 1)) * tabsPerPage;
		int tabWidth = (sidebar - 16 - (pages > 1 ? 20 : 0)) / tabsPerPage;
		int tabTop = y + (embedded ? 12 : 46 + headerExtra);
		if (!narrow || !detail) {
			for (int i = categoryStart; i < Math.min(state.categories.size(), categoryStart + tabsPerPage); i++) {
				int category = i;
				String label = state.categories.get(i).name();
				var button = new PaperButton(x + 22 + (i - categoryStart) * tabWidth, tabTop,
				                             tabWidth - 2, label, true, () -> {
					                             state.category = category;
					                             listScroll.setScroll(0);
					                             textScroll.setScroll(0);
					                             rebuildWidgets();
				                             });
				button.setSelected(state.category == i);
				button.active = state.category != i;
				addRenderableWidget(button);
			}
			if (pages > 1)
				addRenderableWidget(new PaperButton(x + sidebar - 14, tabTop, 18, "›", true, () -> {
					categoryStart = (categoryStart / tabsPerPage + 1) % pages * tabsPerPage;
					rebuildWidgets();
				}));
		}

		if (narrow && detail)
			addRenderableWidget(new PaperButton(x + 22, tabTop, 78, "返回目录", false, () -> {
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

	private int contentLeft() { return narrow ? x + 26 : x + sidebar + 34; }
	private int contentWidth() { return x + bookWidth - 28 - contentLeft(); }
	private int contentTop() {
		if (narrow)
			return bodyTop;
		return embedded ? y + 30 : y + 50 + headerExtra + 22;
	}
	private static Component boldTitle(String text) { return Component.literal(text).withStyle(style -> style.withBold(true)); }
	private Component boldTitle(String text, int maxWidth) {
		if (font.width(boldTitle(text)) <= maxWidth)
			return boldTitle(text);
		int end = text.length();
		while (end > 0 && font.width(boldTitle(text.substring(0, end))) > maxWidth)
			end--;
		return boldTitle(text.substring(0, end));
	}

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
		if (!embedded) {
			graphics.fill(0, 0, width, height, 0x66000000);
			PaperStyle.frame(graphics, x, y, bookWidth, bookHeight);
		}
		if (!embedded) {
			graphics.text(font, font.plainSubstrByWidth(state.title, bookWidth - 44), x + 22, y + 28, INK, false);
			Smp3Client.currencyHud().render(graphics, x + bookWidth - 42, y + (headerExtra == 0 ? 22 : 44),
			                           bookWidth - 64 - (headerExtra == 0 ? font.width(state.title) + 24 : 0),
			                           seconds);
			graphics.horizontalLine(x + 20, x + bookWidth - 20, y + 41 + headerExtra, PaperStyle.LINE);
		}
		if (!narrow) {
			int dividerTop = embedded ? y + 12 : y + 47 + headerExtra;
			graphics.fill(x + sidebar + 14, dividerTop, x + sidebar + 16, bodyBottom, 0x66836B48);
			graphics.fill(x + sidebar + 16, dividerTop, x + sidebar + 19, bodyBottom, 0x22765A37);
		}
		if (!narrow || !detail)
			drawList(graphics, mouseX, mouseY);
		if (!narrow || detail)
			drawMessage(graphics);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private void drawList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int left = x + 22, right = x + sidebar + 7;
		if (entries.isEmpty()) {
			graphics.text(font, "暂无内容", left + 7, bodyTop + 7, MUTED, false);
			return;
		}
		graphics.enableScissor(left, bodyTop, right, bodyBottom);
		for (int row = 0; row < entries.size(); row++) {
			int index = entries.get(row), top = bodyTop + row * 40 - listScroll.scroll();
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
		listScroll.render(graphics, right - 2);
	}

	private void drawMessage(GuiGraphicsExtractor graphics) {
		if (entries.isEmpty())
			return;
		var message = state.messages.get(state.selected);
		int left = contentLeft();
		int titleTop = contentTop();
		int top = titleTop + TITLE_BODY_GAP, bottom = bodyBottom - (message.acknowledgement() ? 30 : 0);
		int dateWidth = font.width(message.date());
		int headerWidth = (int)(contentWidth() / BookText.TEXT_SCALE);
		int titleWidth = (int)((contentWidth() - (dateWidth == 0 ? 0 : dateWidth + 12)) / TITLE_SCALE);
		int headerTop = titleTop - 18 + 4;
		float titleHeight = font.lineHeight * TITLE_SCALE, dateHeight = font.lineHeight * BookText.TEXT_SCALE;
		graphics.pose().pushMatrix();
		graphics.pose().translate(left, headerTop);
		graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
		graphics.text(font, boldTitle(message.title(), titleWidth), 0, 0, INK, false);
		graphics.pose().popMatrix();
		graphics.pose().pushMatrix();
		graphics.pose().translate(left, headerTop + titleHeight - dateHeight);
		graphics.pose().scale(BookText.TEXT_SCALE, BookText.TEXT_SCALE);
		graphics.text(font, message.date(), headerWidth - dateWidth, 0, MUTED, false);
		graphics.pose().popMatrix();
		var body = state.bodies.get(message.id());
		if (body == null) {
			graphics.text(font, "正在加载…", left, top, MUTED, false);
			return;
		}
		if (!body.equals(renderedBody) || renderedWidth != contentWidth()) {
			renderedBody = body;
			renderedWidth = contentWidth();
			textLayout = BookText.layout(body, font, contentWidth() - 10);
			textScroll.setScroll(0);
		}
		state.displayed(state.selected, false);
		textScroll.layout(top, bottom, textLayout.height());
		graphics.enableScissor(left, top, left + contentWidth(), bottom);
		textLayout.render(graphics, font, left, top, bottom, textScroll.scroll());
		graphics.disableScissor();
		textScroll.render(graphics, left + contentWidth() - 3);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick))
			return true;
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int bottom = bodyBottom - (acknowledge.visible ? 30 : 0);
			textScroll.layout(contentTop() + TITLE_BODY_GAP, bottom, textLayout.height());
			boolean listDrag = (!narrow || !detail) &&
			                   listScroll.beginDrag(event.x(), event.y(), x + sidebar + 5);
			if (listDrag || (!narrow || detail) && !entries.isEmpty() &&
			                   textScroll.beginDrag(event.x(), event.y(), contentLeft() + contentWidth() - 3))
				return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && (!narrow || !detail) && event.x() >= x + 22 &&
		    event.x() < x + sidebar + 7 && event.y() >= bodyTop && event.y() < bodyBottom) {
			int row = ((int)event.y() - bodyTop + listScroll.scroll()) / 40;
			if (row < entries.size()) {
				state.selected = entries.get(row);
				state.view(state.selected);
				textScroll.setScroll(0);
				detail = true;
				rebuildWidgets();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
			return super.mouseDragged(event, dx, dy);
		if (listScroll.mouseDragged(event.y()) || textScroll.mouseDragged(event.y()))
			return true;
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
			return super.mouseReleased(event);
		if (listScroll.mouseReleased() || textScroll.mouseReleased())
			return true;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (mouseY > bodyBottom)
			return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
		if ((!narrow || !detail) && mouseY >= bodyTop && mouseX >= x + 22 && mouseX < x + sidebar + 7)
			listScroll.scrollWheel(vertical, 28);
		else if ((!narrow || detail) && mouseY >= contentTop() + TITLE_BODY_GAP && mouseX >= contentLeft() &&
		         mouseX < contentLeft() + contentWidth())
			textScroll.scrollWheel(vertical, 28);
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
