package club.sjmc.smp3.ui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 面板在宿主中的坐标上下文：把面板局部坐标映射到屏幕坐标，
 * 供 tooltip 与 scissor 使用。当前缩放固定为 1，保留字段以便后续扩展。
 */
public record PanelContext(int originX, int originY, float scale) {
    public int toScreenX(double localX) {
        return Math.round((float) (originX + localX * scale));
    }

    public int toScreenY(double localY) {
        return Math.round((float) (originY + localY * scale));
    }

    public int tooltipX(double localX) {
        return toScreenX(localX);
    }

    public int tooltipY(double localY) {
        return toScreenY(localY);
    }

    public void enableScissor(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.enableScissor(toScreenX(x), toScreenY(y), toScreenX(x + width), toScreenY(y + height));
    }

    public void disableScissor(GuiGraphicsExtractor graphics) {
        graphics.disableScissor();
    }
}
