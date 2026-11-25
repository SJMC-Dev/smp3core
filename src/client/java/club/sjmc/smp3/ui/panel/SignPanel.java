package club.sjmc.smp3.ui.panel;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import club.sjmc.smp3.sign.SignPayloads;
import club.sjmc.smp3.ui.themes.PaperStyle;
import club.sjmc.smp3.ui.widget.PanelContext;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

/**
 * 签到日历面板。
 */
public final class SignPanel extends AbstractPanel {
	private static final int COLUMNS = 7;
	private static final float MONTH_SCALE = 1.5F;
	private static final int SIGNED = DyeColor.LIME.getFireworkColor() | 0xFF000000;
	private static final int TODAY = DyeColor.YELLOW.getFireworkColor() | 0xFF000000;
	private static final int FUTURE = DyeColor.LIGHT_GRAY.getFireworkColor() | 0xFF000000;
	private static final int MISSED = DyeColor.GRAY.getFireworkColor() | 0xFF000000;
	private SignPayloads.State state;
	private int panelX, panelY, panelWidth, panelHeight, cellWidth, cellHeight, gridLeft, gridTop;

	public SignPanel(SignPayloads.State state) { this.state = state; }

	public void accept(SignPayloads.State next) {
		boolean relayout = next.year() != state.year() || next.month() != state.month();
		state = next;
		if (relayout)
			onLayout();
	}

	@Override
	protected void onLayout() {
		int rows = rows();
		panelWidth = width() - 8;
		panelHeight = height();
		panelX = x();
		panelY = y() + 6;
		int baseCellWidth = Math.max(1, (panelWidth - 32) / COLUMNS);
		cellWidth = Math.max(1, Math.round(baseCellWidth * .75F));
		int availableHeight = Math.max(rows * 14, height() - 72);
		cellHeight = Math.max(14, Math.min(30, availableHeight / rows));
		gridLeft = panelX + (panelWidth - COLUMNS * cellWidth) / 2;
		gridTop = panelY + 42;
	}

	private LocalDate firstDay() { return LocalDate.of(state.year(), state.month(), 1); }

	private int offset() { return firstDay().getDayOfWeek().getValue() % COLUMNS; }

	private int length() { return firstDay().lengthOfMonth(); }

	private int rows() { return (offset() + length() + COLUMNS - 1) / COLUMNS; }

	private boolean signed(int day) { return state.signed(day); }

	@Override
	public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, PanelContext context) {
		var font = Minecraft.getInstance().font;
		String month = Month.of(state.month()).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
		Component title = Component.literal(month).withStyle(style -> style.withBold(true));
		int titleWidth = Math.round(font.width(title) * MONTH_SCALE);
		int titleX = panelX + (panelWidth - titleWidth) / 2;
		graphics.pose().pushMatrix();
		graphics.pose().translate(titleX, panelY + 18);
		graphics.pose().scale(MONTH_SCALE, MONTH_SCALE);
		graphics.text(font, title, 0, 0, PaperStyle.INK, false);
		graphics.pose().popMatrix();
		int offset = offset();
		for (int day = 1; day <= length(); day++) {
			int index = offset + day - 1;
			int left = gridLeft + index % COLUMNS * cellWidth;
			int top = gridTop + index / COLUMNS * cellHeight;
			boolean hovered = mouseX >= left && mouseX < left + cellWidth && mouseY >= top && mouseY < top + cellHeight;
			if (day == state.today() && hovered)
				graphics.fill(left + 2, top + 2, left + cellWidth - 2, top + cellHeight - 2, 0x227F5936);
			String number = Integer.toString(day);
			graphics.text(font, number, left + (cellWidth - font.width(number)) / 2,
			              top + (cellHeight - font.lineHeight) / 2, color(day), false);
			if (hovered)
				tooltip(graphics, day, mouseX, mouseY, context);
		}
	}

	private int color(int day) {
		if (signed(day))
			return SIGNED;
		if (day == state.today())
			return TODAY;
		return day > state.today() ? FUTURE : MISSED;
	}

	private void tooltip(GuiGraphicsExtractor graphics, int day, int mouseX, int mouseY, PanelContext context) {
		var font = Minecraft.getInstance().font;
		int color = color(day) & 0xFFFFFF;
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(state.month() + "月" + day + "日").withStyle(style -> style.withColor(color)));
		lines.add(Component.literal(status(day)).withStyle(style -> style.withColor(color)));
		boolean signed = signed(day);
		if (day == state.today() && !signed) {
			state.rewards().forEach(reward -> lines.add(Component.literal(reward).withStyle(ChatFormatting.GRAY)));
			lines.add(Component.literal("左键签到").withStyle(ChatFormatting.YELLOW));
		} else if (day == state.today() && state.pending()) {
			lines.add(Component.literal("左键重试奖励").withStyle(ChatFormatting.YELLOW));
		}
		graphics.setTooltipForNextFrame(font, lines, Optional.empty(), context.toScreenX(mouseX),
		                                context.toScreenY(mouseY));
	}

	private String status(int day) {
		if (day > state.today())
			return "未到";
		if (signed(day))
			return "已签到";
		return day == state.today() ? "今日待签到" : "已漏签";
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
			return false;
		int day = dayAt(event.x(), event.y());
		if (day != state.today() || signed(day) && !state.pending())
			return false;
		ClientPlayNetworking.send(new SignPayloads.Request(state.year(), state.month(), day));
		return true;
	}

	private int dayAt(double mouseX, double mouseY) {
		if (mouseX < gridLeft || mouseX >= gridLeft + COLUMNS * cellWidth || mouseY < gridTop ||
		    mouseY >= gridTop + rows() * cellHeight)
			return -1;
		int column = (int)((mouseX - gridLeft) / cellWidth);
		int row = (int)((mouseY - gridTop) / cellHeight);
		int day = row * COLUMNS + column - offset() + 1;
		return day >= 1 && day <= length() ? day : -1;
	}
}
