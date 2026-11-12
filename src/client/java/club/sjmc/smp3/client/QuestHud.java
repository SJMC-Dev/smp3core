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
		if (client.player == null || (!visible && !state.hiding()) || client.gui.hud.isHidden())
			return;
		if (client.gui.screen() != null && !(client.gui.screen() instanceof ChatScreen))
			return;
		state.advance(Math.min(delta.getRealtimeDeltaTicks() / 20, .05F));
		if (state.rows.isEmpty())
			return;
		int width = 48 + state.rows.stream().mapToInt(row -> client.font.width(ratio(row))).max().orElse(0);
		graphics.pose().pushMatrix();
		graphics.pose().translate(graphics.guiWidth() - width * .8F - 8, 8);
		graphics.pose().scale(.8F, .8F);
		int y = 0;
		graphics.enableScissor(0, 0, width + 10, state.rows.size() * 22);
		for (var row : state.rows) {
			float slide = Math.max(0, state.slideTime(row)) / QuestHudState.DURATION;
			int left = Math.round((width + 10) * slide * slide * (3 - 2 * slide));
			int color = row.entry.complete() ? 0xFF55FF55 : 0xFFFFFF55;
			graphics.fill(left, y, left + width, y + 20, 0x88000000);
			graphics.outline(left + 4, y + 4, 12, 12, 0xFFAAAAAA);
			if (row.entry.complete()) {
				int pixels = Math.round(8 * row.checkTime / QuestHudState.DURATION);
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
