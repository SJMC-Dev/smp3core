package club.sjmc.smp3.ui;

import java.lang.reflect.Proxy;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import club.sjmc.smp3.title.TitlePayloads;
import club.sjmc.smp3.ui.panel.TitlePanel;
import club.sjmc.smp3.ui.screen.MenuScreen;
import club.sjmc.smp3.ui.widget.PanelContext;
import club.sjmc.smp3.ui.widget.Rect;

import mod.fuji.core.auxiliary.minecraft.LuckpermsHelper;
import mod.fuji.module.initializer.title.TitleInitializer;
import mod.fuji.module.initializer.title.service.TitleService;
import mod.fuji.module.initializer.title.structure.TitleDescriptor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.util.Tristate;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

final class TitleClientChecks {

	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		try {
			var api = LuckpermsHelper.class.getDeclaredField("instance");
			api.setAccessible(true);
			Object previous = api.get(null);
			var owned = new TitleDescriptor()
			                .setId("smp3_test_owned")
			                .setItem("minecraft:name_tag")
			                .setDisplayName("测试称号")
			                .setLore(List.of());
			var locked = new TitleDescriptor()
			                 .setId("smp3_test_locked")
			                 .setItem("minecraft:name_tag")
			                 .setDisplayName("未解锁称号")
			                 .setLore(List.of());
			api.set(null, permissions(LuckPerms.class));
			world.getServer().runOnServer(server -> {
				TitleService.getAllTitles().addAll(List.of(owned, locked));
				TitleService.setActiveTitle(world.getConnection().getServerPlayer(), null);
			});
			try {
				context.runOnClient(client -> client.getConnection().sendCommand("smp3 menu"));
				context.waitForScreen(MenuScreen.class);
				context.runOnClient(client -> ((MenuScreen)client.gui.screen()).openTab(4));
				context.waitFor(client -> ((MenuScreen)client.gui.screen()).activePanel() instanceof TitlePanel);
				context.runOnClient(client -> {
					var entry = new TitlePayloads.Entry(owned.getId(), owned.getItem(), Component.literal("测试称号"),
					                                    List.of(), true);
					var panel = new TitlePanel(new TitlePayloads.Catalog(List.of(entry), ""));
					panel.layout(new Rect(0, 0, 300, 200), new PanelContext(0, 0));
					if (!panel.mouseClicked(
					        new MouseButtonEvent(30, 25, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)),
					        false))
						throw new AssertionError("Title click was not handled");
				});
				context.waitFor(client -> active((MenuScreen)client.gui.screen()).equals(owned.getId()));
				world.getServer().runOnServer(server -> {
					TitleInitializer.data.readStorage();
					if (!TitleService.getActiveTitle(world.getConnection().getServerPlayer())
					         .orElseThrow()
					         .getId()
					         .equals(owned.getId()))
						throw new AssertionError("Equipped title was not persisted");
				});
				for (String rejected : List.of(locked.getId(), "smp3_test_missing")) {
					context.runOnClient(client -> {
						((MenuScreen)client.gui.screen()).acceptTitles(new TitlePayloads.Catalog(List.of(), ""));
						ClientPlayNetworking.send(new TitlePayloads.Select(rejected));
					});
					context.waitFor(client -> active((MenuScreen)client.gui.screen()).equals(owned.getId()));
				}
			} finally {
				world.getServer().runOnServer(server -> {
					TitleService.setActiveTitle(world.getConnection().getServerPlayer(), null);
					TitleService.getAllTitles().removeAll(List.of(owned, locked));
				});
				api.set(null, previous);
				context.runOnClient(client -> client.gui.setScreen(null));
			}
		} catch (ReflectiveOperationException error) {
			throw new AssertionError(error);
		}
	}

	private static String active(MenuScreen menu) {
		try {
			var field = TitlePanel.class.getDeclaredField("catalog");
			field.setAccessible(true);
			return ((TitlePayloads.Catalog)field.get(menu.activePanel())).activeId();
		} catch (ReflectiveOperationException error) {
			throw new AssertionError(error);
		}
	}

	private static Object permissions(Class<?> type) {
		return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
			return switch (method.getName()) {
				case "isLoaded" -> true;
				case "getUserManager", "getUser", "getCachedData", "getPermissionData" ->
					permissions(method.getReturnType());
				case "checkPermission" ->
					"fuji.title.obtain.smp3_test_owned".equals(args[0]) ? Tristate.TRUE : Tristate.FALSE;
				default -> throw new AssertionError("Unexpected permission call: " + method);
			};
		});
	}
}
