package club.sjmc.smp3.home;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import club.sjmc.smp3.home.HomePayloads.Action;
import club.sjmc.smp3.home.HomePayloads.Request;
import club.sjmc.smp3.home.HomeStore.Location;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

public final class HomeChecks {
	public static void run(GameTestHelper test, ServerPlayer player) throws IOException, CommandSyntaxException {
		var directory = Files.createTempDirectory("smp3-homes-");
		var origin = player.position();
		float yaw = player.getYRot(), pitch = player.getXRot();
		try {
			var store = new HomeStore(directory);
			var service = new HomeService(store);
			var commands = new CommandDispatcher<CommandSourceStack>();
			HomeCommand.register(commands, () -> service);
			var source = player.createCommandSourceStack();
			test.assertTrue(commands.execute("home", source) == 0, "Empty home command reported success");
			var owner = player.getUUID();
			player.setYRot(35);
			player.setXRot(12);
			commands.execute("sethome \"中文 家园\"", source);
			commands.execute("home set \"第二个\"", source);
			test.assertTrue(commands.execute("sethome \"第三个\"", source) == 0,
			                "Limit failure returned command success");
			test.assertTrue(store.homes(owner).size() == 2 && !store.homes(owner).containsKey("第三个"),
			                "New home bypassed the configured limit");
			var saved = store.homes(owner).get("中文 家园");
			commands.execute("edithome \"中文 家园\" rename \"第二个\"", source);
			test.assertTrue(store.homes(owner).get("中文 家园").equals(saved), "Duplicate rename replaced a home");
			commands.execute("edithome \"中文 家园\" rename \"重命名 家园\"", source);
			player.setPos(origin.add(1.25, 0, 1.25));
			commands.execute("edithome \"重命名 家园\" relocate", source);
			test.assertTrue(store.homes(owner).get("重命名 家园").x() == player.getX(),
			                "Updating an existing home was blocked at the limit");
			player.setPos(origin);
			commands.execute("home \"重命名 家园\"", source);
			test.assertTrue(player.position().distanceToSqr(origin.add(1.25, 0, 1.25)) < 1e-6 &&
			                    player.getYRot() == 35 && player.getXRot() == 12,
			                "Home teleport lost position or rotation");
			test.assertTrue(commands.execute("home", source) == 0, "Multiple homes without a default were ambiguous");
			commands.execute("delhome \"第二个\"", source);
			player.setPos(origin);
			test.assertTrue(commands.execute("home", source) == 1 && player.getX() == origin.x + 1.25,
			                "Default command did not select the only home");
			player.setPos(origin);
			commands.execute("home set home", source);
			commands.execute("home tp \"重命名 家园\"", source);
			test.assertTrue(commands.execute("home", source) == 1 && player.getX() == origin.x,
			                "Default command did not select the home named home");
			commands.execute("home unset home", source);
			var suggestions = commands.getCompletionSuggestions(commands.parse("edithome \"重", source)).join();
			test.assertTrue(
			    suggestions.getList().stream().anyMatch(suggestion -> suggestion.getText().equals("\"重命名 家园\"")),
			    "Home suggestions did not preserve quoted Chinese names");
			var restored = new HomeStore(directory);
			test.assertTrue(restored.homes(owner).equals(store.homes(owner)) && restored.homes(owner).size() == 1 &&
			                    restored.homes(UUID.randomUUID()).isEmpty(),
			                "Home persistence or player isolation failed");
			store.save(owner, Map.of("失效维度", new Location("smp3:missing", 0, 64, 0, 0, 0)));
			test.assertTrue(
			    service.apply(player, new Request(Action.TELEPORT, "失效维度", "")).message().contains("维度不可用"),
			    "Missing dimension did not produce a useful error");
			var blocked = directory.resolve("blocked");
			Files.writeString(blocked, "not a directory");
			var failedStore = new HomeStore(blocked);
			try {
				new HomeService(failedStore).apply(player, new Request(Action.CREATE, "", "未保存"));
				throw new AssertionError("Home write unexpectedly succeeded");
			} catch (IOException expected) {
				test.assertTrue(failedStore.homes(owner).isEmpty(), "Failed write left a phantom home in memory");
			}
		} finally {
			player.setPos(origin);
			player.setYRot(yaw);
			player.setXRot(pitch);
			try (var files = Files.walk(directory)) {
				for (var path : files.sorted(Comparator.reverseOrder()).toList())
					Files.delete(path);
			}
		}
	}
}
