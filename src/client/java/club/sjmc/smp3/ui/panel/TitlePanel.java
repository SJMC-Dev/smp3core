package club.sjmc.smp3.ui.panel;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import club.sjmc.smp3.title.TitlePayloads;
import club.sjmc.smp3.ui.themes.PaperStyle;
import club.sjmc.smp3.ui.widget.PanelContext;
import club.sjmc.smp3.ui.widget.Rect;
import club.sjmc.smp3.ui.widget.ScrollView;
import club.sjmc.smp3.ui.widget.WrappedText;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 称号列表面板：左侧网格，右侧详情。
 */
public final class TitlePanel extends AbstractPanel {
	private static final int CELL_HEIGHT = 24;
	private static final int OBTAINED = 0xFF55FF55;
	private static final int MISSING = 0xFFAAAAAA;
	private static final int ACTIVE = 0xFFFFAA00;
	private final TitlePayloads.Catalog catalog;
	private final List<ItemStack> icons;
	private final List<Component> names;
	private final List<List<Component>> descriptions;
	private final boolean embedded;
	private final ScrollView scrolling = new ScrollView();
	private int selected;
	private int panelX, panelY, panelWidth, panelHeight, split, top, bottom, leftX, leftRight, leftWidth, detailX,
	    detailWidth, cellWidth, columns;

	public TitlePanel(TitlePayloads.Catalog catalog) { this(catalog, false); }

