package club.sjmc.smp3.ui.widget;

/**
 * 面板在宿主中的坐标上下文：把面板局部坐标映射到屏幕坐标，供 tooltip 使用。
 */
public record PanelContext(int originX, int originY) {
	public int toScreenX(double localX) { return Math.round((float)(originX + localX)); }

	public int toScreenY(double localY) { return Math.round((float)(originY + localY)); }
}
