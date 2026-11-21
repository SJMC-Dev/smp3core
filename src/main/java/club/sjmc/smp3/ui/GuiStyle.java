package club.sjmc.smp3.ui;

import java.time.format.DateTimeFormatter;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import club.sjmc.smp3.time.ServerDates;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class GuiStyle {
	private static final DateTimeFormatter RESET_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");
	public static final ChatFormatting TITLE = ChatFormatting.WHITE;
	public static final ChatFormatting HEADING = ChatFormatting.GOLD;
	public static final ChatFormatting ACTION = ChatFormatting.YELLOW;
	public static final ChatFormatting ACTIVE = ChatFormatting.WHITE;
	public static final ChatFormatting READY = ChatFormatting.GREEN;
	public static final ChatFormatting MUTED = ChatFormatting.GRAY;
	public static final ChatFormatting ERROR = ChatFormatting.RED;

	private GuiStyle() {}

	public static Component text(String value, ChatFormatting color) {
		return Component.literal(value).withStyle(color);
	}

	public static Component windowTitle(String title) {
		return Component.literal(title).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD);
	}

	public static ChatFormatting difficulty(String category) {
		return switch (category) {
			case "B" -> ChatFormatting.GREEN;
			case "C" -> ChatFormatting.GOLD;
			default -> ChatFormatting.WHITE;
		};
	}

	public static Component resetInfo() {
		var next = ServerDates.nextReset();
		return text("下次重置 " + next.format(RESET_TIME) + " (" + next.getZone() + ")", MUTED);
	}

	public static GuiElementBuilder item(Item icon, String name, ChatFormatting color) {
		return new GuiElementBuilder(icon).setName(text(name, color)).hideDefaultTooltip();
	}

	public static GuiElementBuilder item(ItemStack icon, String name, ChatFormatting color) {
		return new GuiElementBuilder(icon).setName(text(name, color)).hideDefaultTooltip();
	}

	public static GuiElement button(Item icon, String name, ChatFormatting color, Runnable action) {
		return onLeftClick(item(icon, name, color), action).build();
	}

	public static GuiElementBuilder onLeftClick(GuiElementBuilder builder, Runnable action) {
		return builder.setCallback((slot, type, input, gui) -> {
			if (type == ClickType.MOUSE_LEFT) {
				action.run();
			}
		});
	}

	public static void fill(SimpleGui gui, int slots) {
		GuiElement filler = filler();
		for (int slot = 0; slot < slots; slot++) {
			gui.setSlot(slot, filler);
		}
	}

	public static GuiElement close(Runnable action) { return button(Items.BARRIER, "关闭", ERROR, action); }
	public static GuiElement back(Runnable action) { return button(Items.ARROW, "返回上一级", ACTIVE, action); }
	public static GuiElement filler() {
		return item(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY), " ", MUTED).hideTooltip().build();
	}
}
