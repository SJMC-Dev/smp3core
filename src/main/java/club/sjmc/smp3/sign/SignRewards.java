package club.sjmc.smp3.sign;

import java.util.List;

import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.item.Smp3Items;

/**
 * 签到奖励表（硬编码，不读配置文件）。
 */
public final class SignRewards {

	/** 连续签到第 1..5 天的通宝奖励，第 5 天及以后沿用最后一个。 */
	private static final int[] DAILY = { 30, 35, 40, 45, 50 };

	/** 月度累计档位：当月累计签到达到 {@code days} 天后发放。 */
	public static final List<Milestone> MONTH = List.of(
			new Milestone(3, List.of(
					tongbao(50),
					coffeeStamp(3))),
			new Milestone(7, List.of(
					tongbao(100),
					coffeeStamp(5))),
			new Milestone(15, List.of(
					tongbao(150),
					coffeeStamp(10))),
			new Milestone(25, List.of(
					tongbao(200),
					coffeeStamp(15))));

	/** 开服累计档位：签到总天数达到 {@code days} 天后发放，永久只发一次。 */
	public static final List<Milestone> SERVER = List.of(
			new Milestone(1, List.of(tongbao(200))),
			new Milestone(2, List.of(tongbao(200))),
			new Milestone(3, List.of(
					tongbao(100),
					new SignReward.Item(Smp3Items.ANY_ENCHANTED_BOOK, 1))),
			new Milestone(4, List.of(tongbao(200))),
			new Milestone(5, List.of(
					tongbao(100),
					new SignReward.Command("/gacha give <player> ten"))),
			new Milestone(6, List.of(tongbao(200))),
			new Milestone(7, List.of(
					tongbao(200),
					new SignReward.Title("founding_player"))));

	public record Milestone(int days, List<SignReward> rewards) {
	}

	private SignRewards() {
	}

	/** 连续第 {@code streak} 天的通宝奖励。 */
	public static int dailyReward(int streak) {
		int index = Math.max(0, Math.min(streak, DAILY.length) - 1);
		return DAILY[index];
	}

	public static SignReward.Currency tongbao(int amount) {
		return new SignReward.Currency(Smp3Currency.TONGBAO, amount);
	}

	public static SignReward.Currency coffeeStamp(int amount) {
		return new SignReward.Currency(Smp3Currency.COFFEE_STAMP, amount);
	}
}
