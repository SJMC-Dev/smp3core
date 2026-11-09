package club.sjmc.smp3.economy;

import java.util.Collection;
import java.util.List;

import com.mojang.authlib.GameProfile;

import club.sjmc.smp3.Smp3Core;
import eu.pb4.common.economy.api.CommonEconomy;
import eu.pb4.common.economy.api.EconomyAccount;
import eu.pb4.common.economy.api.EconomyCurrency;
import eu.pb4.common.economy.api.EconomyProvider;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 本模组的经济提供器：对外提供 {@code smp3:tongbao} 货币与按 UUID 记账的账户。
 *
 * <p>注册后 Fuji 的 {@code /economy} 命令（account / give / take / set / pay / balanceTop）
 * 即可直接操作本货币，因为它全部通过 common-economy-api 的接口访问。</p>
 */
public final class Smp3EconomyProvider implements EconomyProvider {

	/** 提供器 id，同时作为货币 id 的命名空间（smp3:tongbao）。 */
	public static final String ID = "smp3";
	/** 账户名（本提供器下每个玩家只有一个账户）。 */
	public static final String DEFAULT_ACCOUNT = "tongbao";
	public static final Smp3EconomyProvider INSTANCE = new Smp3EconomyProvider();

	private Smp3EconomyProvider() {
	}

	/** 仅在 Fuji（提供 common-economy-api）存在时注册，避免 NoClassDefFoundError。 */
	public static void register() {
		if (!FabricLoader.getInstance().isModLoaded("fuji")) {
			Smp3Core.LOGGER.warn("[SMP3] 未检测到 Fuji，跳过我方货币注册");
			return;
		}
		CommonEconomy.register(ID, INSTANCE);
		Smp3Core.LOGGER.info("[SMP3] 已注册货币 {}（1 通宝 = {} 原始单位）", Smp3Currency.ID, Smp3Currency.RAW_PER_UNIT);
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
		return isTongbao(currencyId) && profile != null ? Smp3Account.of(profile) : null;
	}

	@Override
	public Collection<EconomyAccount> getAccounts(MinecraftServer server, GameProfile profile) {
		return profile == null ? List.of() : List.of(Smp3Account.of(profile));
	}

	@Override
	public EconomyCurrency getCurrency(MinecraftServer server, String id) {
		return isTongbao(id) ? Smp3Currency.INSTANCE : null;
	}

	@Override
	public Collection<EconomyCurrency> getCurrencies(MinecraftServer server) {
		return List.of(Smp3Currency.INSTANCE);
	}

	@Override
	public String defaultAccount(MinecraftServer server, GameProfile profile, EconomyCurrency currency) {
		return DEFAULT_ACCOUNT;
	}

	/** 同时接受 {@code smp3:tongbao} 与 {@code tongbao}（调用方可能传全名或只传 path）。 */
	private static boolean isTongbao(String id) {
		return Smp3Currency.ID.toString().equals(id) || Smp3Currency.ID.getPath().equals(id);
	}
}
