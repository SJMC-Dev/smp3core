package club.sjmc.smp3.ui.themes;

import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.resources.Identifier;

/**
 * 纸张风格的全部视觉常量。绘制逻辑放在 {@link PaperStyle}，本类只提供数据。
 */
public final class PaperTheme {
	// 文字与线条
	public static final int INK = 0xFF443323;
	public static final int MUTED = 0xFF827058;
	public static final int LINE = 0xFFB7A17D;

	// 外框
	public static final int SHADOW = 0x77000000;
	public static final int FRAME_TOP = 0xFF79523A;
	public static final int FRAME_BOTTOM = 0xFF3F2A20;
	public static final int FRAME_OUTLINE = 0xFFAD8053;
	public static final int SPINE = 0xFF39271F;
	public static final int FRAME_SHADOW_X = 4;
	public static final int FRAME_SHADOW_Y = 5;
	public static final int FRAME_BORDER = 1;
	public static final int FRAME_SPINE_X = 5;
	public static final int FRAME_SPINE_WIDTH = 4;
	public static final int FRAME_TEXT_X = 11;
	public static final int FRAME_TEXT_Y = 8;
	public static final int FRAME_TEXT_INSET_X = 20;
	public static final int FRAME_TEXT_INSET_Y = 16;

	// 书页纹理
	public static final Identifier BOOK_TEXTURE = BookViewScreen.BOOK_LOCATION;
	public static final int BOOK_TEXTURE_U = 32;
	public static final int BOOK_TEXTURE_V = 20;
	public static final int BOOK_TEXTURE_WIDTH = 128;
	public static final int BOOK_TEXTURE_HEIGHT = 140;
	public static final int BOOK_TEXTURE_TEXTURE_WIDTH = 256;
	public static final int BOOK_TEXTURE_TEXTURE_HEIGHT = 256;

	// 滚动条
	public static final int SCROLLBAR_WIDTH = 2;
	public static final int SCROLLBAR_MIN_THUMB = 12;
	public static final int SCROLLBAR_TRACK = 0x44765A37;
	public static final int SCROLLBAR_THUMB = 0xFF9B7B51;

	private PaperTheme() {}
}
