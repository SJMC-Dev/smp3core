package club.sjmc.smp3.ui.state;

import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import club.sjmc.smp3.economy.BalanceSync;

/**
 * 货币余额与变化动画的状态，不包含任何渲染逻辑。
 */
public final class CurrencyState {
	public static final float FLOAT_SECONDS = 1.4F;
	public final Counter coins = new Counter(), stamps = new Counter();
	private final java.util.List<Listener> listeners = new CopyOnWriteArrayList<>();

	public interface Listener {
		void currencyChanged();
	}

	public void addListener(Listener listener) {
		listeners.remove(listener);
		listeners.add(listener);
	}

	public void removeListener(Listener listener) { listeners.remove(listener); }

	public void clear() {
		coins.clear();
		stamps.clear();
	}

	public void accept(BalanceSync.Payload balance) {
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
		listeners.forEach(Listener::currencyChanged);
	}

	public static final class Counter {
		public long value, change;
		public boolean received;
		public float age = FLOAT_SECONDS;
		public String text = "—";

		public void clear() {
			received = false;
			value = change = 0;
			age = FLOAT_SECONDS;
			text = "—";
		}

		public void set(long value) {
			if (received && value != this.value) {
				change = (age < FLOAT_SECONDS ? change : 0) + value - this.value;
				age = 0;
			}
			this.value = value;
			text = String.format(Locale.ROOT, "%,d", value);
			received = true;
		}
	}
}
