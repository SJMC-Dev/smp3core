package club.sjmc.smp3.ui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 面板在宿主中的坐标上下文：把面板局部坐标映射到屏幕坐标，
 * 供 tooltip 与 scissor 使用。当前缩放固定为 1，保留字段以便后续扩展。
 */
public final class PanelContext {
	private final int originX, originY;
	private final float scale;

	public PanelContext(int originX, int originY, float scale) {
		this.originX = originX;
		this.originY = originY;
		this.scale = scale;
	}

	public static PanelContext fullscreen() { return new PanelContext(0, 0, 1F); }

	public int originX() { return originX; }

	public int originY() { return originY; }

	public float scale() { return scale; }

	public int toScreenX(double localX) { return Math.round((float)(originX + localX * scale)); }

	public int toScreenY(double localY) { return Math.round((float)(originY + localY * scale)); }

	public int tooltipX(double localX) { return toScreenX(localX); }

	public int tooltipY(double localY) { return toScreenY(localY); }

	public void enableScissor(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		graphics.enableScissor(toScreenX(x), toScreenY(y), toScreenX(x + width), toScreenY(y + height));
	}

	public void disableScissor(GuiGraphicsExtractor graphics) { graphics.disableScissor(); }
}
