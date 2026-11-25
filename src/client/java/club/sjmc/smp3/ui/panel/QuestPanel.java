package club.sjmc.smp3.ui.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import club.sjmc.smp3.daily.DailyQuestPayloads;
import club.sjmc.smp3.ui.themes.PaperStyle;
import club.sjmc.smp3.ui.widget.ItemIcon;
import club.sjmc.smp3.ui.widget.PanelContext;
import club.sjmc.smp3.ui.widget.ScrollView;
import club.sjmc.smp3.ui.widget.WrappedText;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 每日任务面板：左侧四张任务卡，右侧详情。
 */
public final class QuestPanel extends AbstractPanel {
	private static final int CARD_GAP = 10;
	private static final int DONE = 0xFF3B6B3B;
	private static final int PENDING = 0xFF8A5A22;
	private DailyQuestPayloads.Catalog catalog;
	private int selected;
	private final ScrollView detailScroll = new ScrollView();
	private int panelX, panelY, panelWidth, panelHeight, split, top, bottom, leftX, leftRight, leftWidth, detailX,
	    detailWidth, detailTop, detailContentHeight, cardWidth, cardHeight, gridLeft;

	public QuestPanel(DailyQuestPayloads.Catalog catalog) { this.catalog = catalog; }

	public void accept(DailyQuestPayloads.Catalog next) {
		boolean sameTask = selected < catalog.tasks().size() && selected < next.tasks().size() &&
		                   catalog.tasks().get(selected).token().equals(next.tasks().get(selected).token());
		catalog = next;
		if (!sameTask) {
			detailScroll.setScroll(0);
			detailContentHeight = 0;
		}
		if (selected >= catalog.tasks().size())
			selected = catalog.tasks().isEmpty() ? 0 : catalog.tasks().size() - 1;
	}

	@Override
	protected void onLayout() {
		detailScroll.mouseReleased();
		panelWidth = width();
		panelHeight = height();
		panelX = x() + (width() - panelWidth) / 2;
		panelY = y() + (height() - panelHeight) / 2;
		split = panelX + panelWidth * 11 / 20 - 4;
		leftX = panelX + 18;
		leftRight = panelX + panelWidth * 11 / 20 - 14;
		leftWidth = leftRight - leftX;
		detailX = split + 10;
		detailWidth = panelX + panelWidth - 24 - detailX;
		top = panelY + 18;
		bottom = panelY + panelHeight - 18;
		detailTop = top;
		cardWidth = Math.max(1, (leftWidth - CARD_GAP) / 2 * 9 / 10);
		cardHeight = Math.max(1, (bottom - top - CARD_GAP) / 2);
		gridLeft = leftX + Math.max(0, (leftWidth - (cardWidth * 2 + CARD_GAP)) / 2);
	}

	private int cardLeft(int slot) { return gridLeft + slot % 2 * (cardWidth + CARD_GAP); }

	private int cardTop(int slot) { return top + slot / 2 * (cardHeight + CARD_GAP); }

