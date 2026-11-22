package club.sjmc.smp3.ui.panel;

import java.util.List;
import java.util.stream.IntStream;

import com.mojang.blaze3d.platform.InputConstants;

import club.sjmc.smp3.notice.NoticePayloads;
import club.sjmc.smp3.ui.Smp3Client;
import club.sjmc.smp3.ui.state.CurrencyState;
import club.sjmc.smp3.ui.state.ServerBookState;
import club.sjmc.smp3.ui.themes.PaperStyle;
import club.sjmc.smp3.ui.widget.BookText;
import club.sjmc.smp3.ui.widget.PanelContext;
import club.sjmc.smp3.ui.widget.PaperButton;
import club.sjmc.smp3.ui.widget.ScrollView;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 公告手册面板：左侧分类/列表，右侧正文。
 */
public final class NoticePanel extends AbstractPanel implements ServerBookState.Listener, CurrencyState.Listener {
	private static final int INK = PaperStyle.INK, MUTED = PaperStyle.MUTED;
	private static final float TITLE_SCALE = BookText.TEXT_SCALE * 1.2F;
	private static final int TITLE_BODY_GAP = 4;
	private final ServerBookState state;
	private boolean subscribed;
	private int panelX, panelY, bookWidth, bookHeight, sidebar, bodyTop, bodyBottom, categoryStart;
	private NoticePayloads.Body renderedBody;
	private int renderedWidth;
	private BookText.Layout textLayout = BookText.Layout.EMPTY;
	private List<Integer> entries = List.of();
	private PaperButton acknowledge;
	private final ScrollView listScroll = new ScrollView(), textScroll = new ScrollView();

	public NoticePanel(ServerBookState state) {
		this.state = state;
		this.categoryStart = state.category;
		state.addListener(this);
		Smp3Client.balances().addListener(this);
	}

	@Override
	public void onLayout() {
		clearWidgets();
		var font = Minecraft.getInstance().font;
		if (!subscribed) {
			Smp3Client.balances().clear();
			state.request(NoticePayloads.Action.OPEN, "", "");
			subscribed = true;
		}
		bookWidth = width();
		bookHeight = height();
		panelX = x();
		panelY = y();
		sidebar = Math.min(174, bookWidth / 3);
		bodyTop = panelY + 34;
		bodyBottom = panelY + bookHeight - 8;
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
		int tabTop = panelY + 12;
		for (int i = categoryStart; i < Math.min(state.categories.size(), categoryStart + tabsPerPage); i++) {
			int category = i;
			String label = state.categories.get(i).name();
			PaperButton button = addWidget(new PaperButton(panelX + 22 + (i - categoryStart) * tabWidth, tabTop,
			                                                tabWidth - 2, label, true, () -> {
				                                                state.category = category;
				                                                listScroll.setScroll(0);
				                                                textScroll.setScroll(0);
				                                                onLayout();
			                                                }));
			button.setSelected(state.category == i);
			button.active = state.category != i;
		}
		if (pages > 1)
			addWidget(new PaperButton(panelX + sidebar - 14, tabTop, 18, "›", true, () -> {
				categoryStart = (categoryStart / tabsPerPage + 1) % pages * tabsPerPage;
				onLayout();
			}));
		acknowledge = addWidget(new PaperButton(contentLeft(), bodyBottom - 22, Math.min(134, contentWidth()),
		                                        "我已阅读并确认", false, () -> {
			                                        state.displayed(state.selected, true);
			                                        updateAcknowledgement();
		                                        }));
		if (!entries.isEmpty())
			state.view(state.selected);
		updateAcknowledgement();
	}

	private int contentLeft() { return panelX + sidebar + 34; }

	private int contentWidth() { return panelX + bookWidth - 28 - contentLeft(); }

	private int contentTop() { return panelY + 30; }

	private static Component boldTitle(String text) {
		return Component.literal(text).withStyle(style -> style.withBold(true));
	}

	private Component boldTitle(String text, int maxWidth) {
		var font = Minecraft.getInstance().font;
		if (font.width(boldTitle(text)) <= maxWidth)
			return boldTitle(text);
		int end = text.length();
		while (end > 0 && font.width(boldTitle(text.substring(0, end))) > maxWidth)
			end--;
		return boldTitle(text.substring(0, end));
	}

