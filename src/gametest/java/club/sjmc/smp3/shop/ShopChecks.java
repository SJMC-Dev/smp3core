package club.sjmc.smp3.shop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

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
            {"id":"test.buy","direction":"buy","item":{"id":"minecraft:granite","count":32},"price":2,"limit":{"period":"daily","normal":64}},
            {"id":"test.sell","direction":"sell","accept":{"item":"minecraft:granite","count":2},"price":3,"limit":{"period":"daily","normal":4}},
            {"id":"test.reset","direction":"buy","item":{"id":"minecraft:stone"},"price":1,"limit":{"period":"daily","normal":1}},
            {"id":"test.custom","direction":"buy","icon":"minecraft:diamond","item":{"id":"minecraft:paper","count":2,"components":{"minecraft:custom_name":"Special"}},"currency":"smp3:coffee_stamp","price":1},
            {"id":"test.title","direction":"buy","name":"Test title","icon":"minecraft:name_tag","title":"smp3_test_shop","price":1},
            {"id":"test.title.alias","direction":"buy","name":"Same title","icon":"minecraft:name_tag","title":"smp3_test_shop","price":1},
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
		rotationAndQuota(test, player);
		try (var input = ShopChecks.class.getResourceAsStream("/shops.json")) {
			var defaults =
			    JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
			test.assertTrue(ShopCatalog.parse(defaults, player.registryAccess()).entries().size() == 108,
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

		Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.TONGBAO, 200);
		test.assertTrue(ShopService.trade(player, catalog, "test.title", 2) == ShopService.Status.INVALID_QUANTITY,
		                "Title accepted multiple purchases");
		test.assertTrue(ShopService.trade(player, catalog, "test.title", 1) == ShopService.Status.PENDING &&
		                    balance(player) == 100,
		                "Title purchase did not commit payment and delivery together");
		ShopTitles.deliver(player);
		test.assertTrue(ShopService.trade(player, catalog, "test.title.alias", 1) == ShopService.Status.PENDING &&
		                    balance(player) == 100 && ShopTitles.pending(player, "smp3_test_shop"),
		                "Title retry or alternate offer charged twice");

		player.closeContainer();
		player.getInventory().clearContent();
	}
	private static void rotationAndQuota(GameTestHelper test, ServerPlayer player) {
		var json = JsonParser.parseString("""
            {"shops":[{"id":"market","name":"Market","icon":"minecraft:emerald",
            "rotation":{"enabled":true,"period":"weekly","pools":[{"id":"pool","count":2,"entries":[
            {"id":"one","direction":"buy","item":{"id":"minecraft:stone"},"price":1,"weight":1,"limit":{"period":"weekly","normal":2}},
            {"id":"two","direction":"buy","item":{"id":"minecraft:dirt"},"price":1,"weight":3},
            {"id":"three","direction":"buy","item":{"id":"minecraft:gravel"},"price":1,"weight":1}
            ]}]}}]}
            """).getAsJsonObject();
		var source = ShopCatalog.parse(json, player.registryAccess());
		var pool = source.shops().getFirst().rotation().pools().getFirst();
		var picks = ShopRotation.draw(pool, new Random(0) {
			@Override
			public long nextLong(long bound) {
				return 1;
			}
		});
		test.assertTrue(picks.equals(List.of("two", "three")), "Weighted selection boundary or removal incorrect");
		var monday = LocalDate.of(2030, 2, 4);
		var quota = new ShopQuota(new JsonObject(), monday);
		var state = new JsonObject();
		var entry = source.entries().get("one");
		quota.record(state, entry, 1);
		var saved = state.getAsJsonObject("shop");
		test.assertTrue(new ShopQuota(saved, monday.plusDays(1)).usage(entry).count() == 1 &&
		                    new ShopQuota(saved, monday.plusWeeks(1)).usage(entry).count() == 0 &&
		                    new ShopQuota(saved, monday.minusDays(1)).usage(entry).count() == 1,
		                "Weekly quota reset early, survived expiry, or reset on clock rollback");
		var changed = json.deepCopy();
		changed.getAsJsonArray("shops")
		    .get(0)
		    .getAsJsonObject()
		    .getAsJsonObject("rotation")
		    .getAsJsonArray("pools")
		    .get(0)
		    .getAsJsonObject()
		    .getAsJsonArray("entries")
		    .get(0)
		    .getAsJsonObject()
		    .getAsJsonObject("limit")
		    .addProperty("period", "daily");
		var daily = ShopCatalog.parse(changed, player.registryAccess()).entries().get("one");
		test.assertTrue(new ShopQuota(saved, monday.plusDays(1)).usage(daily).weekly(),
		                "Reload changed active quota period");
		Path directory = null;
		try {
			directory = Files.createTempDirectory("smp3-shop-check");
			Path file = directory.resolve("rotation.json");
			var rotation = new ShopRotation(file);
			var first = rotation.select(source, monday, new Random(12));
			rotation.commit(first);
			var reopened = new ShopRotation(file);
			var next = reopened.select(source, monday.plusDays(1), new Random(99));
			test.assertTrue(first.catalog().entries().keySet().equals(next.catalog().entries().keySet()) &&
			                    next.catalog().entries().size() == 2,
			                "Restart or day change rerolled weekly offers");
			var rotationJson = json.getAsJsonArray("shops").get(0).getAsJsonObject().getAsJsonObject("rotation");
			rotationJson.addProperty("enabled", false);
			var disabled = reopened.select(ShopCatalog.parse(json, player.registryAccess()), monday, new Random(99));
			reopened.commit(disabled);
			test.assertTrue(disabled.catalog().entries().isEmpty(), "Disabled rotation remained on sale");
			rotationJson.addProperty("enabled", true);
			var poolJson = rotationJson.getAsJsonArray("pools").get(0).getAsJsonObject();
			var candidates = poolJson.getAsJsonArray("entries");
			String removed = first.catalog().entries().keySet().iterator().next();
			candidates.asList().removeIf(value -> value.getAsJsonObject().get("id").getAsString().equals(removed));
			poolJson.addProperty("count", 1);
			var reduced = ShopCatalog.parse(json, player.registryAccess());
			var active = reopened.select(reduced, monday, new Random(1));
			test.assertTrue(active.catalog().entries().size() == 1 && !active.catalog().entries().containsKey(removed),
			                "Reload backfilled a removed selection");
			var following = reopened.select(reduced, monday.plusWeeks(1), new Random(1));
			test.assertTrue(following.catalog().entries().size() == 1 &&
			                    following.catalog().refreshes().values().iterator().next().equals(monday.plusWeeks(2)),
			                "Next week did not adopt updated selection count");
			poolJson.addProperty("count", 3);
			try {
				ShopCatalog.parse(json, player.registryAccess());
				throw new AssertionError("Insufficient pool accepted");
			} catch (IllegalArgumentException expected) {
			}
		} catch (IOException error) {
			throw new AssertionError(error);
		} finally {
			if (directory != null) {
				try {
					Files.deleteIfExists(directory.resolve("rotation.json"));
					Files.delete(directory);
				} catch (IOException error) {
					throw new AssertionError(error);
				}
			}
		}
	}

	private static long balance(ServerPlayer player) {
		return Smp3AccountStore.balance(player.getUUID(), Smp3Currency.TONGBAO);
	}
}