	@Override
	public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, PanelContext context) {
		var font = Minecraft.getInstance().font;
		graphics.fill(split, top, split + 2, bottom, 0x66836B48);
		graphics.fill(split + 2, top, split + 5, bottom, 0x22765A37);
		if (catalog.tasks().isEmpty()) {
			graphics.text(font, "暂无任务", leftX, top, PaperStyle.MUTED, false);
		} else {
			for (int slot = 0; slot < Math.min(4, catalog.tasks().size()); slot++)
				drawCard(graphics, slot, mouseX, mouseY, context);
			int detail = Math.min(selected, catalog.tasks().size() - 1);
			drawDetail(graphics, catalog.tasks().get(detail));
		}
	}

	private void drawCard(GuiGraphicsExtractor graphics, int slot, int mouseX, int mouseY, PanelContext context) {
		var font = Minecraft.getInstance().font;
		var task = catalog.tasks().get(slot);
		int left = cardLeft(slot), cardTop = cardTop(slot);
		boolean selectedCard = slot == selected;
		boolean hovered = insideCard(slot, mouseX, mouseY);
		graphics.fill(left, cardTop, left + cardWidth, cardTop + cardHeight, selectedCard ? 0x55EBDDBF : 0x22EBDDBF);
		drawCardBorder(graphics, left, cardTop, cardWidth, cardHeight, selectedCard, hovered);
		graphics.fill(left + 4, cardTop + 2, left + 7, cardTop + cardHeight - 2, difficultyColor(task.difficulty()));
		float iconScale = Math.min(3F, Math.max(1F, Math.min((cardWidth - 8) / 16F, cardHeight * .45F / 16F)));
		int iconSize = Math.round(16 * iconScale);
		int iconX = left + cardWidth / 2 - iconSize / 2, iconY = cardTop + cardHeight * 2 / 5 - iconSize / 2 - 2;
		drawIcon(graphics, ItemIcon.frame(task.icons()), iconX, iconY, iconScale);
		String progress = task.progress() + " / " + task.target();
		int progressY = iconY + iconSize + 8;
		String progressLabel = font.plainSubstrByWidth(progress, Math.max(1, cardWidth - 10));
		graphics.text(font, progressLabel, left + (cardWidth - font.width(progressLabel)) / 2 + 1, progressY + 1,
		              task.complete() ? DONE : PaperStyle.MUTED, false);
		int rerollX = left + cardWidth - 16, rerollY = cardTop + cardHeight - 16;
		if (task.complete()) {
			graphics.fill(left, cardTop, left + cardWidth, cardTop + cardHeight, 0x44F2E7CF);
			String check = "✔";
			float scale = 1.5F;
			int width = font.width(check);
			graphics.pose().pushMatrix();
			graphics.pose().translate(rerollX + 8 - width * scale / 2, rerollY + 1.5F);
			graphics.pose().scale(scale, scale);
			graphics.text(font, check, 0, 0, 0xFF3B8C3B, false);
			graphics.pose().popMatrix();
		} else {
			drawRerollIcon(graphics, rerollX, rerollY, catalog.remaining() == 0 ? 0xFFAD4434 : PaperStyle.INK);
			if (mouseX >= rerollX && mouseX < rerollX + 16 && mouseY >= rerollY && mouseY < rerollY + 16) {
				List<Component> lines = new ArrayList<>();
				lines.add(Component.literal("剩余更换次数：" + catalog.remaining() + " / " + catalog.limit()));
				if (!task.rerollAvailable())
					lines.add(
					    Component.literal(catalog.remaining() == 0 ? "今日刷新次数已用完" : "没有可替换的同难度任务"));
				graphics.setTooltipForNextFrame(font, lines, Optional.empty(), context.toScreenX(mouseX),
				                                context.toScreenY(mouseY));
			}
		}
	}

	private void drawRerollIcon(GuiGraphicsExtractor graphics, int rerollX, int rerollY, int color) {
		var font = Minecraft.getInstance().font;
		String icon = "🗘";
		int offset = Math.max(0, (16 - font.width(icon)) / 2);
		graphics.text(font, icon, rerollX + offset, rerollY + 4, color, false);
	}

	private void drawIcon(GuiGraphicsExtractor graphics, ItemStack icon, int iconX, int iconY, float scale) {
		var font = Minecraft.getInstance().font;
		graphics.pose().pushMatrix();
		graphics.pose().translate(iconX, iconY);
		graphics.pose().scale(scale, scale);
		if (!icon.isEmpty()) {
			graphics.item(icon, 0, 0);
		} else {
			String fallback = "◆";
			graphics.text(font, fallback, (16 - font.width(fallback)) / 2, 4, PaperStyle.INK, false);
		}
		graphics.pose().popMatrix();
	}

	private void drawCardBorder(GuiGraphicsExtractor graphics, int left, int top, int width, int height,
	                            boolean selected, boolean hovered) {
		int edge = selected ? 0x448C5B38 : hovered ? 0x338C5B38 : 0x22765A37;
		int glow = selected ? 0xAA8C5B38 : hovered ? 0x888C5B38 : 0x66836B48;
		graphics.fill(left, top, left + width, top + 2, edge);
		graphics.fill(left, top + height - 2, left + width, top + height, edge);
		graphics.fill(left, top, left + 2, top + height, edge);
		graphics.fill(left + width - 2, top, left + width, top + height, edge);
		graphics.fill(left + 2, top + 2, left + width - 2, top + 4, glow);
		graphics.fill(left + 2, top + height - 4, left + width - 2, top + height - 2, glow);
		graphics.fill(left + 2, top + 2, left + 4, top + height - 2, glow);
		graphics.fill(left + width - 4, top + 2, left + width - 2, top + height - 2, glow);
	}

	private void drawDetail(GuiGraphicsExtractor graphics, DailyQuestPayloads.Task task) {
		var font = Minecraft.getInstance().font;
		detailScroll.layout(detailTop, bottom, detailContentHeight);
		int start = detailTop - detailScroll.scroll();
		graphics.enableScissor(detailX, detailTop, detailX + detailWidth, bottom);
		int nameWidth = Math.max(1, detailWidth);
		int nameCursor =
		    WrappedText.draw(graphics, font, Component.literal(task.name()).withStyle(style -> style.withBold(true)),
			                 detailX, start, nameWidth, PaperStyle.INK);
		int cursor = nameCursor + 2;
		String difficulty = "难度 " + task.difficulty() + (task.tier() == 0 ? "" : " · " + task.tier() + "级");
		graphics.text(font, difficulty, detailX, cursor, difficultyColor(task.difficulty()), false);
		cursor += font.lineHeight + 2;
		String status = task.claimed() ? "奖励已结算" : task.complete() ? "已完成，奖励待发放" : "未完成";
		graphics.text(font, status, detailX, cursor,
		              task.claimed()    ? DONE
		              : task.complete() ? PENDING
		                                : PaperStyle.MUTED,
		              false);
		cursor += font.lineHeight + 5;
		cursor = heading(graphics, "完成条件", cursor);
		for (Component line : task.conditions())
			cursor = WrappedText.draw(graphics, font, line, detailX, cursor, detailWidth, PaperStyle.MUTED);
		cursor = heading(graphics, "任务奖励", cursor + 2);
		for (Component line : task.rewards())
			cursor = WrappedText.draw(graphics, font, line, detailX, cursor, detailWidth, PaperStyle.MUTED);
		detailContentHeight = cursor - start;
		graphics.disableScissor();
		detailScroll.render(graphics, detailX + detailWidth - 3);
	}

	private static int difficultyColor(String difficulty) {
		return switch (difficulty) {
			case "C" -> 0xFF5FAE4A;
			case "B" -> 0xFF4A78C2;
			default -> 0xFFC04A4A;
		};
	}

	private int heading(GuiGraphicsExtractor graphics, String text, int cursor) {
		var font = Minecraft.getInstance().font;
		graphics.text(font, Component.literal(text).withStyle(style -> style.withBold(true)), detailX, cursor,
		              PaperStyle.INK, false);
		return cursor + font.lineHeight + 2;
	}

	private boolean insideCard(int slot, double mouseX, double mouseY) {
		int left = cardLeft(slot), cardTop = cardTop(slot);
		return mouseX >= left && mouseX < left + cardWidth && mouseY >= cardTop && mouseY < cardTop + cardHeight;
	}

	private int cardAt(double mouseX, double mouseY) {
		for (int slot = 0; slot < Math.min(4, catalog.tasks().size()); slot++)
			if (insideCard(slot, mouseX, mouseY))
				return slot;
		return -1;
	}

	private boolean overReroll(int slot, double mouseX, double mouseY) {
		int rerollX = cardLeft(slot) + cardWidth - 16, rerollY = cardTop(slot) + cardHeight - 16;
		return mouseX >= rerollX && mouseX < rerollX + 16 && mouseY >= rerollY && mouseY < rerollY + 16;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
			return false;
		if (detailScroll.beginDrag(event.x(), event.y(), detailX + detailWidth - 3))
			return true;
		int slot = cardAt(event.x(), event.y());
		if (slot < 0)
			return false;
		var task = catalog.tasks().get(slot);
		if (overReroll(slot, event.x(), event.y())) {
			if (!task.complete() && task.rerollAvailable())
				ClientPlayNetworking.send(new DailyQuestPayloads.Reroll(catalog.date(), slot, task.token()));
			else {
				selected = slot;
				detailScroll.setScroll(0);
			}
			return true;
		}
		selected = slot;
		detailScroll.setScroll(0);
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (mouseX >= detailX && mouseX < detailX + detailWidth && mouseY >= detailTop && mouseY < bottom) {
			detailScroll.scrollWheel(vertical, 16);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return detailScroll.mouseDragged(event.y());
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return detailScroll.mouseReleased();
	}
}