	private void updateAcknowledgement() {
		if (acknowledge == null || entries.isEmpty() || state.selected < 0)
			return;
		acknowledge.visible = state.messages.get(state.selected).acknowledgement();
		acknowledge.active = state.unread(state.selected) &&
		                     state.bodies.containsKey(state.messages.get(state.selected).id());
		acknowledge.setMessage(
		    Component.literal(state.selected >= 0 && !state.unread(state.selected) ? "已确认" : "我已阅读并确认"));
	}

	public void contentChanged() {
		renderedBody = null;
		onLayout();
	}

	@Override
	public void noticeCatalogChanged() { contentChanged(); }

	@Override
	public void noticeBodyChanged() { contentChanged(); }

	@Override
	public void currencyChanged() { contentChanged(); }

	public boolean acknowledgeVisible() { return acknowledge != null && acknowledge.visible; }

	public void acknowledge() {
		state.displayed(state.selected, true);
		updateAcknowledgement();
	}

	public void selectCategory(String name) {
		for (int i = 0; i < state.categories.size(); i++)
			if (state.categories.get(i).name().equals(name)) {
				state.category = i;
				listScroll.setScroll(0);
				textScroll.setScroll(0);
				onLayout();
				return;
			}
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, PanelContext context) {
		int dividerTop = panelY + 12;
		graphics.fill(panelX + sidebar + 14, dividerTop, panelX + sidebar + 16, bodyBottom, 0x66836B48);
		graphics.fill(panelX + sidebar + 16, dividerTop, panelX + sidebar + 19, bodyBottom, 0x22765A37);
		drawList(graphics, mouseX, mouseY);
		drawMessage(graphics);
		renderWidgets(graphics, mouseX, mouseY, delta);
	}

	private void drawList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		var font = Minecraft.getInstance().font;
		int left = panelX + 22, right = panelX + sidebar + 7;
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
		var font = Minecraft.getInstance().font;
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
		if (mouseClickedWidgets(event, doubleClick))
			return true;
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
			return false;
		int bottom = bodyBottom - (acknowledge.visible ? 30 : 0);
		textScroll.layout(contentTop() + TITLE_BODY_GAP, bottom, textLayout.height());
		boolean listDrag = listScroll.beginDrag(event.x(), event.y(), panelX + sidebar + 5);
		if (listDrag || !entries.isEmpty() &&
		                   textScroll.beginDrag(event.x(), event.y(), contentLeft() + contentWidth() - 3))
			return true;
		if (event.x() >= panelX + 22 && event.x() < panelX + sidebar + 7 &&
		    event.y() >= bodyTop && event.y() < bodyBottom) {
			int row = ((int)event.y() - bodyTop + listScroll.scroll()) / 40;
			if (row < entries.size()) {
				state.selected = entries.get(row);
				state.view(state.selected);
				textScroll.setScroll(0);
				onLayout();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
			return mouseDraggedWidgets(event, dx, dy);
		if (listScroll.mouseDragged(event.y()) || textScroll.mouseDragged(event.y()))
			return true;
		return mouseDraggedWidgets(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
			return mouseReleasedWidgets(event);
		if (listScroll.mouseReleased() || textScroll.mouseReleased())
			return true;
		return mouseReleasedWidgets(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (mouseY > bodyBottom)
			return false;
		if (mouseY >= bodyTop && mouseX >= panelX + 22 && mouseX < panelX + sidebar + 7)
			listScroll.scrollWheel(vertical, 28);
		else if (mouseY >= contentTop() + TITLE_BODY_GAP && mouseX >= contentLeft() &&
		         mouseX < contentLeft() + contentWidth())
			textScroll.scrollWheel(vertical, 28);
		else
			return false;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) { return keyPressedWidgets(event); }

	@Override
	public boolean charTyped(CharacterEvent event) { return charTypedWidgets(event); }

	@Override
	public void close() {
		state.removeListener(this);
		Smp3Client.balances().removeListener(this);
		if (subscribed) {
			state.request(NoticePayloads.Action.CLOSE, "", "");
			subscribed = false;
		}
	}
}