	public TitlePanel(TitlePayloads.Catalog catalog, boolean embedded) {
		this.catalog = catalog;
		this.embedded = embedded;
		this.icons = catalog.entries().stream().map(entry -> {
			try {
				var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(entry.item()));
				return new ItemStack(item == Items.AIR ? Items.NAME_TAG : item);
			} catch (RuntimeException error) {
				return new ItemStack(Items.NAME_TAG);
			}
		}).toList();
		this.names = catalog.entries().stream().map(entry -> readable(entry.name())).toList();
		this.descriptions =
		    catalog.entries()
		        .stream()
		        .map(entry -> entry.description().stream().map(TitlePanel::readable).toList())
		        .toList();
		this.selected = activeIndex();
	}

	@Override
	protected void onLayout() {
		panelWidth = embedded ? width() : Math.min(620, width() - 32);
		panelHeight = embedded ? height() : Math.min(380, height() - 32);
		panelX = x() + (width() - panelWidth) / 2;
		panelY = y() + (height() - panelHeight) / 2;
		split = panelX + panelWidth * 2 / 3;
		leftX = panelX + 18;
		leftRight = split - 14;
		leftWidth = leftRight - leftX;
		detailX = split + 10;
		detailWidth = panelX + panelWidth - 18 - detailX;
		top = panelY + 18;
		bottom = panelY + panelHeight - 18;
		columns = Math.max(1, leftWidth / 80);
		cellWidth = Math.max(1, leftWidth / columns);
		scrolling.layout(top, bottom, rows() * CELL_HEIGHT);
		if (selected >= catalog.entries().size())
			selected = catalog.entries().isEmpty() ? -1 : catalog.entries().size() - 1;
	}

	private int rows() {
		int count = catalog.entries().size();
		return columns == 0 ? 0 : (count + columns - 1) / columns;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, PanelContext context) {
		var font = Minecraft.getInstance().font;
		if (!embedded) {
			graphics.fill(x(), y(), right(), bottom(), 0x66000000);
			PaperStyle.frame(graphics, panelX, panelY, panelWidth, panelHeight);
		}
		graphics.fill(split, top, split + 2, bottom, 0x66836B48);
		graphics.fill(split + 2, top, split + 5, bottom, 0x22765A37);
		if (catalog.entries().isEmpty()) {
			graphics.text(font, "暂无称号", leftX, top, PaperStyle.MUTED, false);
		} else {
			drawGrid(graphics, mouseX, mouseY);
			drawDetail(graphics);
		}
	}

	private void drawGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		var font = Minecraft.getInstance().font;
		graphics.enableScissor(leftX, top, leftRight, bottom);
		for (int i = 0; i < catalog.entries().size(); i++) {
			var entry = catalog.entries().get(i);
			int left = leftX + i % columns * cellWidth;
			int cellTop = top + i / columns * CELL_HEIGHT - scrolling.scroll();
			if (cellTop + CELL_HEIGHT <= top || cellTop >= bottom)
				continue;
			boolean hovered = mouseX >= left && mouseX < left + cellWidth && mouseY >= cellTop &&
			                  mouseY < cellTop + CELL_HEIGHT;
			if (i == selected || hovered)
				graphics.fill(left, cellTop, left + cellWidth, cellTop + CELL_HEIGHT,
				              i == selected ? 0x447F5936 : 0x227F5936);
			int iconX = left + 4, iconY = cellTop + (CELL_HEIGHT - 16) / 2;
			graphics.item(icons.get(i), iconX, iconY);
			var lines = font.split(names.get(i), Math.max(1, cellWidth - 25));
			if (!lines.isEmpty())
				graphics.text(font, lines.getFirst(), left + 21, cellTop + (CELL_HEIGHT - font.lineHeight) / 2,
				              entry.obtained() ? PaperStyle.INK : 0x99827058, false);
			if (!entry.obtained())
				graphics.fill(left, cellTop, left + cellWidth, cellTop + CELL_HEIGHT, 0x44F2E7CF);
			if (entry.id().equals(catalog.activeId()))
				graphics.outline(left, cellTop, cellWidth, CELL_HEIGHT, 0xFF8C4B30);
		}
		graphics.disableScissor();
		scrolling.render(graphics, leftRight + 4);
	}

	private void drawDetail(GuiGraphicsExtractor graphics) {
		if (selected < 0 || selected >= catalog.entries().size())
			return;
		var font = Minecraft.getInstance().font;
		var entry = catalog.entries().get(selected);
		graphics.enableScissor(detailX, top, detailX + detailWidth, bottom);
		int nameWidth = Math.max(1, detailWidth);
		int nameCursor = WrappedText.draw(
		    graphics, font, names.get(selected).copy().withStyle(style -> style.withBold(true)), detailX, top,
		    nameWidth, entry.obtained() ? PaperStyle.INK : 0x99827058, bottom);
		int cursor = nameCursor + 2;
		graphics.text(font, entry.obtained() ? "已获得" : "未获得", detailX, cursor,
		              entry.obtained() ? OBTAINED : MISSING, false);
		cursor += font.lineHeight + 4;
		if (entry.id().equals(catalog.activeId())) {
			graphics.text(font, "当前佩戴", detailX, cursor, ACTIVE, false);
			cursor += font.lineHeight + 4;
		}
		for (Component line : descriptions.get(selected))
			cursor = WrappedText.draw(graphics, font, line, detailX, cursor, detailWidth, PaperStyle.MUTED, bottom);
		graphics.disableScissor();
	}

	private int activeIndex() {
		for (int i = 0; i < catalog.entries().size(); i++)
			if (catalog.entries().get(i).id().equals(catalog.activeId()))
				return i;
		return catalog.entries().isEmpty() ? -1 : 0;
	}

	private static Component readable(Component source) {
		MutableComponent copy = MutableComponent.create(source.getContents());
		copy.setStyle(readable(source.getStyle()));
		for (Component sibling : source.getSiblings())
			copy.append(readable(sibling));
		return copy;
	}

	private static Style readable(Style style) {
		TextColor color = style.getColor();
		if (color != null && luminance(color.getValue()) >= .85F)
			return style.withColor(PaperStyle.INK & 0xFFFFFF);
		return style;
	}

	private static float luminance(int rgb) {
		float red = ((rgb >> 16) & 0xFF) / 255F;
		float green = ((rgb >> 8) & 0xFF) / 255F;
		float blue = (rgb & 0xFF) / 255F;
		return .2126F * red + .7152F * green + .0722F * blue;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
			return false;
		int index = cellAt(event.x(), event.y());
		if (index < 0)
			return false;
		selected = index;
		return true;
	}

	private int cellAt(double mouseX, double mouseY) {
		if (!new Rect(leftX, top, leftRight - leftX, bottom - top).contains(mouseX, mouseY))
			return -1;
		int column = (int)((mouseX - leftX) / cellWidth);
		if (column >= columns)
			return -1;
		int row = (int)((mouseY - top + scrolling.scroll()) / CELL_HEIGHT);
		int index = row * columns + column;
		return index >= 0 && index < catalog.entries().size() ? index : -1;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (mouseX >= leftX && mouseX < leftRight && mouseY >= top && mouseY < bottom) {
			scrolling.scrollWheel(vertical, CELL_HEIGHT);
			return true;
		}
		return false;
	}
}
