package club.sjmc.smp3.milestone;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.economy.Smp3Currency;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class MilestoneChecks {
	public static void run(GameTestHelper test, ServerPlayer player) throws Exception {
		var server = player.level().getServer();
		var file = FabricLoader.getInstance().getConfigDir().resolve("smp3/milestone-rewards.json");
		String original = Files.readString(file);
		try (var input = MilestoneChecks.class.getResourceAsStream("/milestone-rewards.json")) {
			var defaults = MilestoneConfig.parse(
			    JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject(), server);
			test.assertTrue(defaults.rules().size() == 129 &&
			                    defaults.rules().get("tree.all").requirements().size() == 126,
			                "Production milestones omit vanilla advancements or include extra trees");
		}
		var root = JsonParser.parseString("""
		    {"enabled":true,"rules":[
		      {"id":"individual","trigger":{"type":"advancement","id":"minecraft:story/iron_tools"},
		       "grants":[{"type":"currency","id":"smp3:tongbao","count":3},{"type":"title","id":"test_title"}]},
		      {"id":"tree","trigger":{"type":"advancement_tree","root":["minecraft:story/smelt_iron","minecraft:end/root"],"namespace":"minecraft"},
		       "grants":[{"type":"currency","id":"smp3:tongbao","count":7}]},
		      {"id":"balance","trigger":{"type":"balance","currency":"smp3:coffee_stamp","minimum":2},
		       "grants":[{"type":"currency","id":"smp3:tongbao","count":11}]}
		    ]}
		    """).getAsJsonObject();
		var required = Set.of(Identifier.parse("minecraft:story/smelt_iron"),
		                      Identifier.parse("minecraft:story/iron_tools"), Identifier.parse("minecraft:end/root"));
		var tree = root.getAsJsonArray("rules").get(1).getAsJsonObject().getAsJsonObject("trigger");
		var excluded = new JsonArray();
		MilestoneConfig.parse(root, server)
		    .rules()
		    .get("tree")
		    .requirements()
		    .stream()
		    .filter(id -> !required.contains(id))
		    .forEach(id -> excluded.add(id.toString()));
		tree.add("exclude", excluded);
		test.assertTrue(MilestoneConfig.parse(root, server).rules().get("tree").requirements().equals(required),
		                "Tree exclusions changed the required nodes");
		try {
			for (var id : required) {
				var advancement = server.getAdvancements().get(id);
				for (String criterion : advancement.value().criteria().keySet())
					player.getAdvancements().revoke(advancement, criterion);
			}
			Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.TONGBAO, 0);
			Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.COFFEE_STAMP, 0);
			Files.writeString(file, root.toString());
			MilestoneModule.reload(server);
			MilestoneModule.tick(server);
			test.assertTrue(balance(player) == 0, "Incomplete advancements received rewards");
			complete(player, "minecraft:story/iron_tools");
			MilestoneModule.tick(server);
			test.assertTrue(balance(player) == 300, "Advancement hook missed its reward or paid the unfinished tree");
			Files.writeString(file, root.toString().replace("minecraft:story/smelt_iron", "smp3:missing"));
			try {
				MilestoneModule.reload(server);
				throw new AssertionError("Invalid advancement accepted");
			} catch (IllegalArgumentException expected) {
				test.assertTrue(expected.getMessage().contains("rules[1]"), "Reload error omitted rule location");
			}
			complete(player, "minecraft:story/smelt_iron");
			MilestoneModule.tick(server);
			test.assertTrue(balance(player) == 300, "Joint tree awarded before every root was complete");
			complete(player, "minecraft:end/root");
			MilestoneModule.tick(server);
			test.assertTrue(balance(player) == 1000, "Failed reload replaced the live rules");
			Smp3AccountStore.commitReward(player.getUUID(), "test-balance", null, new JsonObject(),
			                              Map.of(Smp3Currency.COFFEE_STAMP, 2));
			Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.COFFEE_STAMP, 0);
			Files.writeString(file, root.toString());
			MilestoneModule.reload(server);
			MilestoneModule.tick(server);
			test.assertTrue(balance(player) == 2100, "Spending before the next tick lost a reached balance milestone");
			Files.writeString(file, root.toString());
			MilestoneModule.reload(server);
			MilestoneModule.tick(server);
			Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.COFFEE_STAMP, 200);
			MilestoneModule.tick(server);
			test.assertTrue(balance(player) == 2100, "Reload or repeated balance changes duplicated currency");
			test.assertTrue(Smp3AccountStore.rewardState(player.getUUID(), "milestones")
			                    .getAsJsonArray("individual")
			                    .get(0)
			                    .getAsString()
			                    .equals("test_title"),
			                "Unavailable permissions lost the pending title");
		} finally {
			Files.writeString(file, original);
			MilestoneModule.reload(server);
		}
	}

	private static void complete(ServerPlayer player, String id) {
		var advancement = player.level().getServer().getAdvancements().get(Identifier.parse(id));
		for (String criterion : advancement.value().criteria().keySet())
			player.getAdvancements().award(advancement, criterion);
	}

	private static long balance(ServerPlayer player) {
		return Smp3AccountStore.balance(player.getUUID(), Smp3Currency.TONGBAO);
	}
}
