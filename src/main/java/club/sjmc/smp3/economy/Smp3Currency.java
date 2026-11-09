package club.sjmc.smp3.economy;

import java.math.BigInteger;

import eu.pb4.common.economy.api.EconomyCurrency;
import eu.pb4.common.economy.api.EconomyProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 本模组自有的通宝货币。
 *
 * <p>账本按「原始单位」存（1 通宝 = {@value #RAW_PER_UNIT} 原始单位），
 * 与 {@link #formatValue(BigInteger, boolean)} / {@link #parseValue(String)} 的换算保持一致，
 * 这样 Fuji 的 {@code /economy give|take|set} 传入的面值语义与显示不会错位。</p>
 */
public final class Smp3Currency implements EconomyCurrency {

	public static final Identifier ID = Identifier.fromNamespaceAndPath("smp3", "tongbao");
	/** 1 通宝对应的原始单位数。 */
	public static final int RAW_PER_UNIT = 100;
	public static final Smp3Currency INSTANCE = new Smp3Currency();

	private Smp3Currency() {
	}

	@Override
	public Component name() {
		return Component.literal("通宝");
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.IRON_PICKAXE);
	}

	/** 原始单位 → 面值文本。 */
	@Override
	public String formatValue(BigInteger raw, boolean withSymbol) {
		String units = raw.divide(BigInteger.valueOf(RAW_PER_UNIT)).toString();
		return withSymbol ? units + " 通宝" : units;
	}

	/** 面值文本 → 原始单位。 */
	@Override
	public BigInteger parseValue(String value) {
		return new BigInteger(value.trim()).multiply(BigInteger.valueOf(RAW_PER_UNIT));
	}

	@Override
	public EconomyProvider provider() {
		return Smp3EconomyProvider.INSTANCE;
	}
}
