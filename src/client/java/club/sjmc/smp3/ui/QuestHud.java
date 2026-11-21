package club.sjmc.smp3.ui;

import java.util.List;

import club.sjmc.smp3.daily.QuestDefinition;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.item.ItemStack;

final class QuestHud {
	final QuestHudState state = new QuestHudState();
	boolean visible = true;

	void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui.hud.isHidden())
			return;
		if (client.gui.screen() != null && !(client.gui.screen() instanceof ChatScreen))
			return;
		draw(graphics, Math.min(delta.getRealtimeDeltaTicks() / 20, .05F));
	}

	private int questWidth() {
		return 48 + state.rows.stream().mapToInt(row -> Minecraft.getInstance().font.width(ratio(row))).max().orElse(0);
	}

	private int questHeight(boolean editing) {
		return editing ? Math.max(1, state.rows.size()) * 22 : visible || state.hiding() ? state.rows.size() * 22 : 0;
	}

	private int noticeWidth() { return Smp3Client.BOOK.unreadCount() > 0 ? Smp3Client.BOOK.noticeWidth() : 100; }

	HudLayout.Box box(int width, int height, boolean editing) {
		boolean attached = Smp3Client.layout.followQuests && (editing || Smp3Client.BOOK.noticeVisible() ||
		                                                      state.hiding() && Smp3Client.BOOK.unreadCount() > 0);
		return Smp3Client.layout.quests.box(
		    width, height, Math.max(questWidth(), attached ? noticeWidth() : 0),
		    Math.max(20, questHeight(editing) + (attached ? ServerBookState.NOTICE_HEIGHT + 4 : 0)));
	}

	HudLayout.Box noticeBox(int width, int height, boolean editing) {
		if (!Smp3Client.layout.followQuests)
			return Smp3Client.layout.notices.box(width, height, noticeWidth(), ServerBookState.NOTICE_HEIGHT);
		var group = box(width, height, editing);
		int w = Math.round(noticeWidth() * group.scale());
		return new HudLayout.Box(group.x() + group.width() - w,
		                         group.y() + Math.round((questHeight(editing) + 4) * group.scale()), w,
		                         Math.round(ServerBookState.NOTICE_HEIGHT * group.scale()), group.scale());
	}

	void draw(GuiGraphicsExtractor graphics, float seconds) {
		Minecraft client = Minecraft.getInstance();
		if (visible || state.hiding())
			state.advance(seconds);
		if (Smp3Client.BOOK.noticeVisible()) {
			var notice = noticeBox(graphics.guiWidth(), graphics.guiHeight(), false);
			graphics.pose().pushMatrix();
			graphics.pose().translate(notice.x(), notice.y());
			graphics.pose().scale(notice.scale(), notice.scale());
			Smp3Client.BOOK.renderNotice(graphics, noticeWidth(), 0);
			graphics.pose().popMatrix();
		}
		int height = questHeight(false), width = questWidth();
		var box = box(graphics.guiWidth(), graphics.guiHeight(), false);
		int contentWidth = Math.round(box.width() / box.scale());
		graphics.pose().pushMatrix();
		graphics.pose().translate(box.x(), box.y());
		graphics.pose().scale(box.scale(), box.scale());
		graphics.pose().translate(contentWidth - width, 0);

		int y = 0;
		graphics.enableScissor(0, 0, width + 10, height);
		for (var row : state.rows) {
			float slide = Math.max(0, state.slideTime(row)) / QuestHudState.DURATION;
			int left = Math.round((width + 10) * slide * slide * (3 - 2 * slide));
			int color = row.entry.complete() ? 0xFF55FF55 : 0xFFFFFF55;
			graphics.fill(left, y, left + width, y + 20, HudLayout.PANEL);
			graphics.outline(left + 4, y + 4, 12, 12, HudLayout.MUTED);
			if (row.entry.complete()) {
				int pixels = Math.round(8 * Math.min(1, row.checkTime / QuestHudState.DURATION));
				for (int i = 0; i < pixels; i++) {
					int py = y + 8 + (i < 3 ? i : 4 - i);
					graphics.fill(left + 6 + i, py, left + 8 + i, py + 2, color);
				}
			}
			var icons = row.entry.icons();
			if (!icons.isEmpty())
				graphics.item(icon(icons), left + 22, y + 2);
			String ratio = ratio(row);
			graphics.text(client.font, ratio, left + width - 4 - client.font.width(ratio), y + 6, color);
			y += 22;
		}
		graphics.disableScissor();
		graphics.pose().popMatrix();
	}

	private static ItemStack icon(List<ItemStack> icons) {
		var level = Minecraft.getInstance().level;
		int frame = level == null ? 0 : (int)(level.getGameTime() / QuestDefinition.ICON_CYCLE_TICKS);
		return icons.get(Math.floorMod(frame, icons.size()));
	}

	private static String ratio(QuestHudState.Row row) { return row.entry.progress() + "/" + row.entry.target(); }
}
