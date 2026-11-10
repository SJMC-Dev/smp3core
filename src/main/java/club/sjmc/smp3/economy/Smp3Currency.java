package club.sjmc.smp3.economy;

import java.math.BigInteger;
import java.util.List;

import eu.pb4.common.economy.api.EconomyCurrency;
import eu.pb4.common.economy.api.EconomyProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 本模组自有的货币（通宝 / 咖啡印章）。
 *
 * <p>账本按「原始单位」存，1 单位货币 = {@value #RAW_PER_UNIT} 个原始值；
 * {@link #formatValue} / {@link #parseValue} 负责面值与原始值之间的换算。</p>
 */
public final class Smp3Currency implements EconomyCurrency {

	/** 1 单位货币对应的原始值数量。 */
	public static final int RAW_PER_UNIT = 100;

	public static final Smp3Currency TONGBAO = new Smp3Currency("tongbao", "通宝", Items.IRON_PICKAXE);
	public static final Smp3Currency COFFEE_STAMP = new Smp3Currency("coffee_stamp", "咖啡印章", Items.PAPER);

	/** 全部货币，供 provider 使用。 */
	public static final List<Smp3Currency> ALL = List.of(TONGBAO, COFFEE_STAMP);

	private final Identifier id;
	private final String displayName;
	private final Item icon;

	private Smp3Currency(String path, String displayName, Item icon) {
		this.id = Identifier.fromNamespaceAndPath("smp3", path);
		this.displayName = displayName;
		this.icon = icon;
	}

	/** 按全名（smp3:tongbao）或 path（tongbao）查找；找不到返回 {@code null}。 */
	public static Smp3Currency byId(String id) {
		for (Smp3Currency currency : ALL) {
			if (currency.id.toString().equals(id) || currency.id.getPath().equals(id)) {
				return currency;
			}
		}
		return null;
	}

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
		return new ItemStack(icon);
	}

	/** 原始值 → 面值文本。 */
	@Override
	public String formatValue(BigInteger raw, boolean withSymbol) {
		String units = raw.divide(BigInteger.valueOf(RAW_PER_UNIT)).toString();
		return withSymbol ? units + " " + displayName : units;
	}

	/** 面值文本 → 原始值。 */
	@Override
	public BigInteger parseValue(String value) {
		return new BigInteger(value.trim()).multiply(BigInteger.valueOf(RAW_PER_UNIT));
	}

	@Override
	public EconomyProvider provider() {
		return Smp3EconomyProvider.INSTANCE;
	}
}
