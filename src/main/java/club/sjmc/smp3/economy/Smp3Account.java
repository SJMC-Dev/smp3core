package club.sjmc.smp3.economy;

import java.math.BigInteger;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import eu.pb4.common.economy.api.EconomyAccount;
import eu.pb4.common.economy.api.EconomyCurrency;
import eu.pb4.common.economy.api.EconomyProvider;
import eu.pb4.common.economy.api.EconomyTransaction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * 本模组的货币账户：按玩家 UUID + 货币记账，读写 {@link Smp3AccountStore}。
 *
 * <p>余额与交易金额都是「原始单位」，展示与解析交给 {@link Smp3Currency}。</p>
 */
public final class Smp3Account implements EconomyAccount {

	private static final BigInteger MAX_RAW = BigInteger.valueOf(Long.MAX_VALUE);

	private final GameProfile profile;
	private final Smp3Currency currency;

	private Smp3Account(GameProfile profile, Smp3Currency currency) {
		this.profile = profile;
		this.currency = currency;
	}

	public static Smp3Account of(GameProfile profile, Smp3Currency currency) {
		return new Smp3Account(profile, currency);
	}

	@Override
	public Component name() {
		String playerName = profile.name();
		return Component.literal(playerName != null ? playerName : profile.id().toString());
	}

	@Override
	public UUID owner() {
		return profile.id();
	}

	@Override
	public Identifier id() {
		return currency.id();
	}

	@Override
	public BigInteger balance() {
		return BigInteger.valueOf(Smp3AccountStore.balance(owner(), currency));
	}

	@Override
	public void setBalance(BigInteger value) {
		Smp3AccountStore.setBalance(owner(), currency, value.longValueExact());
	}

	@Override
	public EconomyTransaction canIncreaseBalance(BigInteger amount) {
		BigInteger previous = balance();
		BigInteger next = previous.add(amount);
		if (next.compareTo(MAX_RAW) > 0) {
			return transaction(false, previous, previous, amount, "余额超出上限");
		}
		return transaction(true, previous, next, amount, "操作成功");
	}

	@Override
	public EconomyTransaction canDecreaseBalance(BigInteger amount) {
		BigInteger previous = balance();
		BigInteger next = previous.subtract(amount);
		if (next.signum() < 0) {
			return transaction(false, previous, previous, amount, "余额不足");
		}
		return transaction(true, previous, next, amount, "操作成功");
	}

	@Override
	public EconomyProvider provider() {
		return Smp3EconomyProvider.INSTANCE;
	}

	@Override
	public EconomyCurrency currency() {
		return currency;
	}

	private EconomyTransaction transaction(boolean success, BigInteger previous, BigInteger next, BigInteger amount,
			String message) {
		return new EconomyTransaction.Simple(success, Component.literal(message), next, previous, amount, this);
	}
}
