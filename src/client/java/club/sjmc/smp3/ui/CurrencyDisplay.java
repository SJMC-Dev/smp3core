package club.sjmc.smp3.ui;

import java.util.Locale;

import club.sjmc.smp3.economy.BalanceSync;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class CurrencyDisplay {
	private static final float FLOAT_SECONDS = 1.4F;
	private ItemStack coin, stamp;
	final Counter coins = new Counter(), stamps = new Counter();
	void clear() {
		coins.clear();
		stamps.clear();
	}
	void accept(BalanceSync.Payload balance) {
		if (balance.initial())
			clear();
		if (balance.coins() < 0)
			coins.clear();
		else
			coins.set(balance.coins());
		if (balance.stamps() < 0)
			stamps.clear();
		else
			stamps.set(balance.stamps());
	}
	int width() { return coins.width() + 12 + stamps.width(); }

	void render(GuiGraphicsExtractor graphics, int right, int top, int availableWidth, float seconds) {
		if (coin == null) {
			coin = new ItemStack(Items.IRON_PICKAXE);
			stamp = new ItemStack(Items.PAPER);
		}
		float scale = Math.min(1, (float)availableWidth / width());
		graphics.pose().pushMatrix();
		graphics.pose().translate(right - width() * scale, top);
		graphics.pose().scale(scale, scale);
		coins.render(graphics, coin, 0, seconds);
		stamps.render(graphics, stamp, coins.width() + 12, seconds);
		graphics.pose().popMatrix();
	}

	static final class Counter {
		long value, change;
		boolean received;
		float age = FLOAT_SECONDS;
		String text = "—";

		void clear() {
			received = false;
			value = change = 0;
			age = FLOAT_SECONDS;
			text = "—";
		}

		void set(long value) {
			if (received && value != this.value) {
				change = (age < FLOAT_SECONDS ? change : 0) + value - this.value;
				age = 0;
			}
			this.value = value;
			text = String.format(Locale.ROOT, "%,d", value);
			received = true;
		}

		int width() { return 22 + Minecraft.getInstance().font.width(text); }

		void render(GuiGraphicsExtractor graphics, ItemStack icon, int x, float seconds) {
			var font = Minecraft.getInstance().font;
			graphics.item(icon, x, 2);
			graphics.text(font, text, x + 22, 6, 0xFF443323, false);
			age = Math.min(FLOAT_SECONDS, age + seconds);
			if (change == 0 || age >= FLOAT_SECONDS)
				return;
			int alpha = Math.round(255 * Math.min(1, (FLOAT_SECONDS - age) / .6F));
			if (alpha < 4)
				return;
			int color = (alpha << 24) | (change > 0 ? 0x387341 : 0xAD4434);
			String delta = (change > 0 ? "+" : "") + change;
			graphics.text(font, delta, x + width() - font.width(delta), -8 - Math.round(6 * age / FLOAT_SECONDS), color,
			              false);
		}
	}
}
