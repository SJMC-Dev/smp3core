package club.sjmc.smp3.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.renderer.RenderPipelines;

final class PaperStyle {
	static final int INK = 0xFF443323, MUTED = 0xFF827058, LINE = 0xFFB7A17D;
	private PaperStyle() {}
	static void frame(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		graphics.fill(x + 4, y + 5, x + width + 4, y + height + 5, 0x77000000);
		graphics.fillGradient(x, y, x + width, y + height, 0xFF79523A, 0xFF3F2A20);
		graphics.outline(x + 1, y + 1, width - 2, height - 2, 0xFFAD8053);
		graphics.fill(x + 5, y + 4, x + 9, y + height - 4, 0xFF39271F);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BookViewScreen.BOOK_LOCATION, x + 11, y + 8, 32, 20, width - 20,
		              height - 16, 128, 140, 256, 256);
	}
	static void scrollbar(GuiGraphicsExtractor graphics, int x, int top, int bottom, int content, int scroll) {
		int height = bottom - top;
		if (content <= height || height <= 0)
			return;
		int thumb = Math.max(12, height * height / content);
		int y = top + (height - thumb) * scroll / (content - height);
		graphics.fill(x, top, x + 2, bottom, 0x44765A37);
		graphics.fill(x, y, x + 2, y + thumb, 0xFF9B7B51);
	}
}
