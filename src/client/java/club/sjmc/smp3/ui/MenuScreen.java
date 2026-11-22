package club.sjmc.smp3.ui;

import com.mojang.blaze3d.platform.InputConstants;

import club.sjmc.smp3.daily.DailyQuestPayloads;
import club.sjmc.smp3.sign.SignPayloads;
import club.sjmc.smp3.title.TitlePayloads;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.LocalDate;

public final class MenuScreen extends Screen {
	private static final String[] TABS = {"公告", "签到", "任务", "商店", "称号"};
	private static final int TAB_DEPTH = 30;
	private static final int TAB_ACTIVE_DEPTH = 40;
	private static final int TAB_GAP = 6;
	private static final int TAB_DIM = 0xFF6B563E;
	private static final int EMBED_INSET = 10;
	private SignPayloads.State sign;
	private TitlePayloads.Catalog titles;
	private DailyQuestPayloads.Catalog quests;
	private Screen child;
	private int selected = -1;
	private int x, y, panelWidth, panelHeight, tabTop, tabHeight, paneX, paneY, paneWidth, paneHeight;
	private float childScale = .5F;
	private static float tooltipScale = 1;
	private static int tooltipOriginX, tooltipOriginY;

	MenuScreen() { super(Component.literal("菜单")); }

	void acceptSign(SignPayloads.State state) {
		sign = state;
		if (selected == 1) {
			if (child instanceof SignScreen screen)
				screen.accept(state);
			else
				selectTab(1);
		}
	}

	void acceptTitles(TitlePayloads.Catalog catalog) {
		titles = catalog;
		if (selected == 4)
			selectTab(4);
	}

	void acceptQuests(DailyQuestPayloads.Catalog catalog) {
		quests = catalog;
		if (selected == 2) {
			if (child instanceof DailyQuestScreen screen)
				screen.accept(catalog);
			else
				selectTab(2);
		}
	}

	void shopReady() {
		if (selected == 3)
			selectTab(3);
	}

	void balancesChanged() {
		if (child instanceof ShopScreen screen)
			screen.balancesChanged();
		else if (child instanceof ServerBookScreen screen)
			screen.contentChanged();
	}

	void shopStateChanged() {
		if (child instanceof ShopScreen screen)
			screen.stateChanged();
	}

	void noticeChanged() {
		if (child instanceof ServerBookScreen screen)
			screen.contentChanged();
	}

	@Override
	protected void init() {
		panelWidth = Math.min(320, Math.max(200, width - 32 - TAB_ACTIVE_DEPTH));
		panelHeight = Math.min(204, height - 32);
		int totalWidth = panelWidth + TAB_ACTIVE_DEPTH;
		x = (width - totalWidth) / 2 + TAB_ACTIVE_DEPTH;
		y = (height - panelHeight) / 2;
		tabHeight = 28;
		int stack = TABS.length * tabHeight + (TABS.length - 1) * TAB_GAP;
		tabTop = y + (panelHeight - stack) / 2;
		paneX = x + 13;
		paneY = y + 12;
		paneWidth = panelWidth - 26;
		paneHeight = panelHeight - 24;
		if (child != null)
			embedChild(child);
	}

	private static void embed(Screen screen, int x, int y, int width, int height) {
		if (screen instanceof ServerBookScreen child)
			child.embed(x, y, width, height);
		else if (screen instanceof ShopScreen child)
			child.embed(x, y, width, height);
		else if (screen instanceof SignScreen child)
			child.embed(x, y, width, height);
		else if (screen instanceof DailyQuestScreen child)
			child.embed(x, y, width, height);
		else if (screen instanceof TitleScreen child)
			child.embed(x, y, width, height);
	}

	private static int signRows() {
		LocalDate first = LocalDate.now().withDayOfMonth(1);
		int offset = first.getDayOfWeek().getValue() % 7;
		return (offset + first.lengthOfMonth() + 6) / 7;
	}

	private void selectTab(int index) {
		selected = index;
		Screen previous = child;
		child = switch (index) {
			case 0 -> new ServerBookScreen();
			case 1 -> sign == null ? null : new SignScreen(sign);
			case 2 -> quests == null ? null : new DailyQuestScreen(quests);
			case 3 -> new ShopScreen();
			case 4 -> titles == null ? null : new TitleScreen(titles);
			default -> null;
		};
		if (previous != null && previous != child && !(previous instanceof ShopScreen))
			previous.removed();
		if (child != null)
			embedChild(child);
	}

