package club.sjmc.smp3.ui.screen;

import com.mojang.blaze3d.platform.InputConstants;

import club.sjmc.smp3.daily.DailyQuestPayloads;
import club.sjmc.smp3.sign.SignPayloads;
import club.sjmc.smp3.home.HomePayloads;
import club.sjmc.smp3.ui.panel.HomePanel;
import club.sjmc.smp3.title.TitlePayloads;
import club.sjmc.smp3.ui.Smp3Client;
import club.sjmc.smp3.ui.panel.NoticePanel;
import club.sjmc.smp3.ui.panel.Panel;
import club.sjmc.smp3.ui.panel.QuestPanel;
import club.sjmc.smp3.ui.panel.ShopPanel;
import club.sjmc.smp3.ui.panel.SignPanel;
import club.sjmc.smp3.ui.panel.TitlePanel;
import club.sjmc.smp3.ui.themes.PaperStyle;
import club.sjmc.smp3.ui.widget.NoticeWrapHost;
import club.sjmc.smp3.ui.widget.PanelContext;
import club.sjmc.smp3.ui.widget.Rect;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * 集成菜单：左侧标签，右侧直接嵌入 {@link Panel}。
 */
public final class MenuScreen extends Screen implements NoticeWrapHost {
	private static final String[] TABS = {"公告", "签到", "任务", "商店", "称号", "家园"};
	private static final int MARGIN = 16;
	private static final int TAB_DEPTH = 30;
	private static final int TAB_ACTIVE_DEPTH = 40;
	private static final int TAB_GAP = 6;
	private static final int TAB_DIM = 0xFF6B563E;
	private static final int EMBED_INSET = 10;
	private SignPayloads.State sign;
	private TitlePayloads.Catalog titles;
	private DailyQuestPayloads.Catalog quests;
	private HomePayloads.Catalog homes;
	private Panel child;
	private boolean childCaptured;
	private int selected = -1;
	private int x, y, panelWidth, panelHeight, tabTop, tabHeight, paneX, paneY, paneWidth, paneHeight;
	private PanelContext paneContext = new PanelContext(0, 0);

	public MenuScreen() { super(Component.literal("菜单")); }

	public void acceptHomes(HomePayloads.Catalog catalog) {
		homes = catalog;
		if (selected == 5) {
			if (child instanceof HomePanel panel)
				panel.accept(catalog);
			else
				selectTab(5);
		}
	}

	public void acceptSign(SignPayloads.State state) {
		sign = state;
		if (selected == 1) {
			if (child instanceof SignPanel panel)
				panel.accept(state);
			else
				selectTab(1);
		}
	}

	public void acceptTitles(TitlePayloads.Catalog catalog) {
		titles = catalog;
		if (selected != 4)
			return;
		if (child instanceof TitlePanel panel)
			panel.accept(catalog);
		else
			selectTab(4);
	}

	public void acceptQuests(DailyQuestPayloads.Catalog catalog) {
		quests = catalog;
		if (selected == 2) {
			if (child instanceof QuestPanel panel)
				panel.accept(catalog);
			else
				selectTab(2);
		}
	}

	/** 客户端 GameTest 钩子，生产路径不直接调用。 */
	public void openNotices() { openTab(0); }

	public void openTab(int index) { selectTab(index); }

	/** 客户端 GameTest 钩子，生产路径不直接调用。 */
	public NoticePanel noticePanel() { return child instanceof NoticePanel panel ? panel : null; }

	/** 客户端 GameTest 钩子，生产路径不直接调用。 */
	public Panel activePanel() { return child; }

	@Override
	protected void init() {
		panelWidth = width - MARGIN * 2 - TAB_ACTIVE_DEPTH;
		panelHeight = height - MARGIN * 2;
		x = MARGIN + TAB_ACTIVE_DEPTH;
		y = MARGIN;
		tabHeight = Math.min(28, (panelHeight - (TABS.length - 1) * TAB_GAP) / TABS.length);
		int stack = TABS.length * tabHeight + (TABS.length - 1) * TAB_GAP;
		tabTop = y + (panelHeight - stack) / 2;
		paneX = x + 13;
		paneY = y + 12;
		paneWidth = panelWidth - 26;
		paneHeight = panelHeight - 24;
		paneContext = new PanelContext(paneX - EMBED_INSET, paneY - EMBED_INSET);
		layoutChild();
	}

