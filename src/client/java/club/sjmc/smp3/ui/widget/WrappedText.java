package club.sjmc.smp3.ui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * 按宽度折行并逐行绘制文本，返回下一行的 y 坐标。
 */
public final class WrappedText {
	private WrappedText() {}

	public static int draw(GuiGraphicsExtractor graphics, Font font, Component text, int x, int cursor, int width,
	                       int color) {
		return draw(graphics, font, text, x, cursor, width, color, Integer.MAX_VALUE);
	}

	public static int draw(GuiGraphicsExtractor graphics, Font font, Component text, int x, int cursor, int width,
	                       int color, int bottom) {
		for (var line : font.split(text, width)) {
			if (cursor + font.lineHeight > bottom)
				break;
			graphics.text(font, line, x, cursor, color, false);
			cursor += font.lineHeight + 2;
		}
		return cursor;
	}
}
