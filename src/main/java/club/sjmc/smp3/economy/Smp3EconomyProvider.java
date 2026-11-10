package club.sjmc.smp3.economy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.mojang.authlib.GameProfile;

import club.sjmc.smp3.Smp3Core;
import eu.pb4.common.economy.api.CommonEconomy;
import eu.pb4.common.economy.api.EconomyAccount;
import eu.pb4.common.economy.api.EconomyCurrency;
import eu.pb4.common.economy.api.EconomyProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 本模组的经济提供器：对外提供 smp3:tongbao 与 smp3:coffee_stamp。
 *
 * <p>注册后 Fuji 的 /economy 命令（account / give / take / set / pay / balanceTop）
 * 即可直接操作这些货币，因为它全部通过 common-economy-api 的接口访问。</p>
 */
public final class Smp3EconomyProvider implements EconomyProvider {

	/** 提供器 id，同时作为货币 id 的命名空间（smp3:xxx）。 */
	public static final String ID = "smp3";
	public static final Smp3EconomyProvider INSTANCE = new Smp3EconomyProvider();

	private Smp3EconomyProvider() {
	}

	/** 注册货币提供器；调用前必须确认 Fuji 已加载（common-economy-api 由它内嵌提供）。 */
	public static void register() {
		CommonEconomy.register(ID, INSTANCE);
		Smp3Core.LOGGER.info("[SMP3] 已注册货币提供器 {}：通宝 + 咖啡印章", ID);
	}

	@Override
	public Component name() {
		return Component.literal("SMP3");
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.IRON_PICKAXE);
	}

	@Override
	public EconomyAccount getAccount(MinecraftServer server, GameProfile profile, String currencyId) {
		Smp3Currency currency = Smp3Currency.byId(currencyId);
		return currency != null && profile != null ? Smp3Account.of(profile, currency) : null;
	}

	@Override
	public Collection<EconomyAccount> getAccounts(MinecraftServer server, GameProfile profile) {
		if (profile == null) {
			return List.of();
		}
		List<EconomyAccount> accounts = new ArrayList<>(Smp3Currency.ALL.size());
		for (Smp3Currency currency : Smp3Currency.ALL) {
			accounts.add(Smp3Account.of(profile, currency));
		}
		return accounts;
	}

	@Override
	public EconomyCurrency getCurrency(MinecraftServer server, String id) {
		return Smp3Currency.byId(id);
	}

	@Override
	public Collection<EconomyCurrency> getCurrencies(MinecraftServer server) {
		List<EconomyCurrency> currencies = new ArrayList<>(Smp3Currency.ALL);
		return currencies;
	}

	@Override
	public String defaultAccount(MinecraftServer server, GameProfile profile, EconomyCurrency currency) {
		return currency.id().getPath();
	}
}
