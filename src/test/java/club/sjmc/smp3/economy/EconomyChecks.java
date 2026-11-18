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
			UUID player = UUID.randomUUID();
			var file = directory.resolve(player + ".json");
			Files.writeString(file, "{\"balances\":{\"tongbao\":3000},\"rewardStates\":{}}");
			UUID other = UUID.randomUUID();
			Files.writeString(directory.resolve(other + ".json"), "broken unrelated account");
			var ledger = new Smp3AccountStore.Ledger(directory);
			assert ledger.balance(player, "tongbao") == 3000;
			var empty = new JsonObject();
			empty.addProperty("signed", false);
			ledger.update(player, "sign", null, empty, Map.of());
			var signed = empty.deepCopy();
			signed.addProperty("signed", true);
			Files.createDirectory(directory.resolve(player + ".json.tmp"));
			try {
				ledger.update(player, "sign", empty, signed, Map.of("tongbao", 3000L));
				throw new AssertionError("Reward write failure accepted");
			} catch (IllegalStateException expected) {
			}
			assert ledger.balance(player, "tongbao") == 3000 && ledger.rewardState(player, "sign").equals(empty);
			Files.delete(directory.resolve(player + ".json.tmp"));
			ledger.update(player, "sign", empty, signed, Map.of("tongbao", 3000L));
			try {
				ledger.update(player, "sign", empty, signed, Map.of("tongbao", 3000L));
				throw new AssertionError("Stale reward committed twice");
			} catch (IllegalStateException expected) {
			}
			var recovered = new Smp3AccountStore.Ledger(directory);
			assert recovered.balance(player, "tongbao") == 6000 && recovered.rewardState(player, "sign").equals(signed);
			signed.addProperty("signed", false);
			assert recovered.rewardState(player, "sign").get("signed").getAsBoolean();

			var paid = recovered.rewardState(player, "sign");
			recovered.update(player, "sign", paid, paid, Map.of("tongbao", -1000L));
			try {
				recovered.update(player, "sign", paid, paid, Map.of("tongbao", -5001L));
				throw new AssertionError("Overdraft accepted");
			} catch (IllegalArgumentException expected) {
			}
			assert new Smp3AccountStore.Ledger(directory).balance(player, "tongbao") == 5000;

			Files.writeString(file, "broken");
			try {
				new Smp3AccountStore.Ledger(directory).set(player, "tongbao", 0);
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
