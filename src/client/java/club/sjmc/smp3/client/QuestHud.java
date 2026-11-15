package club.sjmc.smp3.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.registries.BuiltInRegistries;
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

	QuestHudState displayedState() { return Smp3Client.PREVIEW.enabled ? Smp3Client.PREVIEW.quests : state; }

	private int questWidth() {
		return 48 + displayedState()
		                .rows.stream()
		                .mapToInt(row -> Minecraft.getInstance().font.width(ratio(row)))
		                .max()
		                .orElse(0);
	}

	private int questHeight() { return visible || displayedState().hiding() ? displayedState().rows.size() * 22 : 0; }

	HudLayout.Box box(int width, int height) {
		boolean notice = Smp3Client.PREVIEW.noticeVisible();
		return Smp3Client.layout.quests.box(width, height, Math.max(questWidth(), notice ? HudPreview.NOTICE_WIDTH : 0),
		                                    Math.max(20, questHeight() + (notice ? 42 : 0)), 0);
	}

	void draw(GuiGraphicsExtractor graphics, float seconds) {
		Minecraft client = Minecraft.getInstance();
		Smp3Client.PREVIEW.renderCurrency(graphics, seconds);
		QuestHudState state = displayedState();
		if (visible || state.hiding())
			state.advance(seconds);
		int height = questHeight(), width = questWidth();
		var box = box(graphics.guiWidth(), graphics.guiHeight());
		int contentWidth = Math.round(box.width() / box.scale());
		graphics.pose().pushMatrix();
		graphics.pose().translate(box.x(), box.y());
		graphics.pose().scale(box.scale(), box.scale());
		Smp3Client.PREVIEW.renderNotice(graphics, contentWidth - HudPreview.NOTICE_WIDTH, height + 4);
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
			graphics.item(new ItemStack(BuiltInRegistries.ITEM.getValue(row.entry.icon())), left + 22, y + 2);
			String ratio = ratio(row);
			graphics.text(client.font, ratio, left + width - 4 - client.font.width(ratio), y + 6, color);
			y += 22;
		}
		graphics.disableScissor();
		graphics.pose().popMatrix();
	}

	private static String ratio(QuestHudState.Row row) { return row.entry.progress() + "/" + row.entry.target(); }
}
