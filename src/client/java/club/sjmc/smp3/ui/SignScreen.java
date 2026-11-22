package club.sjmc.smp3.ui;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import club.sjmc.smp3.sign.SignPayloads;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

public final class SignScreen extends Screen {
	private static final int COLUMNS = 7;
	private static final float MONTH_SCALE = 1.5F;
	private static final int SIGNED = DyeColor.LIME.getFireworkColor() | 0xFF000000;
	private static final int TODAY = DyeColor.YELLOW.getFireworkColor() | 0xFF000000;
	private static final int FUTURE = DyeColor.LIGHT_GRAY.getFireworkColor() | 0xFF000000;
	private static final int MISSED = DyeColor.GRAY.getFireworkColor() | 0xFF000000;
	private SignPayloads.State state;
	private boolean embedded;
	private int originX, originY;
	private int x, y, panelWidth, panelHeight, cellWidth, cellHeight, gridLeft, gridTop;

	SignScreen(SignPayloads.State state) {
		super(Component.literal("签到"));
		this.state = state;
	}

	void accept(SignPayloads.State next) {
		boolean relayout = next.year() != state.year() || next.month() != state.month();
		state = next;
		if (relayout && minecraft != null)
			rebuildWidgets();
	}

	void embed(int originX, int originY, int width, int height) {
		this.originX = originX;
		this.originY = originY;
		this.embedded = true;
		init(width, height);
	}

	@Override
	protected void init() {
		int rows = rows();
		if (embedded) {
			panelWidth = width - 8;
			panelHeight = height;
			x = originX;
			y = originY + 6;
		} else {
			panelWidth = Math.min(320, Math.max(80, width - 32));
			int availableHeight = Math.max(rows * 14, height - 72);
			cellHeight = Math.max(14, Math.min(30, availableHeight / rows));
			panelHeight = 54 + rows * cellHeight;
			x = originX + (width - panelWidth) / 2;
			y = originY + (height - panelHeight) / 2;
		}
		int baseCellWidth = Math.max(1, (panelWidth - 32) / COLUMNS);
		cellWidth = Math.max(1, Math.round(baseCellWidth * .75F));
		if (embedded) {
			int availableHeight = Math.max(rows * 14, height - 72);
			cellHeight = Math.max(14, Math.min(30, availableHeight / rows));
		}
		gridLeft = x + (panelWidth - COLUMNS * cellWidth) / 2;
		gridTop = y + 42;
	}

	private LocalDate firstDay() { return LocalDate.of(state.year(), state.month(), 1); }
	private int offset() { return firstDay().getDayOfWeek().getValue() % COLUMNS; }
	private int length() { return firstDay().lengthOfMonth(); }
	private int rows() { return (offset() + length() + COLUMNS - 1) / COLUMNS; }
	private boolean signed(int day) { return state.signed(day); }

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (!embedded) {
			graphics.fill(0, 0, width, height, 0x66000000);
			PaperStyle.frame(graphics, x, y, panelWidth, panelHeight);
		}
		String month = Month.of(state.month()).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
		Component title = Component.literal(month).withStyle(style -> style.withBold(true));
		int titleWidth = Math.round(font.width(title) * MONTH_SCALE);
		int titleX = x + (panelWidth - titleWidth) / 2;
		graphics.pose().pushMatrix();
		graphics.pose().translate(titleX, y + 18);
		graphics.pose().scale(MONTH_SCALE, MONTH_SCALE);
		graphics.text(font, title, 0, 0, PaperStyle.INK, false);
		graphics.pose().popMatrix();
		int offset = offset();
		for (int day = 1; day <= length(); day++) {
			int index = offset + day - 1;
			int left = gridLeft + index % COLUMNS * cellWidth;
			int top = gridTop + index / COLUMNS * cellHeight;
			boolean hovered = mouseX >= left && mouseX < left + cellWidth && mouseY >= top &&
			                  mouseY < top + cellHeight;
			if (day == state.today() && hovered)
				graphics.fill(left + 2, top + 2, left + cellWidth - 2, top + cellHeight - 2, 0x227F5936);
			String number = Integer.toString(day);
			graphics.text(font, number, left + (cellWidth - font.width(number)) / 2,
			              top + (cellHeight - font.lineHeight) / 2, color(day), false);
			if (hovered)
				tooltip(graphics, day, mouseX, mouseY);
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private int color(int day) {
		if (signed(day))
			return SIGNED;
		if (day == state.today())
			return TODAY;
		return day > state.today() ? FUTURE : MISSED;
	}

	private void tooltip(GuiGraphicsExtractor graphics, int day, int mouseX, int mouseY) {
		int color = color(day) & 0xFFFFFF;
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(state.month() + "月" + day + "日").withStyle(style -> style.withColor(color)));
		lines.add(Component.literal(status(day)).withStyle(style -> style.withColor(color)));
		boolean signed = signed(day);
		if (day == state.today() && !signed) {
			state.rewards().forEach(reward -> lines.add(Component.literal(reward).withStyle(ChatFormatting.GRAY)));
			lines.add(Component.literal("左键签到").withStyle(ChatFormatting.YELLOW));
		} else if (day == state.today() && signed && state.pending()) {
			lines.add(Component.literal("左键重试奖励").withStyle(ChatFormatting.YELLOW));
		}
		graphics.setTooltipForNextFrame(font, lines, Optional.empty(), MenuScreen.tooltipX(mouseX),
		                                MenuScreen.tooltipY(mouseY));
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
		if (super.mouseClicked(event, doubleClick))
			return true;
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

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {}
}
