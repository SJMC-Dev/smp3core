package club.sjmc.smp3.ui.widget;

import club.sjmc.smp3.ui.themes.PaperStyle;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 竖直滚动区域：维护滚动值、内容高度、滚动条拖动与滚轮换算。
 * 坐标使用宿主界面的绝对坐标。
 */
public final class ScrollView {
	private int top, bottom, content, scroll;
	private boolean dragging;
	private double grab;

	public void layout(int top, int bottom, int content) {
		this.top = top;
		this.bottom = bottom;
		this.content = content;
		setScroll(scroll);
	}

	public int scroll() { return scroll; }

	public int maxScroll() { return Math.max(0, content - (bottom - top)); }

	public void setScroll(int value) { scroll = Math.clamp(value, 0, maxScroll()); }

	public void scrollBy(int delta) { setScroll(scroll + delta); }

	public void scrollWheel(double vertical, int step) { scrollBy((int)Math.round(-vertical * step)); }

	public boolean beginDrag(double mouseX, double mouseY, int barX) {
		var bar = bar(barX);
		if (!bar.contains(mouseX, mouseY))
			return false;
		int thumb = bar.thumbTop(scroll);
		grab = mouseY >= thumb && mouseY < thumb + bar.thumbHeight() ? mouseY - thumb : bar.thumbHeight() / 2.0;
		dragging = true;
		return true;
	}

	public boolean mouseDragged(double mouseY) {
		if (!dragging)
			return false;
		scroll = bar(0).position(mouseY, grab);
		return true;
	}

	public boolean mouseReleased() {
		if (!dragging)
			return false;
		dragging = false;
		return true;
	}

	public void render(GuiGraphicsExtractor graphics, int barX) {
		PaperStyle.scrollbar(graphics, barX, top, bottom, content, scroll);
	}

	private PaperStyle.Scrollbar bar(int x) { return new PaperStyle.Scrollbar(x, top, bottom, content); }
}
