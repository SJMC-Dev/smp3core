package club.sjmc.smp3.economy;

import java.nio.file.Files;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;

public final class EconomyChecks {
	public static void run() throws Exception {
		var directory = Files.createTempDirectory("smp3-economy-check-");
		try {
			var file = directory.resolve("economy.json");
			UUID player = UUID.randomUUID();
			Files.writeString(file, "{\"balances\":{\"" + player + "\":3000}}");
			var ledger = new Smp3AccountStore.Ledger(file);
			assert ledger.balance(player, "tongbao") == 3000;
			ledger.grant(player, 100, 0, 80, 2);
			assert ledger.balance(player, "tongbao") == 11000 && ledger.balance(player, "coffee_stamp") == 200;
			ledger = new Smp3AccountStore.Ledger(file);
			ledger.grant(player, 100, 0, 80, 2);
			assert ledger.balance(player, "tongbao") == 11000 && ledger.claimed(player, 100, 0);

			Files.createDirectory(directory.resolve("economy.json.tmp"));
			try {
				ledger.grant(player, 100, 1, 50, 1);
				throw new AssertionError("写入故障未返回失败");
			} catch (IllegalStateException expected) {
			}
			assert ledger.balance(player, "tongbao") == 11000 && ledger.balance(player, "coffee_stamp") == 200;
			assert !ledger.claimed(player, 100, 1);
			Files.delete(directory.resolve("economy.json.tmp"));
			ledger.grant(player, 100, 1, 50, 1);
			assert ledger.balance(player, "tongbao") == 16000 && ledger.balance(player, "coffee_stamp") == 300;
			ledger.set(player, "coffee_stamp", Long.MAX_VALUE);
			try {
				ledger.grant(player, 100, 2, 20, 1);
				throw new AssertionError("溢出未被拒绝");
			} catch (ArithmeticException expected) {
			}
			assert ledger.balance(player, "tongbao") == 16000 && !ledger.claimed(player, 100, 2);
			ledger.set(player, "coffee_stamp", 300);
			ledger.grant(player, 101, 0, 20, 1);
			ledger.grant(player, 100, 3, 20, 1);
			assert ledger.balance(player, "tongbao") == 18000;
			var reloaded = new Smp3AccountStore.Ledger(file);
			assert reloaded.claimed(player, 101, 0) && !reloaded.claimed(player, 101, 1);
			assert !reloaded.claimed(UUID.randomUUID(), 101, 0);
			var empty = new JsonObject();
			empty.addProperty("signed", false);
			ledger.updateReward(player, "sign", null, empty, Map.of());
			var signed = empty.deepCopy();
			signed.addProperty("signed", true);
			Files.createDirectory(directory.resolve("economy.json.tmp"));
			try {
				ledger.updateReward(player, "sign", empty, signed, Map.of("tongbao", 3000L));
				throw new AssertionError("Reward write failure accepted");
			} catch (IllegalStateException expected) {
			}
			assert ledger.balance(player, "tongbao") == 18000 && ledger.rewardState(player, "sign").equals(empty);
			Files.delete(directory.resolve("economy.json.tmp"));
			ledger.updateReward(player, "sign", empty, signed, Map.of("tongbao", 3000L));
			try {
				ledger.updateReward(player, "sign", empty, signed, Map.of("tongbao", 3000L));
				throw new AssertionError("Stale reward committed twice");
			} catch (IllegalStateException expected) {
			}
			var recovered = new Smp3AccountStore.Ledger(file);
			assert recovered.balance(player, "tongbao") == 21000 &&
			    recovered.rewardState(player, "sign").equals(signed);
			signed.addProperty("signed", false);
			assert recovered.rewardState(player, "sign").get("signed").getAsBoolean();

			Files.writeString(file, "broken");
			try {
				new Smp3AccountStore.Ledger(file).set(player, "tongbao", 0);
				throw new AssertionError("损坏账本被覆盖");
			} catch (IllegalStateException expected) {
			}
			assert Files.readString(file).equals("broken");
		} finally {
			try (var paths = Files.walk(directory)) {
				for (var path : paths.sorted(Comparator.reverseOrder()).toList())
					Files.delete(path);
			}
		}
	}
}
