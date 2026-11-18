package club.sjmc.smp3.sign;

import java.time.YearMonth;
import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;
import java.util.Map;

import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.time.ServerDates;

import net.minecraft.commands.Commands;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public final class SignFailureChecks {
	public static void run(GameTestHelper test, ServerPlayer player) throws Exception {
		configuration(test, player);
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
		    "", SignModule.config().lifetime().stream().filter(m -> m.days() == 5).findFirst().orElseThrow());
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

	private static void configuration(GameTestHelper test, ServerPlayer player) throws Exception {
		var file = FabricLoader.getInstance().getConfigDir().resolve("smp3/sign.json");
		String original = Files.readString(file);
		var server = player.level().getServer();
		try {
			Files.writeString(file, """
			    {"daily":{"currency":"smp3:coffee_stamp","amounts":[2,4]},"monthly":[],"lifetime":[
			      {"days":1,"grants":[{"type":"currency","id":"smp3:tongbao","count":9},
			                           {"type":"title","id":"test_sign_title"}]}]}
			    """);
			SignModule.reload(server);
			var config = SignModule.config();
			test.assertTrue(config.dailyReward(99) == 4, "Final daily amount did not repeat");
			SignData.get(server).commit(player.getUUID(), Smp3AccountStore.rewardState(player.getUUID(), "sign"),
			                            new PlayerSignData(), Map.of());
			Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.TONGBAO, 0);
			Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.COFFEE_STAMP, 0);
			SignService.sign(player, ServerDates.today());
			SignService.sign(player, ServerDates.today());
			test.assertTrue(balance(player) == 900 &&
			                    Smp3AccountStore.balance(player.getUUID(), Smp3Currency.COFFEE_STAMP) == 200 &&
			                    SignService.state(player).hasPendingRewards(),
			                "Custom sign rewards or title retry changed currency");
			Files.writeString(file, Files.readString(file).replace("\"days\":1", "\"days\":0"));
			try {
				SignModule.reload(server);
				throw new AssertionError("Invalid sign threshold accepted");
			} catch (IllegalArgumentException expected) {
				test.assertTrue(SignModule.config() == config, "Invalid reload replaced sign configuration");
			}
			Files.writeString(
			    file, "{\"daily\":{\"currency\":\"smp3:tongbao\",\"amounts\":[1]},\"monthly\":[],\"lifetime\":[]}");
			SignModule.reload(server);
			test.assertTrue(SignService.state(player).hasPendingRewards() && balance(player) == 900,
			                "Removing a threshold lost its pending reward or repeated currency");
		} finally {
			Files.writeString(file, original);
			SignModule.reload(server);
		}
	}

	private static long balance(ServerPlayer player) {
		return Smp3AccountStore.balance(player.getUUID(), Smp3Currency.TONGBAO);
	}
}
