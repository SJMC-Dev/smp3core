package club.sjmc.smp3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.YearMonth;
import java.util.Map;

import com.google.gson.Gson;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.SguiUtils;
import eu.pb4.sgui.api.gui.SimpleGui;

import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.item.ItemDelivery;
import club.sjmc.smp3.item.Smp3Items;
import club.sjmc.smp3.item.ui.EnchantBookGui;
import club.sjmc.smp3.milestone.MilestoneChecks;
import club.sjmc.smp3.shop.ShopChecks;
import club.sjmc.smp3.sign.PlayerSignData;
import club.sjmc.smp3.player.PlayerRidingChecks;
import club.sjmc.smp3.sign.SignFailureChecks;
import club.sjmc.smp3.sign.SignService;
import club.sjmc.smp3.time.ServerDates;
import club.sjmc.smp3.time.SignDateChecks;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class FeatureChecks implements ModInitializer {
	@Override
	public void onInitialize() {
		var path = FabricLoader.getInstance().getConfigDir().resolve("smp3/dailyquests.json");
		try (var input = FeatureChecks.class.getResourceAsStream("/dailyquests-fixture.json")) {
			Files.createDirectories(path.getParent());
			var accounts = path.resolveSibling("accounts");
			if (Files.isDirectory(accounts)) {
				try (var files = Files.list(accounts)) {
					for (var file : files.toList())
						Files.delete(file);
				}
			}
			Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
			ShopChecks.install(path.resolveSibling("shops.json"));
			for (String name : new String[] {"gacha.json", "sign.json"}) {
				try (var inputConfig = FeatureChecks.class.getResourceAsStream("/" + name)) {
					Files.copy(inputConfig, path.resolveSibling(name), StandardCopyOption.REPLACE_EXISTING);
				}
			}
			Files.writeString(path.resolveSibling("milestone-rewards.json"), "{\"enabled\":true,\"rules\":[]}");
			Files.writeString(path.resolveSibling("notices.json"), "{\"categories\":[],\"entries\":[]}");
			Files.writeString(path.resolveSibling("client.json"), "{\"questHudVisible\":false}");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static void run(GameTestHelper test, ServerPlayer player) throws Exception {
		PlayerRidingChecks.run(test, player);
		ShopChecks.run(test, player);
		Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.TONGBAO, 0);
		Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.COFFEE_STAMP, 0);
		var today = ServerDates.today();
		var legacy = new CompoundTag();
		legacy.putString("monthKey", YearMonth.from(today).toString());
		legacy.putLong("lastSignEpochDay", today.minusDays(1).toEpochDay());
		legacy.putInt("streak", 2);
		legacy.putInt("monthTotal", 2);
		legacy.putInt("serverTotal", 2);
		legacy.putIntArray("serverClaimed", new int[] {1, 2});
		PlayerSignData state = PlayerSignData.load(legacy);
		Smp3AccountStore.commitReward(player.getUUID(), "sign", Smp3AccountStore.rewardState(player.getUUID(), "sign"),
		                              new Gson().toJsonTree(state).getAsJsonObject(), Map.of());
		SignService.sign(player, today);
		SignService.sign(player, today);
		state = SignService.state(player);
		test.assertTrue(balance(player) == 19000 && state.streak() == 3 && state.monthTotal() == 3 &&
		                    state.serverTotal() == 3,
		                "Sign-in reward/count changed");
		test.assertTrue(Smp3AccountStore.balance(player.getUUID(), Smp3Currency.COFFEE_STAMP) == 400 &&
		                    count(player, Smp3Items.ANY_ENCHANTED_BOOK) == 1,
		                "Milestone reward changed");
		var restored = PlayerSignData.load(state.save());
		test.assertTrue(restored.isMonthClaimed(3) && restored.isServerClaimed(3) &&
		                    restored.isMonthSigned(today.getDayOfMonth()),
		                "Sign-in save changed");
		player.closeContainer();
		player.getInventory().clearContent();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Smp3Items.ANY_ENCHANTED_BOOK, 2));
		EnchantBookGui.open(player, InteractionHand.MAIN_HAND);
		SimpleGui book = (SimpleGui)SguiUtils.getCurrentGui(player);
		book.getGuiElement(0).getGuiCallback().click(0, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, book);
		test.assertTrue(count(player, Smp3Items.ANY_ENCHANTED_BOOK) == 1 && count(player, Items.ENCHANTED_BOOK) == 1,
		                "Book exchange changed");
		player.closeContainer();
		player.getInventory().clearContent();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
		}
		test.assertTrue(ItemDelivery.give(player, new ItemStack(Items.DIAMOND, 3)),
		                "Full inventory did not report overflow");
		var drops = player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4));
		test.assertTrue(
		    drops.stream().anyMatch(item -> item.getItem().is(Items.DIAMOND) && item.getItem().getCount() == 3),
		    "Overflow delivery lost items");
		player.getInventory().clearContent();
		SignFailureChecks.run(test, player);
		SignDateChecks.run(test, player);
		MilestoneChecks.run(test, player);
	}

	private static long balance(ServerPlayer player) {
		return Smp3AccountStore.balance(player.getUUID(), Smp3Currency.TONGBAO);
	}

	private static int count(ServerPlayer player, Item item) {
		int count = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item))
				count += stack.getCount();
		}
		return count;
	}
}
