package club.sjmc.smp3.economy;

import java.math.BigInteger;

import club.sjmc.smp3.Smp3Core;
import eu.pb4.common.economy.api.CommonEconomy;
import eu.pb4.common.economy.api.EconomyAccount;
import eu.pb4.common.economy.api.EconomyTransaction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fuji 通宝的经济桥接。
 *
 * <p>只依赖 {@code eu.pb4.common.economy.api}（由 Fuji 在运行时内嵌提供），不直接依赖 Fuji 内部类，
 * 以免 Fuji 小版本更新破坏编译。余额不做缓存，每次交易实时查询。</p>
 *
 * <h2>单位</h2>
 * <p>本模组内部一律使用「通宝」（整数）：条目价格、余额、日志、玩家消息都是通宝。而
 * {@link EconomyAccount#balance()} 与扣款/入账用的是账本内原始单位
 * （1 通宝 = {@value Smp3Currency#RAW_PER_UNIT} 原始单位），换算集中在本类。</p>
 */
public final class EconomyBridge {

	/** 结算货币：本模组自有的 smp3:tongbao。 */
	private static final Identifier CURRENCY_ID = Smp3Currency.ID;

	/** 避免 Fuji 缺失时每次点击都刷一条告警。 */
	private static boolean warned;

	private EconomyBridge() {
	}

	/** 一次经济操作的结果，余额单位为通宝；查询不到时余额为 {@code null}。 */
	public record Outcome(boolean success, Integer balanceAfter, Component message) {

		static Outcome failure(Component message) {
			return new Outcome(false, null, message);
		}
	}

	private static BigInteger toRaw(int units) {
		return BigInteger.valueOf((long) units * Smp3Currency.RAW_PER_UNIT);
	}

	/** 账本原始单位 → 通宝（向下取整；超出 int 范围时抛 ArithmeticException）。 */
	private static int toUnits(BigInteger raw) {
		return raw.divide(BigInteger.valueOf(Smp3Currency.RAW_PER_UNIT)).intValueExact();
	}

	/** 取玩家在当前货币下的账户；经济 API 不可用时返回 {@code null}。 */
	private static EconomyAccount account(ServerPlayer player) {
		try {
			return CommonEconomy.getAccount(player, CURRENCY_ID);
		} catch (Throwable t) {
			if (!warned) {
				warned = true;
				Smp3Core.LOGGER.warn("[SMP3] 无法获取通宝账户（common-economy-api 不可用，需安装 Fuji）：{}", t.toString());
			}
			return null;
		}
	}

	/** 查询余额（通宝）；不可用时返回 {@code null}。 */
	public static Integer balance(ServerPlayer player) {
		EconomyAccount account = account(player);
		if (account == null) {
			return null;
		}
		try {
			return toUnits(account.balance());
		} catch (Throwable t) {
			Smp3Core.LOGGER.error("[SMP3] 读取余额异常：{}", t.toString());
			return null;
		}
	}

	/** 扣款，{@code units} 单位为通宝。 */
	public static Outcome withdraw(ServerPlayer player, int units) {
		EconomyAccount account = account(player);
		if (account == null) {
			return Outcome.failure(Component.literal("经济系统不可用"));
		}
		try {
			return toOutcome(account.decreaseBalance(toRaw(units)));
		} catch (Throwable t) {
			Smp3Core.LOGGER.error("[SMP3] 扣款异常：{}", t.toString());
			return Outcome.failure(Component.literal("扣款失败"));
		}
	}

	/** 入账，{@code units} 单位为通宝。 */
	public static Outcome deposit(ServerPlayer player, int units) {
		EconomyAccount account = account(player);
		if (account == null) {
			return Outcome.failure(Component.literal("经济系统不可用"));
		}
		try {
			return toOutcome(account.increaseBalance(toRaw(units)));
		} catch (Throwable t) {
			Smp3Core.LOGGER.error("[SMP3] 入账异常：{}", t.toString());
			return Outcome.failure(Component.literal("入账失败"));
		}
	}

	private static Outcome toOutcome(EconomyTransaction transaction) {
		Integer after = transaction.isSuccessful() ? toUnits(transaction.finalBalance()) : null;
		return new Outcome(transaction.isSuccessful(), after, transaction.message());
	}
}
