package club.sjmc.smp3.sign;

import java.time.YearMonth;
import java.util.Map;

import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.time.ServerDates;

import net.minecraft.commands.Commands;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public final class SignFailureChecks {
	public static void run(GameTestHelper test, ServerPlayer player) {
		var today = ServerDates.today();
		var data = SignData.get(player.level().getServer());
		var legacy = new CompoundTag();
		legacy.putString("monthKey", YearMonth.from(today).toString());
		legacy.putLong("lastSignEpochDay", today.minusDays(1).toEpochDay());
		legacy.putInt("streak", 4);
		legacy.putInt("serverTotal", 4);
		legacy.putIntArray("serverClaimed", new int[] {1, 2, 3, 4});
		PlayerSignData state = PlayerSignData.load(legacy);
		data.commit(player.getUUID(), Smp3AccountStore.rewardState(player.getUUID(), "sign"), state, Map.of());
		Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.TONGBAO, Long.MAX_VALUE);
		SignService.sign(player, today);
		test.assertTrue(data.of(player.getUUID()).serverTotal() == 4, "Failed currency commit advanced sign-in");
		Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.TONGBAO, 0);
		SignService.sign(player, today);
		SignService.sign(player, today);
		test.assertTrue(balance(player) == 15000 && !data.of(player.getUUID()).isServerClaimed(5),
		                "Missing command lost pending reward or repeated money");
		int[] calls = {0};
		boolean[] succeeds = {false};
		player.level().getServer().getCommands().getDispatcher().register(
		    Commands.literal("gacha")
		        .requires(source -> source.getEntity() == null)
		        .then(Commands.literal("give").then(
		            Commands.literal(player.getGameProfile().name()).then(Commands.literal("ten").executes(context -> {
			            calls[0]++;
			            return succeeds[0] ? 1 : 0;
		            })))));
		SignService.sign(player, today);
		test.assertTrue(calls[0] == 1 && !data.of(player.getUUID()).isServerClaimed(5),
		                "Failed command marked claimed");
		succeeds[0] = true;
		SignService.sign(player, today);
		SignService.sign(player, today);
		test.assertTrue(calls[0] == 2 && balance(player) == 15000 && data.of(player.getUUID()).isServerClaimed(5),
		                "Console command retry duplicated reward or did not complete");
		var entries = new CompoundTag();
		entries.put(player.getUUID().toString(), legacy);
		var stale = new CompoundTag();
		stale.put("players", entries);
		test.assertTrue(SignData.fromTag(stale).of(player.getUUID()).serverTotal() == 5,
		                "Stale world snapshot overrode ledger");
		state = SignData.copy(data.of(player.getUUID()));
		var claim = new PlayerSignData.Claim(
		    "", SignRewards.SERVER.stream().filter(m -> m.days() == 5).findFirst().orElseThrow());
		for (var delivery : claim.deliveries) {
			delivery.status = delivery.reward.kind() == SignReward.Kind.CURRENCY ? PlayerSignData.Status.DONE
			                                                                     : PlayerSignData.Status.IN_FLIGHT;
		}
		state.pending.add(claim);
		data.commit(player.getUUID(), Smp3AccountStore.rewardState(player.getUUID(), "sign"), state, Map.of());
		SignService.sign(player, today);
		test.assertTrue(calls[0] == 2 && data.of(player.getUUID()).hasPendingRewards(),
		                "Interrupted delivery automatically repeated");
	}

	private static long balance(ServerPlayer player) {
		return Smp3AccountStore.balance(player.getUUID(), Smp3Currency.TONGBAO);
	}
}
