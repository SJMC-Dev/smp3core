package club.sjmc.smp3.shop;

import java.util.ArrayList;
import java.util.List;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.economy.EconomyBridge;
import club.sjmc.smp3.perm.IdentityService;
import club.sjmc.smp3.quota.QuotaStore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 买入与回收的交易流程。
 *
 * <p>所有逻辑都在服务器主线程执行，GUI 点击天然串行。顺序严格按需求文档 §7：</p>
 * <ul>
 *   <li>买入：校验 → 扣款 → 发货 → 记账 → 反馈</li>
 *   <li>回收：校验 → 扣物品 → 入账 → 记账 → 反馈</li>
 * </ul>
 */
public final class ShopService {

	private ShopService() {
	}

	/** 买入（建筑材料商店）。 */
	public static void buy(ServerPlayer player, ShopEntry entry) {
		boolean advanced = IdentityService.isAdvanced(player);

		if (entry.isLockedFor(advanced)) {
			fail(player, entry, "LOCKED", Messages.locked());
			return;
		}

		Integer balance = EconomyBridge.balance(player);
		if (balance == null) {
			fail(player, entry, "NO_ECONOMY", Messages.noEconomy());
			return;
		}
		if (balance < entry.price()) {
			fail(player, entry, "NO_MONEY", Messages.notEnoughMoney(entry.price(), balance));
			return;
		}

		int used = QuotaStore.used(player, entry);
		if (entry.exhausted(advanced, used)) {
			fail(player, entry, "QUOTA", Messages.quotaExhausted(entry, advanced, used));
			return;
		}

		EconomyBridge.Outcome payment = EconomyBridge.withdraw(player, entry.price());
		if (!payment.success()) {
			fail(player, entry, "PAYMENT_FAILED", Messages.paymentFailed(), payment.message());
			return;
		}

		boolean dropped = deliver(player, entry);
		QuotaStore.add(player, entry, entry.amount());

		player.sendSystemMessage(Messages.bought(player, entry, advanced));
		if (dropped) {
			player.sendSystemMessage(Messages.droppedToGround());
		}
		log(player, entry, "SUCCESS", balance, payment.balanceAfter(), dropped ? "dropped" : "");
	}

	/** 回收（回收商店）。 */
	public static void sell(ServerPlayer player, ShopEntry entry) {
		boolean advanced = IdentityService.isAdvanced(player);

		if (entry.isLockedFor(advanced)) {
			fail(player, entry, "LOCKED", Messages.locked());
			return;
		}

		Inventory inventory = player.getInventory();
		int owned = countInInventory(inventory, entry);
		if (owned < entry.amount()) {
			fail(player, entry, "NOT_ENOUGH_ITEMS", Messages.notEnoughItems(entry));
			return;
		}

		int used = QuotaStore.used(player, entry);
		if (entry.exhausted(advanced, used)) {
			fail(player, entry, "QUOTA", Messages.quotaExhausted(entry, advanced, used));
			return;
		}

		List<ItemStack> removed = removeFromInventory(inventory, entry, entry.amount());

		EconomyBridge.Outcome income = EconomyBridge.deposit(player, entry.price());
		if (!income.success()) {
			// 入账失败：把扣掉的物品原样还回去，避免物品凭空消失
			refundItems(player, removed);
			fail(player, entry, "DEPOSIT_FAILED", Messages.depositFailed(), income.message());
			return;
		}

		QuotaStore.add(player, entry, entry.amount());

		player.sendSystemMessage(Messages.sold(player, entry, advanced));
		log(player, entry, "SUCCESS", null, income.balanceAfter(), "");
	}

	/**
	 * 发货：优先插入背包，放不下的部分掉在玩家脚下。
	 *
	 * @return 是否有物品掉落到地上
	 */
	private static boolean deliver(ServerPlayer player, ShopEntry entry) {
		ItemStack stack = new ItemStack(entry.icon(), entry.amount());
		boolean fullyAdded = player.getInventory().add(stack);
		if (fullyAdded || stack.isEmpty()) {
			return false;
		}
		player.spawnAtLocation(player.level(), stack);
		return true;
	}

	private static int countInInventory(Inventory inventory, ShopEntry entry) {
		int total = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && entry.matches(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** 从背包扣除指定数量的物品，返回实际扣掉的堆（用于失败退款）。 */
	private static List<ItemStack> removeFromInventory(Inventory inventory, ShopEntry entry, int amount) {
		List<ItemStack> removed = new ArrayList<>();
		int remaining = amount;
		for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || !entry.matches(stack)) {
				continue;
			}
			int take = Math.min(remaining, stack.getCount());
			removed.add(stack.copyWithCount(take));
			stack.shrink(take);
			remaining -= take;
			if (stack.isEmpty()) {
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}
		inventory.setChanged();
		return removed;
	}

	private static void refundItems(ServerPlayer player, List<ItemStack> removed) {
		for (ItemStack stack : removed) {
			ItemStack copy = stack.copy();
			if (!player.getInventory().add(copy) && !copy.isEmpty()) {
				player.spawnAtLocation(player.level(), copy);
			}
		}
	}

	private static void fail(ServerPlayer player, ShopEntry entry, String result, Component message) {
		fail(player, entry, result, message, null);
	}

	private static void fail(ServerPlayer player, ShopEntry entry, String result, Component message,
			Component detail) {
		player.sendSystemMessage(message);
		log(player, entry, result, null, null, detail == null ? "" : detail.getString());
	}

	/** 每次交易输出一行控制台日志。 */
	private static void log(ServerPlayer player, ShopEntry entry, String result, Integer before, Integer after,
			String note) {
		Smp3Core.LOGGER.info(
				"[SMP3] player={}({}) entry={} dir={} amount={} price={} balance={}->{} 通宝 result={}{}",
				player.getName().getString(), player.getUUID(), entry.id(), entry.direction(),
				entry.amount(), entry.price(),
				before == null ? "-" : before, after == null ? "-" : after,
				result, note.isEmpty() ? "" : " (" + note + ")");
	}
}
