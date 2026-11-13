package club.sjmc.smp3.shop;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.economy.EconomyBridge;
import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.inventory.PlayerItems;
import club.sjmc.smp3.perm.Permissions;
import club.sjmc.smp3.time.ServerDates;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class ShopService {
	public static final String ADVANCED_PERMISSION = "smp.shop.advanced";

	public enum Status {
		BOUGHT,
		SOLD,
		LOCKED,
		NO_ECONOMY,
		NO_MONEY,
		QUOTA,
		PAYMENT_FAILED,
		NOT_ENOUGH_ITEMS,
		DEPOSIT_FAILED
	}

	public record Outcome(Status status, int balance, int remaining, boolean dropped) {
		Outcome(Status status) { this(status, 0, 0, false); }
	}

	private ShopService() {}

	public static Outcome buy(ServerPlayer player, ShopEntry entry) {
		LocalDate today = ServerDates.today();
		boolean advanced = Permissions.has(player, ShopService.ADVANCED_PERMISSION, true);

		if (entry.isLockedFor(advanced)) {
			return fail(player, entry, new Outcome(Status.LOCKED), "");
		}

		Integer balance = EconomyBridge.balance(player, Smp3Currency.TONGBAO);
		if (balance == null) {
			return fail(player, entry, new Outcome(Status.NO_ECONOMY), "");
		}
		if (balance < entry.price()) {
			return fail(player, entry, new Outcome(Status.NO_MONEY, balance, 0, false), "");
		}

		int used = QuotaStore.used(player, entry, today);
		if (entry.exhausted(advanced, used)) {
			return fail(player, entry, new Outcome(Status.QUOTA, 0, entry.remainingItems(advanced, used), false), "");
		}

		EconomyBridge.Outcome payment = EconomyBridge.withdraw(player, Smp3Currency.TONGBAO, entry.price());
		if (!payment.success()) {
			return fail(player, entry, new Outcome(Status.PAYMENT_FAILED),
			            payment.message() == null ? "" : payment.message().getString());
		}

		boolean dropped = PlayerItems.give(player, new ItemStack(entry.icon(), entry.amount()));
		QuotaStore.add(player, entry, entry.amount(), today);

		log(player, entry, "SUCCESS", balance, payment.balanceAfter(), dropped ? "dropped" : "");
		return new Outcome(Status.BOUGHT, payment.balanceAfter(), entry.remainingItems(advanced, used + entry.amount()),
		                   dropped);
	}

	public static Outcome sell(ServerPlayer player, ShopEntry entry) {
		LocalDate today = ServerDates.today();
		boolean advanced = Permissions.has(player, ShopService.ADVANCED_PERMISSION, true);

		if (entry.isLockedFor(advanced)) {
			return fail(player, entry, new Outcome(Status.LOCKED), "");
		}

		Inventory inventory = player.getInventory();
		int owned = countInInventory(inventory, entry);
		if (owned < entry.amount()) {
			return fail(player, entry, new Outcome(Status.NOT_ENOUGH_ITEMS), "");
		}

		int used = QuotaStore.used(player, entry, today);
		if (entry.exhausted(advanced, used)) {
			return fail(player, entry, new Outcome(Status.QUOTA, 0, entry.remainingItems(advanced, used), false), "");
		}

		List<ItemStack> removed = removeFromInventory(inventory, entry, entry.amount());

		EconomyBridge.Outcome income = EconomyBridge.deposit(player, Smp3Currency.TONGBAO, entry.price());
		if (!income.success()) {
			removed.forEach(stack -> PlayerItems.give(player, stack.copy()));
			return fail(player, entry, new Outcome(Status.DEPOSIT_FAILED),
			            income.message() == null ? "" : income.message().getString());
		}

		QuotaStore.add(player, entry, entry.amount(), today);

		log(player, entry, "SUCCESS", null, income.balanceAfter(), "");
		return new Outcome(Status.SOLD, income.balanceAfter(), entry.remainingItems(advanced, used + entry.amount()),
		                   false);
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

	private static Outcome fail(ServerPlayer player, ShopEntry entry, Outcome result, String detail) {
		log(player, entry, result.status().name(), null, null, detail);
		return result;
	}

	private static void log(ServerPlayer player, ShopEntry entry, String result, Integer before, Integer after,
	                        String note) {
		Smp3Core.LOGGER.info("[SMP3] player={}({}) entry={} dir={} amount={} price={} balance={}->{} 通宝 result={}{}",
		                     player.getName().getString(), player.getUUID(), entry.id(), entry.direction(),
		                     entry.amount(), entry.price(), before == null ? "-" : before, after == null ? "-" : after,
		                     result, note.isEmpty() ? "" : " (" + note + ")");
	}
}