	private void selectTab(int index) {
		if (selected == index && child != null)
			return;
		selected = index;
		childCaptured = false;
		Panel previous = child;
		child = switch (index) {
			case 0 -> new NoticePanel(Smp3Client.book());
			case 1 -> sign == null ? null : new SignPanel(sign);
			case 2 -> quests == null ? null : new QuestPanel(quests);
			case 3 -> new ShopPanel(Smp3Client.shop());
			case 4 -> titles == null ? null : new TitlePanel(titles);
			case 5 -> homes == null ? null : new HomePanel(homes);
			default -> null;
		};
		if (previous != null && previous != child) {
			if (previous instanceof ShopPanel shop)
				shop.detachListeners();
			else
				previous.close();
		}
		layoutChild();
	}

	private void layoutChild() {
		if (child != null)
			child.layout(new Rect(0, 0, paneWidth + EMBED_INSET * 2, paneHeight + EMBED_INSET * 2), paneContext);
	}

	@Override
	public boolean usesNoticeWrapping() {
		return child instanceof NoticePanel;
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
			boolean hovered = !active && mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + tabHeight;
			graphics.fill(left, top, right, top + tabHeight, active ? 0xFFF2E7CF : hovered ? 0xDDE0C9A2 : 0xCCB39B7D);
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
			graphics.text(font, label, left + (depth - font.width(label)) / 2, top + (tabHeight - font.lineHeight) / 2,
			              active ? PaperStyle.INK : TAB_DIM, false);
		}
		if (selected < 0) {
			drawCentered(graphics, "选中左侧标签以打开对应界面", PaperStyle.MUTED);
		} else if (child == null) {
			drawCentered(graphics, "正在加载…", PaperStyle.MUTED);
		} else {
			graphics.enableScissor(paneX, paneY, paneX + paneWidth, paneY + paneHeight);
			graphics.pose().pushMatrix();
			graphics.pose().translate(paneX - EMBED_INSET, paneY - EMBED_INSET);
			int virtualMouseX = mouseX - paneX + EMBED_INSET;
			int virtualMouseY = mouseY - paneY + EMBED_INSET;
			try {
				child.render(graphics, virtualMouseX, virtualMouseY, delta, paneContext);
			} finally {
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
			if (new Rect(left, top, x - left, tabHeight).contains(mouseX, mouseY))
				return i;
		}
		return -1;
	}

	private boolean insidePane(double mouseX, double mouseY) {
		return new Rect(paneX, paneY, paneWidth, paneHeight).contains(mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick))
			return true;
		int tab = tabAt(event.x(), event.y());
		if (tab >= 0) {
			childCaptured = false;
			selectTab(tab);
			return true;
		}
		childCaptured =
		    child != null && insidePane(event.x(), event.y()) && child.mouseClicked(map(event), doubleClick);
		return childCaptured;
	}

	private MouseButtonEvent map(MouseButtonEvent event) {
		return new MouseButtonEvent(event.x() - paneX + EMBED_INSET, event.y() - paneY + EMBED_INSET,
		                            event.buttonInfo());
	}

	@Override
	public boolean mouseReleased(@NotNull MouseButtonEvent event) {
		if (child == null)
			return super.mouseReleased(event);
		boolean captured = childCaptured || insidePane(event.x(), event.y());
		childCaptured = false;
		return captured ? child.mouseReleased(map(event)) : super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(@NotNull MouseButtonEvent event, double dx, double dy) {
		if (child != null && (childCaptured || insidePane(event.x(), event.y())))
			return child.mouseDragged(map(event), dx, dy);
		return super.mouseDragged(event, dx, dy);
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
	public boolean charTyped(@NotNull CharacterEvent event) {
		return child != null && child.charTyped(event);
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		if (child != null && insidePane(mouseX, mouseY))
			child.mouseMoved(mouseX - paneX + EMBED_INSET, mouseY - paneY + EMBED_INSET);
	}

	@Override
	public void removed() {
		childCaptured = false;
		if (child != null)
			child.close();
		if (Smp3Client.shop().session != 0)
			Smp3Client.shop().close();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {}
}
