package club.sjmc.smp3.ui.widget;

/**
 * 屏幕/面板内的整数矩形区域，统一命中测试与边界判断。
 */
public record Rect(int x, int y, int width, int height) {
	public int right() { return x + width; }

	public int bottom() { return y + height; }

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
	}

	public boolean isEmpty() { return width <= 0 || height <= 0; }
}
