package club.sjmc.smp3.shop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.item.ItemDelivery;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ShopChecks {
	public static void install(Path file) throws IOException { Files.writeString(file, fixture().toString()); }
	private static JsonObject fixture() {
		JsonObject root = JsonParser.parseString("""
            {"shops":[{"id":"test","name":"Test shop","icon":"minecraft:emerald","children":[
            {"id":"trades","name":"Trades","icon":"minecraft:paper","entries":[
            {"id":"test.buy","direction":"buy","item":{"id":"minecraft:granite","count":32},"price":2,"dailyLimit":64},
            {"id":"test.sell","direction":"sell","accept":{"item":"minecraft:granite","count":2},"price":3,"dailyLimit":4},
            {"id":"test.reset","direction":"buy","item":{"id":"minecraft:stone"},"price":1,"dailyLimit":1},
            {"id":"test.custom","direction":"buy","icon":"minecraft:diamond","item":{"id":"minecraft:paper","count":2,"components":{"minecraft:custom_name":"Special"}},"currency":"smp3:coffee_stamp","price":1},
            {"id":"test.tag","direction":"sell","name":"Planks","icon":"minecraft:oak_planks","accept":{"item":"#minecraft:planks","count":2},"price":1}
            ]}]}]}
            """).getAsJsonObject();
		var shops = root.getAsJsonArray("shops");
		var shop = shops.get(0).getAsJsonObject();
		var categories = shop.getAsJsonArray("children");
		var category = categories.get(0).getAsJsonObject();
		var entries = category.getAsJsonArray("entries");
		while (entries.size() < 28) {
			var entry = entries.get(0).deepCopy().getAsJsonObject();
			entry.addProperty("id", "filler." + entries.size());
			entries.add(entry);
		}
		while (categories.size() < 28) {
			var next = category.deepCopy();
			next.addProperty("id", "category." + categories.size());
			next.add("entries", new JsonArray());
			categories.add(next);
		}
		while (shops.size() < 28) {
			var next = shop.deepCopy();
			next.addProperty("id", "shop." + shops.size());
			next.add("children", new JsonArray());
			shops.add(next);
		}
		categories.get(1).getAsJsonObject().add("children", JsonParser.parseString("""
            [{"id":"nested","name":"Nested","icon":"minecraft:book","children":[
              {"id":"deep","name":"Deep","icon":"minecraft:paper"}]}]
            """).getAsJsonArray());
		return root;
	}
	public static void run(GameTestHelper test, ServerPlayer player) {
		ShopCatalog catalog = ShopModule.catalog();
		try (var input = ShopChecks.class.getResourceAsStream("/shops.json")) {
			var defaults =
			    JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
			test.assertTrue(ShopCatalog.parse(defaults, player.registryAccess()).entries().size() == 43,
			                "Default catalog changed");
		} catch (IOException error) {
			throw new AssertionError(error);
		}
		JsonObject invalid = fixture();
		var entry = invalid.getAsJsonArray("shops")
		                .get(0)
		                .getAsJsonObject()
		                .getAsJsonArray("children")
		                .get(0)
		                .getAsJsonObject()
		                .getAsJsonArray("entries")
		                .get(0)
		                .getAsJsonObject();
		entry.addProperty("price", 0.5);
		try {
			ShopCatalog.parse(invalid, player.registryAccess());
			throw new AssertionError("Fractional price accepted");
		} catch (IllegalArgumentException expected) {
			test.assertTrue(expected.getMessage().contains("test.buy"), "Config error omitted entry location");
		}
		entry.addProperty("price", 2);
		entry.addProperty("id", "test.sell");
		try {
			ShopCatalog.parse(invalid, player.registryAccess());
			throw new AssertionError("Duplicate entry accepted");
		} catch (IllegalArgumentException expected) {
		}

		test.assertTrue(ShopService.trade(player, catalog, "test.buy", 0) == ShopService.Status.INVALID_QUANTITY &&
		                    ShopService.trade(player, catalog, "test.buy", Integer.MAX_VALUE) ==
		                        ShopService.Status.INVALID_QUANTITY,
		                "Invalid batch count accepted");
		player.getInventory().clearContent();
		Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.TONGBAO, 10000);
		Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.COFFEE_STAMP, 100);
		test.assertTrue(ShopService.trade(player, catalog, "test.buy", 1) == ShopService.Status.BOUGHT,
		                "Purchase failed");
		ShopService.trade(player, catalog, "test.buy", 1);
		test.assertTrue(ShopService.trade(player, catalog, "test.buy", 1) == ShopService.Status.QUOTA, "Quota ignored");
		test.assertTrue(player.getInventory().getItem(0).getCount() == 64 && balance(player) == 9600 &&
		                    ShopService.usage(player).get("test.buy") == 64,
		                "Purchase state diverged");
		test.assertTrue(ShopService.trade(player, catalog, "test.sell", 1) == ShopService.Status.SOLD &&
		                    balance(player) == 9900,
		                "Sale failed");
		JsonObject persisted;
		Path account = FabricLoader.getInstance().getConfigDir().resolve("smp3/accounts/" + player.getUUID() + ".json");
		try {
			persisted = JsonParser.parseString(Files.readString(account))
			                .getAsJsonObject()
			                .getAsJsonObject("rewardStates")
			                .getAsJsonObject(ItemDelivery.LEDGER_KEY);
		} catch (IOException error) {
			throw new AssertionError(error);
		}
		test.assertTrue(ShopQuota.read(persisted).used().equals(ShopService.usage(player)), "Quota was not persisted");
		Path blocked = account.resolveSibling(account.getFileName() + ".tmp");
		try {
			Files.createDirectory(blocked);
			test.assertTrue(ShopService.trade(player, catalog, "test.sell", 1) == ShopService.Status.FAILED,
			                "Failed write accepted");
			test.assertTrue(player.getInventory().getItem(0).getCount() == 62 && balance(player) == 9900 &&
			                    ShopService.usage(player).get("test.sell") == 2,
			                "Failed write changed inventory, balance or quota");
		} catch (IOException error) {
			throw new AssertionError(error);
		} finally {
			try {
				Files.delete(blocked);
			} catch (IOException error) {
				throw new AssertionError(error);
			}
		}

		player.getInventory().clearContent();
		ItemStack named = new ItemStack(Items.GRANITE, 2);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep"));
		player.getInventory().setItem(0, named);
		test.assertTrue(ShopService.trade(player, catalog, "test.sell", 1) == ShopService.Status.NOT_ENOUGH_ITEMS &&
		                    named.getCount() == 2,
		                "Ordinary sale consumed customized items");
		player.getInventory().setItem(1, new ItemStack(Items.OAK_PLANKS));
		player.getInventory().setItem(2, new ItemStack(Items.BIRCH_PLANKS));
		test.assertTrue(ShopService.trade(player, catalog, "test.tag", 1) == ShopService.Status.SOLD &&
		                    player.getInventory().getItem(1).isEmpty() && player.getInventory().getItem(2).isEmpty(),
		                "Tag sale did not combine items");
		for (int i = 0; i < 36; i++)
			player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
		test.assertTrue(ShopService.trade(player, catalog, "test.custom", 1) == ShopService.Status.BOUGHT &&
		                    Smp3AccountStore.balance(player.getUUID(), Smp3Currency.COFFEE_STAMP) == 0,
		                "Custom purchase failed");
		test.assertTrue(
		    ItemDelivery.pending(player).stream().anyMatch(item
			                                               -> item.is(Items.PAPER) && item.getCount() == 2 &&
			                                                      item.getHoverName().getString().equals("Special")),
		    "Configured output lost components or used icon");
		player.getInventory().setItem(0, ItemStack.EMPTY);
		ItemDelivery.deliver(player);
		test.assertTrue(player.getInventory().getItem(0).is(Items.PAPER) && ItemDelivery.pending(player).isEmpty(),
		                "Pending purchase was not delivered");
		test.assertTrue(ShopService.trade(player, catalog, "test.custom", 1) == ShopService.Status.NO_MONEY,
		                "Insufficient balance accepted");

		player.closeContainer();
		player.getInventory().clearContent();
	}
	private static long balance(ServerPlayer player) {
		return Smp3AccountStore.balance(player.getUUID(), Smp3Currency.TONGBAO);
	}
}
