package club.sjmc.smp3.sign;

import java.time.YearMonth;
import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;
import java.util.Map;
import java.util.UUID;

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
		var file = FabricLoader.getInstance().getConfigDir().resolve("smp3/sign.json");
		String original = Files.readString(file);
		try {
			Files.writeString(file, """
			    {"daily":[{"currency":"smp3:tongbao","amounts":[50]},
			              {"currency":"smp3:coffee_stamp","amounts":[1]}],"monthly":[],"lifetime":[
			      {"days":5,"grants":[{"type":"currency","id":"smp3:tongbao","count":100},
			        {"type":"command","command":"gacha give <player> ten"}]}]}
			    """);
			SignModule.reload(player.level().getServer());
			failures(test, player);
		} finally {
			Files.writeString(file, original);
			SignModule.reload(player.level().getServer());
		}
	}

	private static void failures(GameTestHelper test, ServerPlayer player) {
		var stamps = Smp3AccountStore.balance(player.getUUID(), Smp3Currency.COFFEE_STAMP);
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
		int rank = data.nextRank(today.toEpochDay());
		Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.TONGBAO, Long.MAX_VALUE);
		SignService.sign(player, today);
		test.assertTrue(data.nextRank(today.toEpochDay()) == rank, "Failed commit consumed a check-in rank");
		test.assertTrue(data.of(player.getUUID()).serverTotal() == 4, "Failed currency commit advanced sign-in");
		test.assertTrue(Smp3AccountStore.balance(player.getUUID(), Smp3Currency.COFFEE_STAMP) == stamps,
		                "Failed sign-in credited the other currency");
		Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.TONGBAO, 0);
		SignService.sign(player, today);
		SignService.sign(player, today);
		test.assertTrue(data.of(player.getUUID()).dailyRank() == rank && data.nextRank(today.toEpochDay()) == rank + 1,
		                "Retry or duplicate check-in consumed an extra rank");
		test.assertTrue(balance(player) == 15000 && !data.of(player.getUUID()).isServerClaimed(5),
		                "Missing command lost pending reward or repeated money");
		test.assertTrue(Smp3AccountStore.balance(player.getUUID(), Smp3Currency.COFFEE_STAMP) == stamps + 100,
		                "Retry duplicated the daily stamp");
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
		var recovered = SignData.fromTag(stale);
		test.assertTrue(recovered.nextRank(today.toEpochDay()) == data.nextRank(today.toEpochDay()),
		                "Restart recovered rank from stale world data instead of accounts");
		test.assertTrue(recovered.of(player.getUUID()).dailyRank() == rank &&
		                    PlayerSignData.load(recovered.of(player.getUUID()).save()).dailyRank() == rank,
		                "Rank was not persisted");
		var other = UUID.randomUUID();
		var otherState = new PlayerSignData();
		otherState.rotateMonth(today);
		otherState.sign(today);
		otherState.dailyRank(recovered.nextRank(today.toEpochDay()));
		recovered.commit(other, null, otherState, Map.of());
		test.assertTrue(otherState.dailyRank() == rank + 1 &&
		                    SignData.fromTag(stale).nextRank(today.toEpochDay()) == rank + 2,
		                "Different players received the same rank");
		test.assertTrue(recovered.nextRank(today.plusDays(1).toEpochDay()) == 1, "New day did not reset check-in rank");
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
			    {"daily":[{"currency":"smp3:coffee_stamp","amounts":[2,4]}],"monthly":[],"lifetime":[
			      {"days":1,"grants":[{"type":"currency","id":"smp3:tongbao","count":9},
			                           {"type":"title","id":"test_sign_title"}]}]}
			    """);
			SignModule.reload(server);
			var config = SignModule.config();
			test.assertTrue(config.dailyRewards(99).get(Smp3Currency.COFFEE_STAMP) == 4,
			                "Final daily amount did not repeat");
			var legacy = new CompoundTag();
			legacy.putInt("serverTotal", 1);
			legacy.putIntArray("serverClaimed", new int[] {1});
			var previous = PlayerSignData.load(legacy);
			test.assertTrue(previous.queueMissingTitles(config, title -> false) && previous.pending.size() == 1 &&
			                    previous.pending.getFirst().deliveries.size() == 1 &&
			                    previous.pending.getFirst().deliveries.getFirst().reward.kind() ==
			                        SignReward.Kind.TITLE &&
			                    !previous.queueMissingTitles(config, title -> false),
			                "Title backfill replayed a claimed reward");
			previous.finish(previous.pending.getFirst());
			test.assertTrue(!previous.queueMissingTitles(config, title -> true), "Owned title queued again");
			legacy.putLong("lastSignEpochDay", ServerDates.today().toEpochDay());
			SignData.get(server).commit(player.getUUID(), Smp3AccountStore.rewardState(player.getUUID(), "sign"),
			                            PlayerSignData.load(legacy), Map.of());
			Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.TONGBAO, 0);
			Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.COFFEE_STAMP, 0);
			SignService.reconcile(player);
			SignService.sign(player, ServerDates.today());
			test.assertTrue(balance(player) == 0 &&
			                    Smp3AccountStore.balance(player.getUUID(), Smp3Currency.COFFEE_STAMP) == 0 &&
			                    SignService.state(player).pending.size() == 1,
			                "Legacy signed day replayed daily or milestone currency");
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
			    file, "{\"daily\":[{\"currency\":\"smp3:tongbao\",\"amounts\":[1]}],\"monthly\":[],\"lifetime\":[]}");
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
