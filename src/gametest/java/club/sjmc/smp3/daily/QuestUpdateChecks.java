package club.sjmc.smp3.daily;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.google.gson.JsonParser;

import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.item.ItemDefinitions;
import club.sjmc.smp3.item.ItemDelivery;
import club.sjmc.smp3.item.PackItem;
import club.sjmc.smp3.time.ServerDates;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

final class QuestUpdateChecks {
	static void ownership(GameTestHelper test, ServerPlayer player, ItemEntity gold) throws Exception {
		var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.registryAccess());
		gold.saveWithoutId(saved);
		gold.removeAttached(BarterAttribution.OWNER);
		gold.load(TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), saved.buildResult()));
		test.assertTrue(player.getUUID().equals(gold.getAttached(BarterAttribution.OWNER)),
		                "Ground attribution did not persist");
		var other = new ItemEntity(player.level(), 0, 0, 0, new ItemStack(Items.GOLD_INGOT));
		var merge = ItemEntity.class.getDeclaredMethod("tryToMerge", ItemEntity.class);
		merge.setAccessible(true);
		merge.invoke(gold, other);
		test.assertTrue(gold.getItem().getCount() == 2 && !other.isRemoved(),
		                "Attributed and unattributed gold merged");
		other.setAttached(BarterAttribution.OWNER, UUID.randomUUID());
		merge.invoke(gold, other);
		test.assertTrue(gold.getItem().getCount() == 2, "Different owners merged");
		other.setAttached(BarterAttribution.OWNER, player.getUUID());
		merge.invoke(gold, other);
		test.assertTrue(gold.getItem().getCount() == 3 && other.isRemoved(), "Same-owner gold did not merge");
		var container = new SimpleContainer(5);
		for (int i = 0; i < 5; i++)
			container.setItem(i, new ItemStack(Items.GOLD_INGOT, 64));
		HopperBlockEntity.addItem(container, gold);
		test.assertTrue(gold.getItem().getCount() == 3 && gold.hasAttached(BarterAttribution.OWNER),
		                "Failed collection changed attribution");
		container.getItem(0).shrink(1);
		HopperBlockEntity.addItem(container, gold);
		test.assertTrue(gold.getItem().getCount() == 2 && container.getItem(0).getCount() == 64 &&
		                    gold.hasAttached(BarterAttribution.OWNER),
		                "Partial hopper transfer failed");
		player.getInventory().clearContent();
		player.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 63));
		gold.setNoPickUpDelay();
		gold.playerTouch(player);
		test.assertTrue(gold.isRemoved() && player.getInventory().getItem(0).getCount() == 64 &&
		                    player.getInventory().getItem(1).getCount() == 1,
		                "Inventory stacking changed");
	}
	static void rewards(GameTestHelper test, ServerPlayer player) {
		long day = ServerDates.today().toEpochDay() + 1;
		long count = QuestRewards.progress(player).completed();
		QuestRewards.complete(player, day, 3, "counter", new QuestReward(0, 0));
		QuestRewards.complete(player, day, 3, "counter", new QuestReward(0, 0));
		test.assertTrue(QuestRewards.progress(player).completed() == count + 1, "Completion was counted twice");
		player.getInventory().clearContent();
		for (int i = 0; i < 36; i++)
			player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
		player.getInventory().setItem(0, new ItemStack(Items.BREAD, 63));
		var bread = JsonParser.parseString("{\"id\":\"minecraft:bread\",\"count\":130}").getAsJsonObject();
		var reward = new QuestReward(5, 1, List.of(bread));
		long before = Smp3AccountStore.balance(player.getUUID(), Smp3Currency.TONGBAO);
		QuestRewards.grant(player, new QuestRewards.DailyReward(day, 0, "bread", reward));
		QuestRewards.grant(player, new QuestRewards.DailyReward(day, 0, "bread", reward));
		ItemDelivery.deliver(player);
		test.assertTrue(Smp3AccountStore.balance(player.getUUID(), Smp3Currency.TONGBAO) == before + 500 &&
		                    ItemDelivery.pending(player).getFirst().getCount() == 129 &&
		                    player.getInventory().getItem(0).getCount() == 64,
		                "Partial reward settlement failed");
		player.getInventory().setItem(1, ItemStack.EMPTY);
		player.getInventory().setItem(2, ItemStack.EMPTY);
		ItemDelivery.deliver(player);
		test.assertTrue(ItemDelivery.pending(player).getFirst().getCount() == 1,
		                "Reward quantities did not split at stack limits");
		player.getInventory().clearContent();
		player.removeAttached(ItemDelivery.APPLIED);
		ItemDelivery.deliver(player);
		test.assertTrue(player.getInventory().getItem(1).getCount() == 64 &&
		                    ItemDelivery.pending(player).getFirst().getCount() == 1,
		                "Inventory checkpoint recovery failed");
		player.getInventory().setItem(3, ItemStack.EMPTY);
		ItemDelivery.deliver(player);
		test.assertTrue(ItemDelivery.pending(player).isEmpty() && player.getInventory().getItem(3).getCount() == 1,
		                "Pending delivery was lost");
		before = Smp3AccountStore.balance(player.getUUID(), Smp3Currency.TONGBAO);
		Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.TONGBAO, Long.MAX_VALUE);
		try {
			QuestRewards.grant(player, new QuestRewards.DailyReward(day, 1, "bread", reward));
			throw new AssertionError("Overflow accepted");
		} catch (ArithmeticException expected) {
		}
		test.assertTrue(!QuestRewards.progress(player).paid(day, 1) && ItemDelivery.pending(player).isEmpty(),
		                "Failed settlement committed part of reward");
		Smp3AccountStore.setBalance(player.getUUID(), Smp3Currency.TONGBAO, before);
		player.getInventory().clearContent();
		var definition = JsonParser.parseString("""
{
  "id": "smp3:random_pack",
  "count": 2,
  "components": {
    "smp3:pack": {
      "draws": 2,
      "options": [
        {
          "name": "minecraft:diamond",
          "weight": 1,
          "grants": [
            {
              "id": "minecraft:diamond",
              "type": "item",
              "count": {
                "min": 2,
                "max": 2
              }
            }
          ]
        }
      ]
    }
  }
}
""").getAsJsonObject();
		var pack = ItemDefinitions.stack(definition, player.registryAccess());
		player.setItemInHand(InteractionHand.MAIN_HAND, pack);
		test.assertTrue(PackItem.open(player, InteractionHand.MAIN_HAND, pack, 0, new Random(1)) ==
		                    PackItem.OpenResult.OPENED,
		                "Pack did not open");
		test.assertTrue(PackItem.open(player, InteractionHand.MAIN_HAND, pack, 0, new Random(2)) ==
		                    PackItem.OpenResult.CHANGED,
		                "Stale callback consumed another pack");
		long sequence = player.getAttachedOrElse(ItemDelivery.APPLIED, 0L);
		player.getInventory().clearContent();
		player.removeAttached(ItemDelivery.APPLIED);
		ItemDelivery.deliver(player);
		test.assertTrue(player.getMainHandItem().getCount() == 1 &&
		                    player.getInventory().getItem(1).is(Items.DIAMOND) &&
		                    player.getInventory().getItem(1).getCount() == 4 &&
		                    player.getAttachedOrElse(ItemDelivery.APPLIED, 0L) == sequence,
		                "Pack recovery redrew, duplicated or lost items");
		var choiceDefinition = DailyQuestModule.current()
		                           .config.tasks()
		                           .stream()
		                           .filter(t -> t.id().equals("craft_planks_a"))
		                           .findFirst()
		                           .orElseThrow()
		                           .rewards()
		                           .grants()
		                           .getFirst()
		                           .deepCopy();
		choiceDefinition.remove("type");
		var choice = ItemDefinitions.stack(choiceDefinition, player.registryAccess());
		player.getInventory().clearContent();
		player.setItemInHand(InteractionHand.MAIN_HAND, choice);
		test.assertTrue(PackItem.open(player, InteractionHand.MAIN_HAND, choice, 1, new Random(1)) ==
		                    PackItem.OpenResult.OPENED,
		                "Choice pack did not open");
		test.assertTrue(player.getInventory().getItem(0).is(Items.OAK_PLANKS) &&
		                    player.getInventory().getItem(0).getCount() == 32 &&
		                    player.getInventory().getItem(1).is(Items.GLASS),
		                "Choice pack delivered the wrong option");
		test.assertTrue(PackItem.open(player, InteractionHand.MAIN_HAND, choice, 0, new Random(1)) ==
		                    PackItem.OpenResult.CHANGED,
		                "Choice pack redeemed twice");
		test.assertTrue(QuestRewards.progress(player).completed() == count + 1 &&
		                    QuestRewards.progress(player).paid(day, 0),
		                "Item delivery overwrote quest receipts");
		var checkpoint = Smp3AccountStore.rewardState(player.getUUID(), ItemDelivery.LEDGER_KEY);
		QuestRewards.complete(player, day, 2, "preserve delivery", new QuestReward(0, 0));
		var updated = Smp3AccountStore.rewardState(player.getUUID(), ItemDelivery.LEDGER_KEY);
		test.assertTrue(checkpoint.get("inventory").equals(updated.get("inventory")) &&
		                    checkpoint.get("sequence").equals(updated.get("sequence")),
		                "Quest completion overwrote the delivery checkpoint");
	}
}
