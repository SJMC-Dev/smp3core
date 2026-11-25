package club.sjmc.smp3.ui.hud;

import java.util.List;
import java.util.Optional;

import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.ui.state.CurrencyState;
import club.sjmc.smp3.ui.themes.PaperStyle;
import club.sjmc.smp3.ui.widget.PanelContext;
import club.sjmc.smp3.ui.widget.ItemIcon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 货币余额的 HUD/面板渲染，读取 {@link CurrencyState}。
 */
public final class CurrencyHud {
	private final CurrencyState state;

	public CurrencyHud(CurrencyState state) { this.state = state; }

	public int width() { return counterWidth(state.coins) + 12 + counterWidth(state.stamps); }

	private static int counterWidth(CurrencyState.Counter counter) {
		return 22 + Minecraft.getInstance().font.width(counter.text);
	}

	public int height(int availableWidth) { return width() > availableWidth ? 52 : 20; }

	public void render(GuiGraphicsExtractor graphics, int right, int top, int availableWidth, float seconds, int mouseX,
	                   int mouseY, PanelContext context) {
		boolean stacked = width() > availableWidth;
		renderCounter(graphics, state.coins, ItemIcon.currency(Smp3Currency.TONGBAO), "铁镐通宝",
		              stacked ? right : right - counterWidth(state.stamps) - 12, top, availableWidth, seconds, mouseX,
		              mouseY, context);
		renderCounter(graphics, state.stamps, ItemIcon.currency(Smp3Currency.COFFEE_STAMP), "咖啡印章", right,
		              top + (stacked ? 32 : 0), availableWidth, seconds, mouseX, mouseY, context);
	}

	private static void renderCounter(GuiGraphicsExtractor graphics, CurrencyState.Counter counter, ItemStack icon,
	                                  String label, int right, int top, int availableWidth, float seconds, int mouseX,
	                                  int mouseY, PanelContext context) {
		var font = Minecraft.getInstance().font;
		String text = PaperStyle.ellipsize(font, counter.text, availableWidth - 22);
		int left = right - 22 - font.width(text);
		graphics.item(icon, left, top + 2);
		graphics.text(font, text, left + 22, top + 6, PaperStyle.INK, false);
		if (mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + 20)
			graphics.setTooltipForNextFrame(font, List.of(Component.literal(label + "：" + counter.text)),
			                                Optional.empty(), context.toScreenX(mouseX), context.toScreenY(mouseY));
		counter.age = Math.min(CurrencyState.FLOAT_SECONDS, counter.age + seconds);
		if (counter.change == 0 || counter.age >= CurrencyState.FLOAT_SECONDS)
			return;
		int alpha = Math.round(255 * Math.min(1, (CurrencyState.FLOAT_SECONDS - counter.age) / .6F));
		if (alpha < 4)
			return;
		int color = (alpha << 24) | (counter.change > 0 ? 0x387341 : 0xAD4434);
		String delta = PaperStyle.ellipsize(font, (counter.change > 0 ? "+" : "") + counter.change, right - left);
		graphics.text(font, delta, right - font.width(delta),
		              top - 8 - Math.round(6 * counter.age / CurrencyState.FLOAT_SECONDS), color, false);
	}
}
