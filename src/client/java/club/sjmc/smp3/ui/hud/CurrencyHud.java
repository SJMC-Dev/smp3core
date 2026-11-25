package club.sjmc.smp3.ui.hud;

import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.ui.state.CurrencyState;
import club.sjmc.smp3.ui.widget.ItemIcon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

	public void render(GuiGraphicsExtractor graphics, int right, int top, int availableWidth, float seconds) {
		float scale = Math.min(1, (float)availableWidth / width());
		graphics.pose().pushMatrix();
		graphics.pose().translate(right - width() * scale, top);
		graphics.pose().scale(scale, scale);
		renderCounter(graphics, state.coins, ItemIcon.currency(Smp3Currency.TONGBAO), 0, seconds);
		renderCounter(graphics, state.stamps, ItemIcon.currency(Smp3Currency.COFFEE_STAMP),
		              counterWidth(state.coins) + 12, seconds);
		graphics.pose().popMatrix();
	}

	private static void renderCounter(GuiGraphicsExtractor graphics, CurrencyState.Counter counter, ItemStack icon,
	                                  int x, float seconds) {
		var font = Minecraft.getInstance().font;
		graphics.item(icon, x, 2);
		graphics.text(font, counter.text, x + 22, 6, 0xFF443323, false);
		counter.age = Math.min(CurrencyState.FLOAT_SECONDS, counter.age + seconds);
		if (counter.change == 0 || counter.age >= CurrencyState.FLOAT_SECONDS)
			return;
		int alpha = Math.round(255 * Math.min(1, (CurrencyState.FLOAT_SECONDS - counter.age) / .6F));
		if (alpha < 4)
			return;
		int color = (alpha << 24) | (counter.change > 0 ? 0x387341 : 0xAD4434);
		String delta = (counter.change > 0 ? "+" : "") + counter.change;
		graphics.text(font, delta, x + counterWidth(counter) - font.width(delta),
		              -8 - Math.round(6 * counter.age / CurrencyState.FLOAT_SECONDS), color, false);
	}
}