	private void embedChild(Screen screen) {
		childScale = 1F;
		embed(screen, 0, 0, paneWidth + EMBED_INSET * 2, paneHeight + EMBED_INSET * 2);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0x66000000);
		PaperStyle.frame(graphics, x, y, panelWidth, panelHeight);
		for (int i = 0; i < TABS.length; i++) {
			int top = tabTop + i * (tabHeight + TAB_GAP);
			boolean active = i == selected;
			int depth = active ? TAB_ACTIVE_DEPTH : TAB_DEPTH;
			int left = x - depth;
			int right = x + (active ? 9 : 1);
			boolean hovered = !active && mouseX >= left && mouseX < right && mouseY >= top &&
			                  mouseY < top + tabHeight;
			graphics.fill(left, top, right, top + tabHeight,
			              active ? 0xFFF2E7CF : hovered ? 0xDDE0C9A2 : 0xCCB39B7D);
			int edge = active ? 0xAA8C5B38 : 0x66836B48;
			int glow = active ? 0x448C5B38 : 0x22765A37;
			graphics.fill(left, top, right, top + 1, edge);
			graphics.fill(left, top + 1, right, top + 2, glow);
			graphics.fill(left, top + tabHeight - 1, right, top + tabHeight, edge);
			graphics.fill(left, top + tabHeight - 2, right, top + tabHeight - 1, glow);
			graphics.fill(left, top, left + 1, top + tabHeight, edge);
			graphics.fill(left + 1, top, left + 2, top + tabHeight, glow);
			graphics.fill(right - 1, top, right, top + tabHeight, edge);
			graphics.fill(right - 2, top, right - 1, top + tabHeight, glow);
			String label = TABS[i];
			graphics.text(font, label, left + (depth - font.width(label)) / 2,
			              top + (tabHeight - font.lineHeight) / 2, active ? PaperStyle.INK : TAB_DIM, false);
		}
		if (selected < 0) {
			drawCentered(graphics, "选中左侧标签以打开对应界面", PaperStyle.MUTED);
		} else if (child == null) {
			drawCentered(graphics, "正在加载…", PaperStyle.MUTED);
		} else {
			graphics.enableScissor(paneX, paneY, paneX + paneWidth, paneY + paneHeight);
			graphics.pose().pushMatrix();
			graphics.pose().translate(paneX - EMBED_INSET, paneY - EMBED_INSET);
			graphics.pose().scale(childScale, childScale);
			int virtualMouseX = (int)Math.round(mouseX - paneX + EMBED_INSET);
			int virtualMouseY = (int)Math.round(mouseY - paneY + EMBED_INSET);
			tooltipScale = childScale;
			tooltipOriginX = paneX - EMBED_INSET;
			tooltipOriginY = paneY - EMBED_INSET;
			try {
				child.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
			} finally {
				tooltipScale = 1;
				tooltipOriginX = tooltipOriginY = 0;
				graphics.pose().popMatrix();
				graphics.disableScissor();
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private void drawCentered(GuiGraphicsExtractor graphics, String text, int color) {
		var lines = font.split(Component.literal(text), Math.max(1, paneWidth - 8));
		int cursor = paneY + (paneHeight - lines.size() * (font.lineHeight + 2)) / 2;
		for (var line : lines) {
			graphics.text(font, line, paneX + (paneWidth - font.width(line)) / 2, cursor, color, false);
			cursor += font.lineHeight + 2;
		}
	}

	private int tabAt(double mouseX, double mouseY) {
		for (int i = 0; i < TABS.length; i++) {
			int top = tabTop + i * (tabHeight + TAB_GAP);
			int depth = i == selected ? TAB_ACTIVE_DEPTH : TAB_DEPTH;
			int left = x - depth;
			if (mouseX >= left && mouseX < x && mouseY >= top && mouseY < top + tabHeight)
				return i;
		}
		return -1;
	}

	private boolean insidePane(double mouseX, double mouseY) {
		return mouseX >= paneX && mouseX < paneX + paneWidth && mouseY >= paneY && mouseY < paneY + paneHeight;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick))
			return true;
		int tab = tabAt(event.x(), event.y());
		if (tab >= 0) {
			selectTab(tab);
			return true;
		}
		return child != null && insidePane(event.x(), event.y()) && child.mouseClicked(map(event), doubleClick);
	}

	private MouseButtonEvent map(MouseButtonEvent event) {
		return new MouseButtonEvent(event.x() - paneX + EMBED_INSET, event.y() - paneY + EMBED_INSET,
		                            event.buttonInfo());
	}

	static int tooltipX(double x) { return (int)Math.round(tooltipOriginX + x * tooltipScale); }
	static int tooltipY(double y) { return (int)Math.round(tooltipOriginY + y * tooltipScale); }

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return child != null ? child.mouseReleased(map(event)) : super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return child != null ? child.mouseDragged(map(event), dx / childScale, dy / childScale)
		                     : super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (child != null && insidePane(mouseX, mouseY))
			return child.mouseScrolled(mouseX - paneX + EMBED_INSET, mouseY - paneY + EMBED_INSET, horizontal,
			                          vertical);
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE)
			return super.keyPressed(event);
		return child != null ? child.keyPressed(event) : super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return child != null ? child.charTyped(event) : false;
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		if (child != null && insidePane(mouseX, mouseY))
			child.mouseMoved(mouseX - paneX + EMBED_INSET, mouseY - paneY + EMBED_INSET);
	}

	@Override
	public void removed() {
		if (child != null)
			child.removed();
		if (Smp3Client.SHOP.session != 0)
			Smp3Client.SHOP.close();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {}
}
