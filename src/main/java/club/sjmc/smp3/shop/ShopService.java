package club.sjmc.smp3.shop;

import java.util.List;
import java.util.Map;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.item.ItemDelivery;
import club.sjmc.smp3.perm.Permissions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class ShopService {
	public static final String ADVANCED_PERMISSION = "smp.shop.advanced";
	public enum Status {
		BOUGHT,
		SOLD,
		STALE,
		LOCKED,
		NO_MONEY,
		QUOTA,
		NOT_ENOUGH_ITEMS,
		INVALID_QUANTITY,
		OWNED,
		PENDING,
		FAILED
	}
	private ShopService() {}

	public static Map<String, Long> usage(ServerPlayer player) {
		return ShopQuota.read(Smp3AccountStore.rewardState(player.getUUID(), ItemDelivery.LEDGER_KEY, "shop")).used();
	}
	public static Status trade(ServerPlayer player, ShopCatalog shown, String id, int batches) {
		if (shown == null || shown != ShopModule.catalog() || !shown.entries().containsKey(id))
			return Status.STALE;
		ShopEntry entry = shown.entries().get(id);
		if (batches < 1 || batches > Integer.MAX_VALUE / Math.max(entry.amount(), entry.price()) ||
		    !entry.title().isEmpty() && batches != 1)
			return Status.INVALID_QUANTITY;
		int amount = entry.amount() * batches, price = entry.price() * batches;
		try {
			if (!entry.title().isEmpty()) {
				if (ShopTitles.pending(player, entry.title()))
					return Status.PENDING;
				if (Permissions.has(player, ShopTitles.permission(entry.title()), true))
					return Status.OWNED;
			}
			boolean advanced = Permissions.has(player, ADVANCED_PERMISSION, true);
			if (entry.limitFor(advanced) == 0)
				return Status.LOCKED;
			ShopQuota quota =
			    ShopQuota.read(Smp3AccountStore.rewardState(player.getUUID(), ItemDelivery.LEDGER_KEY, "shop"));
			long used = quota.usage(entry).count();
			if (entry.limitFor(advanced) != -1 && entry.remainingItems(advanced, used) < amount)
				return Status.QUOTA;
			boolean buy = entry.direction() == TradeDirection.BUY;
			if (buy && Smp3AccountStore.balance(player.getUUID(), entry.currency()) < Smp3Currency.rawUnits(price))
				return Status.NO_MONEY;
			var delivery = entry.delivery() == null ? null : entry.delivery().deepCopy();
			if (delivery != null)
				delivery.addProperty("count", amount);
			boolean completed = ItemDelivery.transact(player,
			                                          inventory
			                                          -> buy || remove(inventory, entry, amount),
			                                          delivery != null ? List.of(delivery) : List.of(), state -> {
				                                          quota.record(state, entry, amount);
				                                          if (!entry.title().isEmpty())
					                                          ShopTitles.queue(state, entry.title());
			                                          }, Map.of(entry.currency(), buy ? -price : price));
			if (!completed)
				return Status.NOT_ENOUGH_ITEMS;
			Smp3Core.LOGGER.info("Shop trade player={} entry={} direction={} amount={} price={} currency={}",
			                     player.getUUID(), id, entry.direction(), amount, price, entry.currency().id());
			if (!entry.title().isEmpty()) {
				ShopTitles.deliver(player);
				return Status.PENDING;
			}
			return buy ? Status.BOUGHT : Status.SOLD;
		} catch (RuntimeException error) {
			Smp3Core.LOGGER.error("Shop trade failed player={} entry={}", player.getUUID(), id, error);
			return Status.FAILED;
		}
	}
	public record
	    Quote(long owned, long used, int limit, int maximum, long balance, boolean weekly, boolean pendingTitle) {}
	public static Quote quote(ServerPlayer player, ShopEntry entry) {
		long owned = 0;
		boolean pendingTitle = false;
		if (entry.title().isEmpty()) {
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				ItemStack stack = player.getInventory().getItem(slot);
				boolean matches = entry.direction() == TradeDirection.SELL
				                      ? entry.matches(stack)
				                      : ItemStack.isSameItemSameComponents(stack, entry.template());
				if (matches)
					owned += stack.getCount();
			}
		} else {
			pendingTitle = ShopTitles.pending(player, entry.title());
			owned = Permissions.has(player, ShopTitles.permission(entry.title()), false) ? 1 : 0;
		}
		var quota = ShopQuota.read(Smp3AccountStore.rewardState(player.getUUID(), ItemDelivery.LEDGER_KEY, "shop"))
		                .usage(entry);
		long used = quota.count();
		int limit = entry.limitFor(Permissions.has(player, ADVANCED_PERMISSION, false));
		long balance = Smp3AccountStore.balance(player.getUUID(), entry.currency());
		long maximum = Integer.MAX_VALUE / Math.max(entry.amount(), entry.price());
		maximum =
		    Math.min(maximum, entry.direction() == TradeDirection.BUY ? balance / Smp3Currency.rawUnits(entry.price())
			                                                          : owned / entry.amount());
		if (limit != -1)
			maximum = Math.min(maximum, Math.max(0, limit - used) / entry.amount());
		if (!entry.title().isEmpty())
			maximum = owned > 0 || pendingTitle ? 0 : Math.min(maximum, 1);
		return new Quote(owned, used, limit, (int)maximum, balance, quota.weekly(), pendingTitle);
	}

	private static boolean remove(List<ItemStack> inventory, ShopEntry entry, int remaining) {
		for (ItemStack stack : inventory) {
			if (!entry.matches(stack))
				continue;
			int take = Math.min(remaining, stack.getCount());
			stack.shrink(take);
			remaining -= take;
			if (remaining == 0)
				return true;
		}
		return false;
	}
}
