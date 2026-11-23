package club.sjmc.smp3.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;

import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.shop.ShopCatalog;
import club.sjmc.smp3.shop.ShopModule;
import club.sjmc.smp3.shop.ShopPayloads;
import club.sjmc.smp3.shop.ShopService;
import club.sjmc.smp3.ui.panel.ShopPanel;
import club.sjmc.smp3.ui.screen.MenuScreen;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

final class ShopClientChecks {
	static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		var path = FabricLoader.getInstance().getConfigDir().resolve("smp3/shops.json");
		try {
			String original = Files.readString(path);
			context.getInput().resizeWindow(1280, 800);
			var previous = new AtomicReference<ShopCatalog>();
			world.getServer().runOnServer(server -> {
				var player = world.getConnection().getServerPlayer();
				previous.set(ShopModule.catalog());
				player.getInventory().clearContent();
				Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.TONGBAO, 10000);
				if (!ShopModule.open(player))
					throw new AssertionError("Shop client protocol unavailable");
			});
			context.waitFor(client -> shop(client) != null);
			context.runOnClient(client -> {
				var screen = shop(client);
				screen.selectNode("trades", true);
				screen.selectEntry("test.buy");
			});
			context.waitFor(client -> Smp3Client.SHOP.quote != null && !Smp3Client.SHOP.pending);
			var request = context.computeOnClient(client -> {
				if (Smp3Client.SHOP.quote.maximum() != 2)
					throw new AssertionError("Wrong maximum batch count");
				var input = shop(client).quantityInput();
				var panel = shop(client);
				panel.mouseClicked(new MouseButtonEvent(input.getX() + input.getWidth() / 2.0, input.getY() + 2,
				                                        new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)),
				                   false);
				panel.keyPressed(new KeyEvent(InputConstants.KEY_A, 'a', InputConstants.MOD_CONTROL));
				panel.charTyped(new CharacterEvent('2'));
				if (!input.getValue().equals("2"))
					throw new AssertionError("Quantity input did not accept keyboard editing after a click: value=" +
					                         input.getValue() + " focused=" + input.isFocused());
				return new ShopPayloads.Request(Smp3Client.SHOP.session, ShopPayloads.Action.TRADE, "test.buy",
				                                Smp3Client.SHOP.version, 2);
			});
			context.takeScreenshot("shop-parchment");
			context.runOnClient(client -> shop(client).confirmTrade());
			context.waitFor(client -> Smp3Client.SHOP.quote.used() == 64 && !Smp3Client.SHOP.pending);
			context.runOnClient(client -> ClientPlayNetworking.send(request));
			context.waitFor(client -> Smp3Client.SHOP.message.contains("变化"));
			world.getServer().runOnServer(server -> {
				var player = world.getConnection().getServerPlayer();
				if (Smp3AccountStore.balance(player.getUUID(), Smp3Currency.TONGBAO) != 9600 ||
				    player.getInventory().getItem(0).getCount() != 64)
					throw new AssertionError("Bulk trade or replay protection failed");
			});
			context.getInput().resizeWindow(640, 480);
			context.takeScreenshot("shop-parchment-narrow");
			context.runOnClient(client -> (shop(client)).selectNode("", true));
			context.takeScreenshot("shop-root-narrow");
			context.runOnClient(client -> {
				var screen = shop(client);
				screen.selectNode("trades", true);
				screen.selectEntry("test.buy");
			});
			Files.writeString(path, "{\"shops\":[{\"id\":\"invalid\"}]}");
			reload(context, world, false);
			world.getServer().runOnServer(server -> {
				if (ShopModule.catalog() != previous.get())
					throw new AssertionError("Failed reload replaced catalog");
			});
			var updated = JsonParser.parseString(original).getAsJsonObject();
			var shops = updated.getAsJsonArray("shops");
			while (shops.size() > 1)
				shops.remove(shops.size() - 1);
			var entries = shops.get(0)
			                  .getAsJsonObject()
			                  .getAsJsonArray("children")
			                  .get(0)
			                  .getAsJsonObject()
			                  .getAsJsonArray("entries");
			var entry = entries.get(0).getAsJsonObject();
			entry.addProperty("price", 3);
			entry.getAsJsonObject("limit").addProperty("normal", 32);
			while (entries.size() > 1)
				entries.remove(entries.size() - 1);
			Files.writeString(path, updated.toString());
			reload(context, world, true);
			context.waitFor(client -> Smp3Client.SHOP.entries.size() == 1 && Smp3Client.SHOP.quote != null);
			world.getServer().runOnServer(server -> {
				var player = world.getConnection().getServerPlayer();
				if (ShopService.trade(player, previous.get(), "test.buy", 1) != ShopService.Status.STALE ||
				    ShopService.trade(player, ShopModule.catalog(), "test.buy", 1) != ShopService.Status.QUOTA ||
				    Smp3AccountStore.balance(player.getUUID(), Smp3Currency.TONGBAO) != 9600)
					throw new AssertionError("Reload accepted stale price or erased quota");
			});
			Files.writeString(path, original);
			reload(context, world, true);
			context.waitFor(
			    client -> Smp3Client.SHOP.entries.containsKey("test.title") && !Smp3Client.SHOP.pendingOpen);
			context.runOnClient(client -> (shop(client)).selectEntry("test.title"));
			context.waitFor(client -> Smp3Client.SHOP.quote != null && !Smp3Client.SHOP.pending);
			context.runOnClient(client -> {
				if (!Smp3Client.SHOP.entries.get("test.title").title() || Smp3Client.SHOP.quote.maximum() != 1)
					throw new AssertionError("Title offer did not synchronize single-purchase state");
			});
			context.runOnClient(client -> shop(client).confirmTrade());
			context.waitFor(client -> {
				if (!Smp3Client.SHOP.pending && !Smp3Client.SHOP.quote.pendingTitle() &&
				    !Smp3Client.SHOP.message.isEmpty())
					throw new AssertionError("Title trade rejected: " + Smp3Client.SHOP.message);
				return Smp3Client.SHOP.quote.pendingTitle() && !Smp3Client.SHOP.pending;
			});
			context.takeScreenshot("shop-title-pending");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(client -> client.gui.screen() == null);
			context.getInput().resizeWindow(854, 480);
		} catch (IOException error) {
			throw new UncheckedIOException(error);
		}
	}
	private static ShopPanel shop(Minecraft client) {
		return client.gui.screen() instanceof MenuScreen menu && menu.activePanel() instanceof ShopPanel panel ? panel
		                                                                                                       : null;
	}

	private static void reload(ClientGameTestContext context, TestSingleplayerContext world, boolean valid) {
		var result = new AtomicReference<Boolean>();
		world.getServer().runOnServer(server -> {
			if (!ShopModule.reload(message -> result.set(true), message -> result.set(false)))
				throw new AssertionError("Reload remained busy");
			if (ShopModule.reload(message -> {}, message -> {}))
				throw new AssertionError("Concurrent reload accepted");
		});
		context.waitFor(client -> result.get() != null);
		if (result.get() != valid)
			throw new AssertionError("Unexpected reload result");
	}
}
