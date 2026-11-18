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

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.EditBox;

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
			context.waitForScreen(ShopScreen.class);
			context.clickScreenButton("下一页");
			context.clickScreenButton("上一页");
			context.runOnClient(client -> {
				var screen = (ShopScreen)client.gui.screen();
				screen.selectNode("trades", true);
				screen.selectEntry("test.buy");
			});
			context.waitFor(client -> Smp3Client.SHOP.quote != null && !Smp3Client.SHOP.pending);
			context.clickScreenButton("下一页");
			context.runOnClient(client -> {
				if (!Smp3Client.SHOP.selected.isEmpty())
					throw new AssertionError("Hidden product remained selected");
			});
			context.clickScreenButton("上一页");
			context.runOnClient(client -> ((ShopScreen)client.gui.screen()).selectEntry("test.buy"));
			context.waitFor(client -> Smp3Client.SHOP.quote != null && !Smp3Client.SHOP.pending);
			var request = context.computeOnClient(client -> {
				if (Smp3Client.SHOP.quote.maximum() != 2)
					throw new AssertionError("Wrong maximum batch count");
				var input = (EditBox)client.gui.screen()
				                .children()
				                .stream()
				                .filter(EditBox.class ::isInstance)
				                .findFirst()
				                .orElseThrow();
				input.setValue("2");
				return new ShopPayloads.Request(Smp3Client.SHOP.session, ShopPayloads.Action.TRADE, "test.buy",
				                                Smp3Client.SHOP.version, 2);
			});
			context.takeScreenshot("shop-parchment");
			context.clickScreenButton("花费 4 通宝");
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
			context.clickScreenButton("下一页");
			context.clickScreenButton("上一页");
			context.clickScreenButton("Test shop");
			context.clickScreenButton("商店");
			context.takeScreenshot("shop-root-narrow");
			context.runOnClient(client -> {
				var screen = (ShopScreen)client.gui.screen();
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
			entry.addProperty("dailyLimit", 32);
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
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(client -> client.gui.screen() == null);
			context.getInput().resizeWindow(854, 480);
		} catch (IOException error) {
			throw new UncheckedIOException(error);
		}
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
