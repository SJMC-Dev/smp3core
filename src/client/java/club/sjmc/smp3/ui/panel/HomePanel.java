package club.sjmc.smp3.ui.panel;

import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import club.sjmc.smp3.home.HomePayloads.Action;
import club.sjmc.smp3.home.HomePayloads;
import club.sjmc.smp3.ui.themes.PaperStyle;
import club.sjmc.smp3.ui.widget.PanelContext;
import club.sjmc.smp3.ui.widget.PaperButton;
import club.sjmc.smp3.ui.widget.ScrollView;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class HomePanel extends AbstractPanel {
	private static final int ROW = 30;
	private static final int RIGHT_MARGIN = 12;
	private static final ItemStack ICON = new ItemStack(Items.COMPASS);
	private final ScrollView scrolling = new ScrollView();
	private HomePayloads.Catalog catalog;
	private String selected = "";
	private EditBox name;
	private Action confirmation;
	private int left, listTop, listBottom, innerWidth, controls;

	public HomePanel(HomePayloads.Catalog catalog) { this.catalog = catalog; }

	public void accept(HomePayloads.Catalog next) {
		catalog = next;
		if (catalog.entries().stream().noneMatch(entry -> entry.name().equals(selected)))
			selected = "";
		onLayout();
	}

	@Override
	protected void onLayout() {
		String input = name == null ? "" : name.getValue();
		boolean focused = name != null && name.isFocused();
		clearWidgets();
		left = x() + 18;
		innerWidth = width() - 36 - RIGHT_MARGIN;
		listTop = y() + 32;
		controls = bottom() - 86;
		listBottom = controls - 8;
		scrolling.layout(listTop, listBottom, catalog.entries().size() * ROW);
		if (!catalog.available())
			return;
		var font = Minecraft.getInstance().font;
		name = addWidget(new EditBox(font, left + 5, controls + 6, innerWidth - 8, 14, Component.literal("家园名称")));
		name.setMaxLength(64);
		name.setBordered(false);
		name.setTextShadow(false);
		name.setTextColor(PaperStyle.INK);
		name.setTextColorUneditable(PaperStyle.MUTED);
		name.setHint(Component.literal("输入家园名称").withStyle(style -> style.withoutShadow()));
		name.setValue(input);
		name.setFocused(focused);
		int cell = (innerWidth - 8) / 3;
		if (confirmation != null) {
			button(0, 0, cell, "确认" + (confirmation == Action.DELETE ? "删除" : "更新"), () -> {
				Action action = confirmation;
				confirmation = null;
				request(action);
			}, true);
			button(1, 0, cell, "取消", () -> {
				confirmation = null;
				onLayout();
			}, true);
		} else {
			button(0, 0, cell, "传送", () -> request(Action.TELEPORT), !selected.isEmpty());
			button(1, 0, cell, "新建", () -> request(Action.CREATE), true);
			button(2, 0, cell, "重命名", () -> request(Action.RENAME), !selected.isEmpty());
			button(0, 1, cell, "更新位置", () -> confirm(Action.UPDATE), !selected.isEmpty());
			button(1, 1, cell, "删除", () -> confirm(Action.DELETE), !selected.isEmpty());
			button(2, 1, cell, "刷新", () -> request(Action.REFRESH), true);
		}
	}

	private void button(int column, int row, int cell, String label, Runnable action, boolean active) {
		var button = addWidget(
		    new PaperButton(left + column * (cell + 4), controls + 23 + row * 24, cell, label, false, action));
		button.active = active;
	}

	private void confirm(Action action) {
		confirmation = action;
		onLayout();
	}

	private void request(Action action) {
		ClientPlayNetworking.send(new HomePayloads.Request(action, selected, name.getValue()));
		onLayout();
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, PanelContext context) {
		var font = Minecraft.getInstance().font;
		String message = confirmation == null            ? catalog.message()
		                 : confirmation == Action.DELETE ? "确认删除所选家园？"
		                                                 : "将所选家园更新到你当前的位置？";
		if (message.isEmpty())
			message = "我的家园 (" + catalog.entries().size() + "/" + catalog.limit() + ")";
		graphics.text(font, font.plainSubstrByWidth(message, innerWidth), left + 1, y() + 19, PaperStyle.INK, false);
		if (mouseX >= left && mouseX < left + innerWidth && mouseY >= y() + 18 && mouseY < y() + 28)
			graphics.setTooltipForNextFrame(font, List.of(Component.literal(message)), Optional.empty(),
			                                context.toScreenX(mouseX), context.toScreenY(mouseY));
		if (!catalog.available())
			return;
		graphics.enableScissor(left, listTop, left + innerWidth, listBottom);
		for (int i = 0; i < catalog.entries().size(); i++) {
			var entry = catalog.entries().get(i);
			int rowY = listTop + i * ROW - scrolling.scroll();
			if (rowY + ROW <= listTop || rowY >= listBottom)
				continue;
			boolean hovered = mouseX >= left && mouseX < left + innerWidth && mouseY >= rowY && mouseY < rowY + ROW;
			if (entry.name().equals(selected) || hovered)
				graphics.fill(left, rowY, left + innerWidth - 5, rowY + ROW - 1,
				              entry.name().equals(selected) ? 0x447F5936 : 0x227F5936);
			graphics.item(ICON, left + 3, rowY + 6);
			graphics.text(font, font.plainSubstrByWidth(entry.name(), innerWidth - 30), left + 25, rowY + 4,
			              PaperStyle.INK, false);
			String dimension = switch (entry.dimension()) {
				case "minecraft:overworld" -> "主世界";
				case "minecraft:the_nether" -> "下界";
				case "minecraft:the_end" -> "末地";
				default -> entry.dimension();
			};
			String location = dimension + "  " + entry.x() + ", " + entry.y() + ", " + entry.z();
			graphics.text(font, font.plainSubstrByWidth(location, innerWidth - 30), left + 25, rowY + 17,
			              PaperStyle.MUTED, false);
			if (hovered && mouseY >= listTop && mouseY < listBottom)
				graphics.setTooltipForNextFrame(font,
				                                List.of(Component.literal(entry.name()), Component.literal(location)),
				                                Optional.empty(), context.toScreenX(mouseX), context.toScreenY(mouseY));
		}
		if (catalog.entries().isEmpty())
			graphics.text(font, "还没有家园，可在当前位置新建", left + 1, listTop + 8, PaperStyle.MUTED, false);
		graphics.disableScissor();
		scrolling.render(graphics, left + innerWidth - 3);
		graphics.outline(left, controls, innerWidth, 20, PaperStyle.LINE);
		renderWidgets(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (mouseClickedWidgets(event, doubleClick))
			return true;
		if (!catalog.available() || event.button() != InputConstants.MOUSE_BUTTON_LEFT)
			return false;
		if (scrolling.beginDrag(event.x(), event.y(), left + innerWidth - 3))
			return true;
		if (event.x() < left || event.x() >= left + innerWidth || event.y() < listTop || event.y() >= listBottom)
			return false;
		int index = (int)(event.y() - listTop + scrolling.scroll()) / ROW;
		if (index >= catalog.entries().size())
			return false;
		selected = catalog.entries().get(index).name();
		name.setValue(selected);
		confirmation = null;
		onLayout();
		return true;
	}
	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return scrolling.mouseDragged(event.y()) || mouseDraggedWidgets(event, dx, dy);
	}
	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return scrolling.mouseReleased() || mouseReleasedWidgets(event);
	}
	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		if (y < listTop || y >= listBottom)
			return false;
		scrolling.scrollWheel(vertical, ROW);
		return true;
	}
	@Override
	public boolean keyPressed(KeyEvent event) {
		return keyPressedWidgets(event);
	}
	@Override
	public boolean charTyped(CharacterEvent event) {
		return charTypedWidgets(event);
	}
}
