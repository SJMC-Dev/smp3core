package club.sjmc.smp3.economy;

import java.math.BigInteger;
import java.util.List;

import eu.pb4.common.economy.api.EconomyCurrency;
import eu.pb4.common.economy.api.EconomyProvider;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class Smp3Currency implements EconomyCurrency {

	public static final int RAW_PER_UNIT = 100;

	public static final Smp3Currency TONGBAO = new Smp3Currency("tongbao", "通宝", Items.IRON_PICKAXE);
	public static final Smp3Currency COFFEE_STAMP = new Smp3Currency("coffee_stamp", "咖啡印章", Items.PAPER);

	public static final List<Smp3Currency> ALL = List.of(TONGBAO, COFFEE_STAMP);

	private final Identifier id;
	private final String displayName;
	private final Item icon;

	private Smp3Currency(String path, String displayName, Item icon) {
		this.id = Identifier.fromNamespaceAndPath("smp3", path);
		this.displayName = displayName;
		this.icon = icon;
	}

	public static Smp3Currency byId(String id) {
		for (Smp3Currency currency : ALL) {
			if (currency.id.toString().equals(id) || currency.id.getPath().equals(id)) {
				return currency;
			}
		}
		return null;
	}

	public static long rawUnits(int amount) { return Math.multiplyExact((long)amount, RAW_PER_UNIT); }

	public static BigInteger toRaw(int amount) { return BigInteger.valueOf(rawUnits(amount)); }

	public static int toUnits(BigInteger raw) { return raw.divide(BigInteger.valueOf(RAW_PER_UNIT)).intValueExact(); }

	@Override
	public Component name() {
		return Component.literal(displayName);
	}

	@Override
	public Identifier id() {
		return id;
	}

	@Override
	public ItemStack icon() {
		var stack = new ItemStack(icon);
		if (this == COFFEE_STAMP)
			stack.set(DataComponents.ITEM_MODEL, id);
		return stack;
	}

	@Override
	public String formatValue(BigInteger raw, boolean withSymbol) {
		String units = raw.divide(BigInteger.valueOf(RAW_PER_UNIT)).toString();
		return withSymbol ? units + " " + displayName : units;
	}

	@Override
	public BigInteger parseValue(String value) {
		return new BigInteger(value.trim()).multiply(BigInteger.valueOf(RAW_PER_UNIT));
	}

	@Override
	public EconomyProvider provider() {
		return Smp3EconomyProvider.INSTANCE;
	}
}
