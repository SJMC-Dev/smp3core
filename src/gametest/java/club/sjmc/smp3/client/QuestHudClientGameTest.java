package club.sjmc.smp3.client;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import club.sjmc.smp3.daily.DailyQuestModule;
import club.sjmc.smp3.daily.QuestDefinition;
import club.sjmc.smp3.daily.ui.DailyQuestGui;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;

public final class QuestHudClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (var world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.waitFor(client -> !Smp3Client.HUD.state.rows.isEmpty());
			world.getServer().runOnServer(server -> {
				var player = world.getConnection().getServerPlayer();
				server.getPlayerList().op(new NameAndId(player.getGameProfile()),
				                          Optional.of(LevelBasedPermissionSet.GAMEMASTER), Optional.empty());
			});
			context.waitFor(
			    client
			    -> client.getConnection().getCommands().getRoot().getChild("dailyquest").getChild("player") != null);
			context.runOnClient(client -> {
				var connection = client.getConnection();
				var commands = connection.getCommands();
				for (String root : List.of("dailyquest", "dailyquests")) {
					var suggestions =
					    commands
					        .getCompletionSuggestions(commands.parse(root + " ", connection.getSuggestionsProvider()))
					        .join();
					if (suggestions.getList().stream().noneMatch(
					        s -> s.getText().equals(client.player.getName().getString())))
						throw new AssertionError("Client OP player completion missing: " + root);
				}
			});
			context.runOnClient(client -> client.options.guiScale().set(2));
			context.takeScreenshot("quest-hud-active");
			String token = context.computeOnClient(client -> Smp3Client.HUD.state.rows.getFirst().entry.token());

			world.getServer().runOnServer(server -> DailyQuestGui.show(world.getConnection().getServerPlayer()));
			context.waitForScreen(AbstractContainerScreen.class);
			hoverSlot(context, 10);
			context.takeScreenshot("quest-menu-description");
			hoverSlot(context, 19);
			context.takeScreenshot("quest-menu-progress-rewards");

			world.getServer().runOnServer(server -> {
				var player = world.getConnection().getServerPlayer();
				var service = DailyQuestModule.current();
				var task = service.state(player).assignments().getFirst();
				var facts = new HashMap<String, Set<String>>();
				task.definition.objective().conditions().entrySet().forEach(
				    c -> facts.put(c.getKey(), Set.of(QuestDefinition.values(c.getValue()).getFirst())));
				for (int i = 0; i < task.target; i++) {
					service.record(player, new QuestDefinition.Event(task.definition.objective().event(), facts));
				}
			});
			context.waitFor(client
			                -> Smp3Client.HUD.state.rows.stream().anyMatch(
			                    row -> row.entry.token().equals(token) && row.entry.claimed()));
			context.waitTicks(2);
			context.takeScreenshot("quest-menu-paid");
			world.getServer().runOnServer(server -> world.getConnection().getServerPlayer().closeContainer());
			context.waitFor(client -> client.gui.screen() == null);
			context.takeScreenshot("quest-hud-completed");
			context.waitFor(
			    client -> Smp3Client.HUD.state.rows.stream().noneMatch(row -> row.entry.token().equals(token)));
			context.takeScreenshot("quest-hud-claimed");
		}
		context.runOnClient(client -> {
			if (!Smp3Client.HUD.state.rows.isEmpty())
				throw new AssertionError("断开后残留任务");
		});
	}

	private static void hoverSlot(ClientGameTestContext context, int slot) {
		var position = context.computeOnClient(client -> {
			var window = client.getWindow();
			double x = (window.getGuiScaledWidth() - 176) / 2 + 8 + slot % 9 * 18 + 8;
			double y = (window.getGuiScaledHeight() - 186) / 2 + 18 + slot / 9 * 18 + 8;
			return new double[] {x * window.getScreenWidth() / window.getGuiScaledWidth(),
			                     y * window.getScreenHeight() / window.getGuiScaledHeight()};
		});
		context.getInput().setCursorPos(position[0], position[1]);
		context.waitTicks(2);
	}
}
