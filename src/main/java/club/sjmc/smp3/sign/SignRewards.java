package club.sjmc.smp3.sign;

import java.util.List;

import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.item.Smp3Items;

public final class SignRewards {

	private static final int[] DAILY = {30, 35, 40, 45, 50};

	public static final List<Milestone> MONTH = List.of(new Milestone(3, List.of(tongbao(50), coffeeStamp(3))),
	                                                    new Milestone(7, List.of(tongbao(100), coffeeStamp(5))),
	                                                    new Milestone(15, List.of(tongbao(150), coffeeStamp(10))),
	                                                    new Milestone(25, List.of(tongbao(200), coffeeStamp(15))));

	public static final List<Milestone> SERVER =
	    List.of(new Milestone(1, List.of(tongbao(200))), new Milestone(2, List.of(tongbao(200))),
		        new Milestone(3, List.of(tongbao(100), SignReward.item(Smp3Items.ANY_ENCHANTED_BOOK, 1))),
		        new Milestone(4, List.of(tongbao(200))),
		        new Milestone(5, List.of(tongbao(100), SignReward.command("/gacha give <player> ten"))),
		        new Milestone(6, List.of(tongbao(200))),
		        new Milestone(7, List.of(tongbao(200), SignReward.title("founding_player"))));

	public record Milestone(int days, List<SignReward> rewards) {}

	private SignRewards() {}

	public static int dailyReward(int streak) {
		int index = Math.max(0, Math.min(streak, DAILY.length) - 1);
		return DAILY[index];
	}

	public static SignReward tongbao(int amount) { return SignReward.currency(Smp3Currency.TONGBAO, amount); }

	public static SignReward coffeeStamp(int amount) { return SignReward.currency(Smp3Currency.COFFEE_STAMP, amount); }
}
