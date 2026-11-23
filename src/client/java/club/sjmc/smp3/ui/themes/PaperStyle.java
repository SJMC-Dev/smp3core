package club.sjmc.smp3.ui.themes;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

public final class PaperStyle {
	public static final int INK = PaperTheme.INK, MUTED = PaperTheme.MUTED, LINE = PaperTheme.LINE;
	private PaperStyle() {}

	public static void frame(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		graphics.fill(x + PaperTheme.FRAME_SHADOW_X, y + PaperTheme.FRAME_SHADOW_Y,
		              x + width + PaperTheme.FRAME_SHADOW_X, y + height + PaperTheme.FRAME_SHADOW_Y, PaperTheme.SHADOW);
		graphics.fillGradient(x, y, x + width, y + height, PaperTheme.FRAME_TOP, PaperTheme.FRAME_BOTTOM);
		graphics.outline(x + PaperTheme.FRAME_BORDER, y + PaperTheme.FRAME_BORDER, width - 2 * PaperTheme.FRAME_BORDER,
		                 height - 2 * PaperTheme.FRAME_BORDER, PaperTheme.FRAME_OUTLINE);
		graphics.fill(x + PaperTheme.FRAME_SPINE_X, y + PaperTheme.FRAME_SPINE_WIDTH,
		              x + PaperTheme.FRAME_SPINE_X + PaperTheme.FRAME_SPINE_WIDTH,
		              y + height - PaperTheme.FRAME_SPINE_WIDTH, PaperTheme.SPINE);
		graphics.blit(RenderPipelines.GUI_TEXTURED, PaperTheme.BOOK_TEXTURE, x + PaperTheme.FRAME_TEXT_X,
		              y + PaperTheme.FRAME_TEXT_Y, PaperTheme.BOOK_TEXTURE_U, PaperTheme.BOOK_TEXTURE_V,
		              width - PaperTheme.FRAME_TEXT_INSET_X, height - PaperTheme.FRAME_TEXT_INSET_Y,
		              PaperTheme.BOOK_TEXTURE_WIDTH, PaperTheme.BOOK_TEXTURE_HEIGHT,
		              PaperTheme.BOOK_TEXTURE_TEXTURE_WIDTH, PaperTheme.BOOK_TEXTURE_TEXTURE_HEIGHT);
	}

	public static void scrollbar(GuiGraphicsExtractor graphics, int x, int top, int bottom, int content, int scroll) {
		var bar = new Scrollbar(x, top, bottom, content);
		if (bar.range() == 0)
			return;
		int y = bar.thumbTop(scroll);
		graphics.fill(x, top, x + PaperTheme.SCROLLBAR_WIDTH, bottom, PaperTheme.SCROLLBAR_TRACK);
		graphics.fill(x, y, x + PaperTheme.SCROLLBAR_WIDTH, y + bar.thumbHeight(), PaperTheme.SCROLLBAR_THUMB);
	}

	public record Scrollbar(int x, int top, int bottom, int content) {
		public int range() { return bottom > top ? Math.max(0, content - (bottom - top)) : 0; }

		public int thumbHeight() {
			return Math.min(bottom - top, Math.max(PaperTheme.SCROLLBAR_MIN_THUMB,
			                                       (bottom - top) * (bottom - top) / Math.max(1, content)));
		}

		public int thumbTop(int scroll) {
			return top + (bottom - top - thumbHeight()) * Math.clamp(scroll, 0, range()) / Math.max(1, range());
		}

		public boolean contains(double mouseX, double mouseY) {
			return range() > 0 && mouseX >= x - 4 && mouseX < x + 6 && mouseY >= top && mouseY < bottom;
		}

		public int position(double mouseY, double grab) {
			return Math.clamp(
			    (int)Math.round((mouseY - top - grab) * range() / Math.max(1, bottom - top - thumbHeight())), 0,
			    range());
		}
	}
}
