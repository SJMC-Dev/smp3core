package club.sjmc.smp3.ui.hud;

import club.sjmc.smp3.ui.Smp3Client;
import club.sjmc.smp3.ui.state.ServerBookState;
import club.sjmc.smp3.ui.widget.ItemIcon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Items;

/**
 * HUD 右上角的未读公告条，只读取 {@link ServerBookState} 的数据。
 */
public final class NoticeHud {
	private final ServerBookState state;

	public NoticeHud(ServerBookState state) { this.state = state; }

	public boolean visible() { return Smp3Client.hud().visible && state.unreadCount() > 0; }

	private String title() {
		int first = 0;
		while (!state.unread(first) || !state.messages.get(first).notifyUnread())
			first++;
		var font = Minecraft.getInstance().font;
		String title = state.messages.get(first).title();
		return font.width(title) <= 80 ? title : font.plainSubstrByWidth(title, 80 - font.width("…")) + "…";
	}

	public int width() {
		var font = Minecraft.getInstance().font;
		return 40 + font.width(title()) + font.width(Integer.toString(state.unreadCount()));
	}

	public void render(GuiGraphicsExtractor graphics, int right, int y) {
		if (!visible())
			return;
		var font = Minecraft.getInstance().font;
		int x = right - width();
		String count = Integer.toString(state.unreadCount());
		graphics.fill(x, y, right, y + ServerBookState.NOTICE_HEIGHT, HudLayout.PANEL);
		graphics.item(ItemIcon.item(Items.BOOK), x + 4, y + 4);
		graphics.fill(x + 17, y + 3, x + 21, y + 7, HudLayout.RED);
		graphics.text(font, title(), x + 26, y + 8, HudLayout.TEXT);
		graphics.text(font, count, right - 6 - font.width(count), y + 8, HudLayout.RED);
	}
}
