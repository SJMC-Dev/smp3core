package club.sjmc.smp3.client;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.SguiUtils;
import eu.pb4.sgui.api.gui.SimpleGui;

import club.sjmc.smp3.daily.DailyQuestModule;
import club.sjmc.smp3.daily.QuestDefinition;
import club.sjmc.smp3.daily.ui.DailyQuestGui;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

public final class QuestHudClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (var world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.waitFor(client -> Smp3Client.HUD.state.rows.size() == 4);
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
			context.runOnClient(client -> {
				if (!Smp3Client.HUD.visible)
					throw new AssertionError("HUD 默认未显示");
				client.options.guiScale().set(2);
			});
			context.waitTicks(5);
			context.takeScreenshot("quest-hud-active");
			context.getInput().pressKey(InputConstants.KEY_H);
			context.waitFor(client -> !Smp3Client.HUD.visible);
			context.takeScreenshot("quest-hud-hiding");
			context.waitFor(client -> !Smp3Client.HUD.state.hiding());
			context.runOnClient(client -> {
				var settings = FabricLoader.getInstance().getConfigDir().resolve("smp3/client.json");
				try {
					if (!Files.readString(settings).contains("false"))
						throw new AssertionError("隐藏偏好未保存");
				} catch (java.io.IOException e) {
					throw new AssertionError(e);
				}
			});
			context.getInput().pressKey(InputConstants.KEY_H);
			context.waitFor(client -> Smp3Client.HUD.visible);
			context.setScreen(() -> new ChatScreen("", false));
			context.getInput().typeChars("H");
			context.waitTicks(2);
			context.runOnClient(client -> {
				if (!Smp3Client.HUD.visible || !(client.gui.screen() instanceof ChatScreen))
					throw new AssertionError("聊天输入影响 HUD");
			});
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(client -> client.gui.screen() == null);

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
			context.waitFor(client -> Smp3Client.HUD.state.rows.getFirst().entry.complete());
			context.waitTicks(10);
			context.runOnClient(client -> {
				if (Smp3Client.HUD.state.rows.size() != 4)
					throw new AssertionError("完成未领取条目消失");
			});
			context.takeScreenshot("quest-hud-completed");
			world.getServer().runOnServer(server -> {
				DailyQuestGui.show(world.getConnection().getServerPlayer());
				SimpleGui gui = (SimpleGui)SguiUtils.getCurrentGui(world.getConnection().getServerPlayer());
				var refresh = gui.getGuiElement(46).getItemStack();
				if (refresh.is(Items.ARROW) || refresh.is(Items.BARRIER))
					throw new AssertionError("完成后刷新按钮仍显示");
			});
			context.waitForScreen(AbstractContainerScreen.class);
			hoverSlot(context, 10);
			context.takeScreenshot("quest-menu-description");
			hoverSlot(context, 19);
			context.takeScreenshot("quest-menu-status");
			hoverSlot(context, 28);
			context.takeScreenshot("quest-menu-rewards");
			world.getServer().runOnServer(server -> {
				SimpleGui gui = (SimpleGui)SguiUtils.getCurrentGui(world.getConnection().getServerPlayer());
				gui.getGuiElement(28).getGuiCallback().click(28, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, gui);
				if (gui.getGuiElement(28).getItemStack().is(Items.CHEST))
					throw new AssertionError("领取后奖励箱仍显示");
			});
			context.waitFor(client -> Smp3Client.HUD.state.rows.getFirst().entry.claimed());
			context.waitTicks(2);
			context.takeScreenshot("quest-menu-claimed");
			context.waitTicks(10);
			context.runOnClient(client -> {
				if (Smp3Client.HUD.state.rows.getFirst().slideTime != 0)
					throw new AssertionError("菜单中提前播放滑出");
			});
			world.getServer().runOnServer(server -> world.getConnection().getServerPlayer().closeContainer());
			context.waitFor(client
			                -> client.gui.screen() == null &&
			                       Smp3Client.HUD.state.rows.stream().anyMatch(row -> row.slideTime > 0));
			context.takeScreenshot("quest-hud-sliding");
			context.waitFor(client -> client.gui.screen() == null && Smp3Client.HUD.state.rows.size() == 3);
			context.takeScreenshot("quest-hud-claimed");
			context.getInput().resizeWindow(1280, 720);
			context.runOnClient(client -> client.options.guiScale().set(3));
			context.waitTicks(5);
			context.takeScreenshot("quest-hud-scale-3");
			context.getInput().pressKey(InputConstants.KEY_F1);
			context.waitFor(client -> client.gui.hud.isHidden());
			context.takeScreenshot("quest-hud-f1-hidden");
			context.getInput().pressKey(InputConstants.KEY_F1);
			context.waitFor(client -> !client.gui.hud.isHidden());
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
			double y = (window.getGuiScaledHeight() - 222) / 2 + 18 + slot / 9 * 18 + 8;
			return new double[] {x * window.getScreenWidth() / window.getGuiScaledWidth(),
			                     y * window.getScreenHeight() / window.getGuiScaledHeight()};
		});
		context.getInput().setCursorPos(position[0], position[1]);
		context.waitTicks(2);
	}
}
