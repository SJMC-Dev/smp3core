package club.sjmc.smp3.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;

import com.google.gson.Gson;

import club.sjmc.smp3.config.ServerConfig;
import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.shop.QuotaStore;
import club.sjmc.smp3.shop.ShopEntry;
import club.sjmc.smp3.shop.ShopService;
import club.sjmc.smp3.shop.TradeDirection;
import club.sjmc.smp3.sign.PlayerSignData;
import club.sjmc.smp3.sign.SignService;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

public final class SignDateChecks {
	public static void run(GameTestHelper test, ServerPlayer player) {
		Clock previous = ServerDates.clock;
		LocalTime previousResetTime = ServerDates.resetTime;
		try {
			var january = LocalDate.of(2030, 1, 31);
			var february = january.plusDays(1);
			var legacy = new CompoundTag();
			legacy.putString("monthKey", "2030-01");
			legacy.putLong("lastSignEpochDay", january.toEpochDay());
			legacy.putInt("streak", 1);
			legacy.putInt("monthTotal", 1);
			legacy.putInt("serverTotal", 8);
			legacy.putIntArray("serverClaimed", new int[] {1, 2, 3, 4, 5, 6, 7});
			var state = PlayerSignData.load(legacy);
			Smp3AccountStore.commitReward(player.getUUID(), "sign",
			                              Smp3AccountStore.rewardState(player.getUUID(), "sign"),
			                              new Gson().toJsonTree(state).getAsJsonObject(), Map.of());
			ServerDates.configure(new ServerConfig(ZoneId.of("Asia/Shanghai"), LocalTime.of(4, 0)));
			Instant boundary = Instant.parse("2030-01-31T20:00:00Z");
			ServerDates.clock = Clock.fixed(boundary.minusSeconds(1), ZoneId.of("Asia/Shanghai"));
			SignService.sign(player, january);
			test.assertTrue(SignService.state(player).monthKey().equals("2030-01"), "Month changed before reset");
			var entry =
			    new ShopEntry("test.reset", TradeDirection.BUY, null, Items.STONE, Items.STONE, null, 1, 1, 1, 1);
			Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.TONGBAO, 100);
			test.assertTrue(ShopService.buy(player, entry).status() == ShopService.Status.BOUGHT &&
			                    QuotaStore.used(player, entry) == 1,
			                "Shop quota was not consumed before reset");
			ServerDates.clock = Clock.fixed(boundary, ZoneId.of("Asia/Shanghai"));
			test.assertTrue(QuotaStore.used(player, entry) == 0, "Shop quota did not reset with check-in");
			SignService.sign(player, january);

			test.assertTrue(SignService.state(player).monthKey().equals("2030-02") &&
			                    SignService.state(player).monthTotal() == 0 && balance(player) == 0,
			                "Stale calendar signed new month");
			SignService.sign(player, february);
			SignService.sign(player, february);
			test.assertTrue(balance(player) == 3500 && SignService.state(player).monthTotal() == 1,
			                "Month rollover duplicated sign-in");
			ServerDates.clock = Clock.fixed(boundary, ZoneId.of("UTC"));
			SignService.sign(player, january);
			test.assertTrue(balance(player) == 3500 && SignService.state(player).monthKey().equals("2030-02"),
			                "Timezone rollback reset sign-in");
			ServerDates.clock = Clock.fixed(boundary.plusSeconds(86400), ZoneId.of("UTC"));
			SignService.sign(player, february);
			test.assertTrue(balance(player) == 3500, "Timezone catch-up repeated reward");
		} finally {
			ServerDates.clock = previous;
			ServerDates.resetTime = previousResetTime;
		}
	}

	private static long balance(ServerPlayer player) {
		return Smp3AccountStore.balance(player.getUUID(), Smp3Currency.TONGBAO);
	}
}
