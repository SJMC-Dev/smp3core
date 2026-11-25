package club.sjmc.smp3.ui.widget;

import club.sjmc.smp3.ui.themes.PaperStyle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class PaperButton extends Button {
	private static final int INK = PaperStyle.INK, MUTED = PaperStyle.MUTED;
	private final boolean tab;
	private boolean selected;

	public PaperButton(int x, int y, int width, String text, boolean tab, Runnable action) {
		super(x, y, width, tab ? 18 : 20, Component.literal(text), _ -> action.run(), DEFAULT_NARRATION);
		this.tab = tab;
	}

	public void setSelected(boolean selected) { this.selected = selected; }

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		boolean isSelected = tab && selected;
		boolean dimmed = !active && !isSelected;
		boolean hovered = mouseX >= getX() && mouseX < getRight() && mouseY >= getY() && mouseY < getBottom();
		boolean highlighted = active && (hovered || isHoveredOrFocused());
		int border = isSelected || highlighted ? 0xFF8C5D38 : PaperStyle.LINE;
		int background = isSelected ? 0xCCD4BC91 : highlighted ? 0xDDE8D6B4 : 0x55EBDDBF;
		if (dimmed) {
			border = 0x66B7A17D;
			background = 0x33EBDDBF;
		}
		graphics.fill(getX(), getY(), getRight(), getBottom(), background);
		if (tab) {
			graphics.horizontalLine(getX(), getRight() - 1, getBottom() - 1, border);
			if (isSelected)
				graphics.fill(getX() + 3, getBottom() - 2, getRight() - 3, getBottom(), 0xFF8C4B30);
		} else
			graphics.outline(getX(), getY(), getWidth(), getHeight(), border);
		if (isFocused() && active)
			graphics.outline(getX() + 2, getY() + 2, getWidth() - 4, getHeight() - 4, border);
		var font = Minecraft.getInstance().font;
		String label = font.plainSubstrByWidth(getMessage().getString(), getWidth() - 8);
		graphics.text(font, label, getX() + (getWidth() - font.width(label)) / 2 + 1,
		              getY() + (getHeight() - font.lineHeight) / 2 + 1,
		              dimmed ? 0x66827058 : active || isSelected ? INK : MUTED, false);
	}
}
