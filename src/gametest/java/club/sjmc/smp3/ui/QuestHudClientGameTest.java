package club.sjmc.smp3.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;

import club.sjmc.smp3.daily.DailyQuestModule;
import club.sjmc.smp3.daily.QuestDefinition;
import club.sjmc.smp3.daily.QuestHudPayload;
import club.sjmc.smp3.daily.ui.DailyQuestGui;
import club.sjmc.smp3.daily.ui.QuestRewardsGui;
import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.gacha.GachaChecks;
import club.sjmc.smp3.item.ItemDefinitions;
import club.sjmc.smp3.item.ui.PackGui;
import club.sjmc.smp3.notice.NoticeFixture;
import club.sjmc.smp3.notice.NoticeModule;
import club.sjmc.smp3.notice.NoticePayloads;
import club.sjmc.smp3.ui.hud.HudLayout;
import club.sjmc.smp3.ui.panel.NoticePanel;
import club.sjmc.smp3.ui.screen.HudSettingsScreen;
import club.sjmc.smp3.ui.screen.NoticeEditorScreen;
import club.sjmc.smp3.ui.screen.PanelScreen;
import club.sjmc.smp3.ui.widget.BookText;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;

public final class QuestHudClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		TestWorldSave save;
		try (var world = context.worldBuilder().create()) {
			save = world.getWorldSave();
			world.getConnection().waitForChunksRender();
			context.waitFor(client -> !Smp3Client.HUD.state.rows.isEmpty());
			context.runOnClient(client -> {
				if (Smp3Client.HUD.visible)
					throw new AssertionError("Saved hidden preference was lost");
				if (client.getConnection().getCommands().getRoot().getChild("smp3").getChild("hud") == null)
					throw new AssertionError("HUD settings command unavailable to ordinary players");
				Smp3Client.toggleHud();
			});
			var expectedRows =
			    context.computeOnClient(client -> Smp3Client.HUD.state.rows.stream().map(row -> row.entry).toList());
			for (boolean visible : List.of(true, false)) {
				context.runOnClient(client -> {
					if (Smp3Client.HUD.visible != visible)
						Smp3Client.toggleHud();
					Smp3Client.HUD.state.clear();
					client.getConnection().sendCommand("smp3 hud");
				});
				context.waitForScreen(HudSettingsScreen.class);
				context.waitFor(
				    client -> Smp3Client.HUD.state.rows.stream().map(row -> row.entry).toList().equals(expectedRows));
				context.runOnClient(client -> {
					if (Smp3Client.HUD.visible != visible)
						throw new AssertionError("HUD refresh changed the visibility preference");
					client.gui.setScreen(null);
					Smp3Client.HUD.state.clear();
					ClientPlayNetworking.send(QuestHudPayload.Request.INSTANCE);
				});
				context.waitFor(
				    client -> Smp3Client.HUD.state.rows.stream().map(row -> row.entry).toList().equals(expectedRows));
				context.runOnClient(client -> {
					if (Smp3Client.HUD.visible != visible || client.gui.screen() != null)
						throw new AssertionError("HUD snapshot request changed client preferences or opened a screen");
				});
			}
			context.runOnClient(client -> Smp3Client.toggleHud());
			world.getServer().runOnServer(server -> {
				var player = world.getConnection().getServerPlayer();
				server.getPlayerList().op(new NameAndId(player.getGameProfile()),
				                          Optional.of(LevelBasedPermissionSet.GAMEMASTER), Optional.empty());
			});
			context.waitFor(client
			                -> client.getConnection()
			                           .getCommands()
			                           .getRoot()
			                           .getChild("smp3")
			                           .getChild("dailyquest")
			                           .getChild("player") != null);
			context.runOnClient(client -> {
				var connection = client.getConnection();
				var commands = connection.getCommands();
				for (String root : List.of("smp3 dailyquest", "smp3 dailyquests")) {
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
			ShopClientChecks.run(context, world);
			String token = context.computeOnClient(client -> Smp3Client.HUD.state.rows.getFirst().entry.token());

			world.getServer().runOnServer(server -> DailyQuestGui.show(world.getConnection().getServerPlayer()));
			context.waitForScreen(AbstractContainerScreen.class);
			hoverSlot(context, 10);
			context.takeScreenshot("quest-menu-description");
			hoverSlot(context, 19);
			context.takeScreenshot("quest-menu-progress-rewards");
			hoverSlot(context, 10);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitFor(client -> client.player.containerMenu instanceof ChestMenu menu && menu.getRowCount() == 3);
			hoverSlot(context, 1);
			context.takeScreenshot("quest-menu-conditions");
			hoverSlot(context, 22);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitFor(client -> client.player.containerMenu instanceof ChestMenu menu && menu.getRowCount() == 4);

			world.getServer().runOnServer(server -> {
				var player = world.getConnection().getServerPlayer();
				var service = DailyQuestModule.current();
				var task = service.state(player).assignments().getFirst();
				var facts = new HashMap<String, Set<String>>();
				task.definition.objectives().getFirst().objective().conditions().entrySet().forEach(
				    c -> facts.put(c.getKey(), Set.of(QuestDefinition.values(c.getValue()).getFirst())));
				for (int i = 0; i < task.target; i++) {
					service.record(player, new QuestDefinition.Event(
					                           task.definition.objectives().getFirst().objective().event(), facts));
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
			for (String taskId : List.of("craft_planks_a", "piglin_c")) {
				world.getServer().runOnServer(server -> {
					var player = world.getConnection().getServerPlayer();
					var reward = DailyQuestModule.current()
					                 .config.tasks()
					                 .stream()
					                 .filter(t -> t.id().equals(taskId))
					                 .findFirst()
					                 .orElseThrow()
					                 .rewards()
					                 .grants()
					                 .getFirst()
					                 .deepCopy();
					reward.remove("type");
					player.setItemInHand(InteractionHand.MAIN_HAND,
					                     ItemDefinitions.stack(reward, player.registryAccess()));
					PackGui.open(player, InteractionHand.MAIN_HAND);
				});
				context.waitForScreen(AbstractContainerScreen.class);
				hoverSlot(context, 12);
				context.takeScreenshot(taskId.equals("piglin_c") ? "random-pack" : "choice-pack");
				world.getServer().runOnServer(server -> world.getConnection().getServerPlayer().closeContainer());
				context.waitFor(client -> client.gui.screen() == null);
			}
			world.getServer().runOnServer(server -> {
				var player = world.getConnection().getServerPlayer();
				QuestRewardsGui.preview(player, List.of(player.getMainHandItem().copy()));
			});
			context.waitForScreen(AbstractContainerScreen.class);
			hoverSlot(context, 0);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitFor(client
			                -> client.gui.screen() != null &&
			                       client.gui.screen().getTitle().getString().equals(
			                           Component.translatable("item.smp3.random_pack").getString()));
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(client
			                -> client.gui.screen() != null &&
			                       client.gui.screen().getTitle().getString().equals("任务物品奖励"));
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(
			    client -> client.gui.screen() != null && client.gui.screen().getTitle().getString().equals("每日任务"));
			world.getServer().runOnServer(server -> world.getConnection().getServerPlayer().closeContainer());
			context.waitFor(client -> client.gui.screen() == null);
			context.takeScreenshot("reward-pack-item");
			context.getInput().resizeWindow(1280, 800);
			context.runOnClient(client -> {
				Smp3Client.layout = new HudLayout();
				Smp3Client.BOOK.clear();
			});
			context.getInput().pressKey(options -> options.keyChat);
			context.getInput().typeChars("/smp3 info");
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			waitForNotice(context);
			context.takeScreenshot("server-book-empty");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(client -> client.gui.screen() == null);
			context.runOnClient(client -> client.getConnection().sendCommand("smp3 info edit"));
			context.waitForScreen(NoticeEditorScreen.class);
			context.runOnClient(client -> {
				String rule = "1. "
				              + "除自然生成的奖励箱外，所有箱子、潜影盒等容器，无论他人是否标记，未经允许均**"
				              + "不可擅自拿取或借用其中物品**。";
				var field = new MultilineTextField(client.font, 120);
				field.setValue(rule);
				field.seekCursorToPoint(0, client.font.lineHeight);
				if (field.cursor() <= 3 || !field.value().equals(rule))
					throw new AssertionError("Chinese wrapping stranded the list marker or changed the source");
				var preview = BookText.layout(new NoticePayloads.Body("", "", rule, true), client.font, 120);
				if (client.font.width(preview.lines().getFirst().text()) <= client.font.width("1."))
					throw new AssertionError("Chinese preview stranded the list marker");
				for (var line : preview.lines())
					if (client.font.width(line.text()) * line.scale() > 120)
						throw new AssertionError("Chinese preview line overflowed");
				var inputs = client.gui.screen()
				                 .children()
				                 .stream()
				                 .filter(EditBox.class ::isInstance)
				                 .map(EditBox.class ::cast)
				                 .toList();
				inputs.get(0).setValue("editor-test");
				inputs.get(1).setValue("社区建设活动");
				client.gui.screen()
				    .children()
				    .stream()
				    .filter(MultiLineEditBox.class ::isInstance)
				    .map(MultiLineEditBox.class ::cast)
				    .findFirst()
				    .orElseThrow()
				    .setValue("# 一起建设公共花园\n\n欢迎带上**花卉和木材**参与活动。\n\n" + rule +
					          "\n\n1. 在出生点集合。\n2. "
				              + "和其他玩家商量分工。");
			});
			context.takeScreenshot("notice-editor-wide");
			context.getInput().resizeWindow(640, 480);
			context.takeScreenshot("notice-editor-narrow");
			context.clickScreenButton("预览");
			context.takeScreenshot("notice-editor-preview");
			context.clickScreenButton("正文");
			world.getServer().runOnServer(server
			                              -> server.getPlayerList().deop(
			                                  new NameAndId(world.getConnection().getServerPlayer().getGameProfile())));
			context.clickScreenButton("发布公告");
			context.waitFor(client
			                -> client.gui.screen() instanceof NoticeEditorScreen &&
			                       client.gui.screen().children().stream().anyMatch(
			                           child
			                           -> child instanceof Button button && button.active &&
			                                  button.getMessage().getString().equals("发布公告")));
			context.runOnClient(client -> {
				if (!client.gui.screen()
				         .children()
				         .stream()
				         .filter(MultiLineEditBox.class ::isInstance)
				         .map(MultiLineEditBox.class ::cast)
				         .findFirst()
				         .orElseThrow()
				         .getValue()
				         .contains("公共花园") ||
				    !Smp3Client.BOOK.messages.isEmpty())
					throw new AssertionError("Unauthorized publication changed content or discarded the draft");
			});
			world.getServer().runOnServer(
			    server
			    -> server.getPlayerList().op(new NameAndId(world.getConnection().getServerPlayer().getGameProfile()),
				                             Optional.of(LevelBasedPermissionSet.GAMEMASTER), Optional.empty()));
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.clickScreenButton("放弃修改");
			context.waitFor(client -> client.gui.screen() == null);
			context.runOnClient(client -> client.getConnection().sendCommand("smp3 info edit"));
			context.waitForScreen(NoticeEditorScreen.class);
			context.runOnClient(client -> {
				var fields = client.gui.screen()
				                 .children()
				                 .stream()
				                 .filter(EditBox.class ::isInstance)
				                 .map(EditBox.class ::cast)
				                 .toList();
				fields.get(0).setValue("editor-test");
				fields.get(1).setValue("社区建设活动");
			});
			context.takeScreenshot("notice-editor-metadata");
			context.clickScreenButton("需确认：否");
			context.clickScreenButton("正文");
			context.runOnClient(client
			                    -> client.gui.screen()
			                           .children()
			                           .stream()
			                           .filter(MultiLineEditBox.class ::isInstance)
			                           .map(MultiLineEditBox.class ::cast)
			                           .findFirst()
			                           .orElseThrow()
			                           .setValue("正文\n".repeat(2200) + "🌿"));
			context.clickScreenButton("发布公告");
			waitForNotice(context);
			context.waitFor(client
			                -> Smp3Client.BOOK.messages.size() == 1 &&
			                       Smp3Client.BOOK.messages.getFirst().id().equals("editor-test"));
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(client -> client.gui.screen() == null);
			context.runOnClient(client -> {
				if (Smp3Client.BOOK.unreadCount() != 1)
					throw new AssertionError("Required announcement was read without confirmation");
				client.getConnection().sendCommand("smp3 info edit editor-test");
			});
			context.waitForScreen(NoticeEditorScreen.class);
			context.clickScreenButton("正文");
			context.runOnClient(client
			                    -> client.gui.screen()
			                           .children()
			                           .stream()
			                           .filter(MultiLineEditBox.class ::isInstance)
			                           .map(MultiLineEditBox.class ::cast)
			                           .findFirst()
			                           .orElseThrow()
			                           .setValue("未发布的修改"));
			context.clickScreenButton("删除公告");
			context.takeScreenshot("notice-delete-confirmation");
			context.clickScreenButton("取消");
			context.runOnClient(client -> {
				if (!client.gui.screen()
				         .children()
				         .stream()
				         .filter(MultiLineEditBox.class ::isInstance)
				         .map(MultiLineEditBox.class ::cast)
				         .findFirst()
				         .orElseThrow()
				         .getValue()
				         .equals("未发布的修改"))
					throw new AssertionError("Cancelling deletion lost the draft");
			});
			context.clickScreenButton("删除公告");
			context.clickScreenButton("确认删除");
			waitForNotice(context);
			context.waitFor(client
			                -> Smp3Client.BOOK.messages.isEmpty() && Smp3Client.BOOK.unreadCount() == 0 &&
			                       !Smp3Client.BOOK.noticeVisible());
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(client -> client.gui.screen() == null);
			world.getServer().runOnServer(server -> NoticeFixture.join(world.getConnection().getServerPlayer()));
			context.waitTicks(2);
			context.runOnClient(client -> {
				if (Smp3Client.BOOK.pendingOpen != null || notice(client) != null)
					throw new AssertionError("Deleted announcement prompted on join");
			});
			context.getInput().resizeWindow(1280, 800);
			NoticeFixture.install();
			var result = new AtomicReference<String>();
			world.getServer().runOnServer(server -> {
				if (!NoticeModule.current().reload(result::set) || NoticeModule.current().reload(result::set))
					throw new AssertionError("Concurrent notice reload accepted");
				var player = world.getConnection().getServerPlayer();
				Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.TONGBAO, 1245000);
				Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.COFFEE_STAMP, 1800);
			});
			context.waitFor(client -> result.get() != null && Smp3Client.BOOK.messages.size() == 3);
			context.runOnClient(client -> {
				if (!Smp3Client.BOOK.title.equals("社区公告与指南") ||
				    !Smp3Client.BOOK.messages.getFirst().id().equals("rules"))
					throw new AssertionError("Configured title or pin ordering lost");
			});
			hudControls(context);
			world.getServer().runOnServer(server -> NoticeFixture.join(world.getConnection().getServerPlayer()));
			waitForNotice(context);
			context.waitFor(
			    client -> Smp3Client.BOOK.bodies.containsKey("rules") && Smp3Client.BALANCES.coins.received);
			context.takeScreenshot("server-book-join-confirmation");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(client -> client.gui.screen() == null);
			context.runOnClient(client -> {
				if (Smp3Client.BOOK.unreadCount() != 3)
					throw new AssertionError("Closing required notice marked it read");
			});
			world.getServer().runOnServer(server -> NoticeFixture.join(world.getConnection().getServerPlayer()));
			waitForNotice(context);
			context.runOnClient(client -> notice(client).acknowledge());
			context.waitFor(client -> !Smp3Client.BOOK.unread(0));
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(client -> client.gui.screen() == null);
			context.waitTicks(2);
			context.runOnClient(client -> {
				if (client.gui.screen() != null)
					throw new AssertionError("Required notices chained after confirmation");
			});

			context.waitTicks(2);
			context.takeScreenshot("hud-notice");
			context.runOnClient(client -> {
				Smp3Client.BOOK.selected = 2;
				client.gui.setScreen(new PanelScreen(new NoticePanel(Smp3Client.book())));
			});
			context.waitFor(client -> !Smp3Client.BOOK.unread(2) && Smp3Client.BALANCES.coins.value == 12450);
			context.takeScreenshot("server-book");
			world.getServer().runOnServer(server -> {
				var player = world.getConnection().getServerPlayer();
				Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.TONGBAO, 1257500);
				Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.COFFEE_STAMP, 1700);
			});
			context.waitFor(
			    client -> Smp3Client.BALANCES.coins.value == 12575 && Smp3Client.BALANCES.stamps.value == 17);
			context.takeScreenshot("server-book-balances");
			context.runOnClient(client -> {
				Smp3Client.BOOK.selected = 1;
				client.gui.setScreen(new PanelScreen(new NoticePanel(Smp3Client.book())));
			});
			context.waitTicks(2);
			context.takeScreenshot("server-book-rules");
			context.getInput().resizeWindow(1920, 1200);
			context.runOnClient(client -> {
				client.options.guiScale().set(0);
				client.resizeGui();
				client.gui.setScreen(new PanelScreen(new NoticePanel(Smp3Client.book())));
			});
			context.waitTicks(3);
			context.takeScreenshot("server-book-laptop");
			context.runOnClient(client -> {
				if (notice(client) == null || !notice(client).acknowledgeVisible())
					throw new AssertionError("Laptop margins hid the announcement detail pane");
				client.options.guiScale().set(2);
				client.resizeGui();
			});
			context.getInput().resizeWindow(640, 480);
			context.runOnClient(client -> client.gui.setScreen(new PanelScreen(new NoticePanel(Smp3Client.book()))));
			context.waitTicks(3);
			context.takeScreenshot("server-book-narrow");
			context.runOnClient(client -> notice(client).selectCategory("帮助"));
			context.waitTicks(2);
			context.takeScreenshot("server-book-empty-category");
			context.runOnClient(client -> notice(client).selectCategory("服规"));
			context.getInput().setCursorPos(80, 290);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitFor(client -> notice(client) != null && notice(client).acknowledgeVisible());
			context.takeScreenshot("server-book-narrow-detail");
			context.runOnClient(client -> notice(client).acknowledge());
			context.waitFor(client -> !Smp3Client.BOOK.unread(1));
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(client -> client.gui.screen() == null);
			world.getServer().runOnServer(server -> {
				NoticeFixture.join(world.getConnection().getServerPlayer());
				Smp3AccountStore.setBalance(world.getConnection().getServerPlayer().getUUID(), Smp3Currency.TONGBAO,
				                            1300000);
			});
			context.waitTicks(25);
			context.runOnClient(client -> {
				if (client.gui.screen() != null || Smp3Client.BOOK.unreadCount() != 0 ||
				    Smp3Client.BALANCES.coins.value != 12575)
					throw new AssertionError("Read receipt or closed balance subscription failed");
			});
			result.set(null);
			try {
				Files.writeString(NoticeFixture.INDEX, "invalid JSON");
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			world.getServer().runOnServer(server -> NoticeModule.current().reload(result::set));
			context.waitFor(client -> result.get() != null);
			context.runOnClient(client -> {
				if (Smp3Client.BOOK.messages.size() != 3)
					throw new AssertionError("Failed reload replaced valid catalog");
			});
			NoticeFixture.install();
			GachaChecks.run(context, world);
		}
		context.runOnClient(client -> {
			if (!Smp3Client.BOOK.messages.isEmpty() || Smp3Client.BALANCES.coins.received ||
			    !Smp3Client.HUD.state.rows.isEmpty())
				throw new AssertionError("Session data remains after disconnect");
		});
		try (var world = save.open()) {
			world.getConnection().waitForChunksRender();
			context.waitFor(client -> Smp3Client.BOOK.messages.size() == 3);
			context.runOnClient(client -> {
				if (Smp3Client.BOOK.unreadCount() != 0 || notice(client) != null)
					throw new AssertionError("Read state lost after server restart");
			});
		}
	}

	private static NoticePanel notice(Minecraft client) {
		return client.gui.screen() instanceof PanelScreen screen &&
		       screen.panel() instanceof NoticePanel panel ? panel : null;
	}

	private static void waitForNotice(ClientGameTestContext context) {
		context.waitFor(client -> notice(client) != null);
	}

	private static void hudControls(ClientGameTestContext context) {
		String previous = context.computeOnClient(client -> KeyMapping.get("key.smp3.quest_hud").saveString());
		context.runOnClient(client -> {
			if (!Smp3Client.HUD.visible)
				Smp3Client.toggleHud();
			KeyMapping.get("key.smp3.quest_hud").setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_J));
			KeyMapping.resetMapping();
		});
		context.getInput().pressKey(InputConstants.KEY_J);
		context.waitFor(client -> !Smp3Client.HUD.visible && !Smp3Client.BOOK.noticeVisible());
		context.getInput().pressKey(InputConstants.KEY_J);
		context.waitFor(client -> Smp3Client.HUD.visible && Smp3Client.BOOK.noticeVisible());
		context.runOnClient(client -> {
			KeyMapping.get("key.smp3.quest_hud").setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_K));
			KeyMapping.resetMapping();
		});
		context.getInput().pressKey(InputConstants.KEY_J);
		context.runOnClient(client -> {
			if (!Smp3Client.HUD.visible)
				throw new AssertionError("Old binding still toggles HUD");
		});
		context.getInput().pressKey(InputConstants.KEY_K);
		context.waitFor(client -> !Smp3Client.HUD.visible);
		context.getInput().pressKey(InputConstants.KEY_K);
		context.waitFor(client -> Smp3Client.HUD.visible);
		context.getInput().pressKey(options -> options.keyChat);
		context.getInput().typeChars("/smp3 hud");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(HudSettingsScreen.class);
		dragHud(context, false);
		context.clickScreenButton(Component.translatable("smp3.hud.detach").getString());
		dragHud(context, true);
		context.takeScreenshot("hud-settings-independent");
		context.getInput().resizeWindow(640, 480);
		context.waitTicks(2);
		context.takeScreenshot("hud-settings-narrow");
		context.runOnClient(client -> {
			var window = client.getWindow();
			for (var box :
			     List.of(Smp3Client.HUD.box(window.getGuiScaledWidth(), window.getGuiScaledHeight(), true),
			             Smp3Client.HUD.noticeBox(window.getGuiScaledWidth(), window.getGuiScaledHeight(), true)))
				if (box.x() < 0 || box.y() < 0 || box.x() + box.width() > window.getGuiScaledWidth() ||
				    box.y() + box.height() > window.getGuiScaledHeight())
					throw new AssertionError("HUD outside resized screen");
		});
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitFor(client -> client.gui.screen() == null);
		try {
			var saved = JsonParser
			                .parseString(
			                    Files.readString(FabricLoader.getInstance().getConfigDir().resolve("smp3/client.json")))
			                .getAsJsonObject();
			var layout = saved.getAsJsonObject("layout");
			if (layout.get("followQuests").getAsBoolean() ||
			    layout.getAsJsonObject("quests").get("x").getAsFloat() >= .3F ||
			    layout.getAsJsonObject("notices").get("x").getAsFloat() <= .3F ||
			    !saved.get("hudVisible").getAsBoolean())
				throw new AssertionError("HUD controls were not saved");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		context.runOnClient(client -> client.gui.setScreen(new HudSettingsScreen()));
		context.clickScreenButton(Component.translatable("smp3.hud.reset").getString());
		context.clickScreenButton(Component.translatable("smp3.hud.bindings").getString());
		context.waitForScreen(KeyBindsScreen.class);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(HudSettingsScreen.class);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitFor(client -> client.gui.screen() == null);
		context.getInput().resizeWindow(1280, 800);
		context.runOnClient(client -> {
			if (Smp3Client.layout.quests.x != 1 || Smp3Client.layout.quests.y != 0 || !Smp3Client.layout.followQuests)
				throw new AssertionError("HUD reset failed");
			KeyMapping.get("key.smp3.quest_hud").setKey(InputConstants.getKey(previous));
			KeyMapping.resetMapping();
		});
	}

	private static void dragHud(ClientGameTestContext context, boolean notice) {
		var points = context.computeOnClient(client -> {
			var window = client.getWindow();
			int width = window.getGuiScaledWidth(), height = window.getGuiScaledHeight();
			var box = notice ? Smp3Client.HUD.noticeBox(width, height, true) : Smp3Client.HUD.box(width, height, true);
			double sx = (double)window.getScreenWidth() / width, sy = (double)window.getScreenHeight() / height;
			return new double[] {(box.x() + 6) * sx, (box.y() + 6) * sy, (notice ? width / 2.0 + 6 : 30) * sx,
			                     (notice ? 22 : 30) * sy};
		});
		context.getInput().setCursorPos(points[0], points[1]);
		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.getInput().setCursorPos(points[2], points[3]);
		context.waitTick();
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static void hoverSlot(ClientGameTestContext context, int slot) {
		var position = context.computeOnClient(client -> {
			var window = client.getWindow();
			double x = (window.getGuiScaledWidth() - 176) / 2 + 8 + slot % 9 * 18 + 8;
			int rows = ((ChestMenu)((AbstractContainerScreen<?>)client.gui.screen()).getMenu()).getRowCount();
			double y = (window.getGuiScaledHeight() - (114 + rows * 18)) / 2 + 18 + slot / 9 * 18 + 8;
			return new double[] {x * window.getScreenWidth() / window.getGuiScaledWidth(),
			                     y * window.getScreenHeight() / window.getGuiScaledHeight()};
		});
		context.getInput().setCursorPos(position[0], position[1]);
		context.waitTicks(2);
	}
}
