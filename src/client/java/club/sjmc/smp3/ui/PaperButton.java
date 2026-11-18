package club.sjmc.smp3.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class PaperButton extends Button {
	private static final int INK = PaperStyle.INK, MUTED = PaperStyle.MUTED;
	private final boolean tab;

	PaperButton(int x, int y, int width, String text, boolean tab, Runnable action) {
		super(x, y, width, tab ? 18 : 20, Component.literal(text), button -> action.run(), DEFAULT_NARRATION);
		this.tab = tab;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		boolean selected = tab && !active;
		boolean highlighted = active && isHoveredOrFocused();
		int border = selected || highlighted ? 0xFF8C5D38 : 0xFFB7A17D;
		graphics.fill(getX(), getY(), getRight(), getBottom(),
		              selected      ? 0xCCD4BC91
		              : highlighted ? 0xCCE6D2AA
		                            : 0x55EBDDBF);
		if (tab) {
			graphics.horizontalLine(getX(), getRight() - 1, getBottom() - 1, border);
			if (selected)
				graphics.fill(getX() + 3, getBottom() - 2, getRight() - 3, getBottom(), 0xFF8C4B30);
		} else
			graphics.outline(getX(), getY(), getWidth(), getHeight(), border);
		if (isFocused() && active)
			graphics.outline(getX() + 2, getY() + 2, getWidth() - 4, getHeight() - 4, border);
		var font = Minecraft.getInstance().font;
		String label = font.plainSubstrByWidth(getMessage().getString(), getWidth() - 8);
		graphics.text(font, label, getX() + (getWidth() - font.width(label)) / 2,
		              getY() + (getHeight() - font.lineHeight) / 2, active || selected ? INK : MUTED, false);
	}
}
