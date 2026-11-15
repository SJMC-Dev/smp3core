package club.sjmc.smp3.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;

import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.economy.Smp3Currency;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

// Ledger transactions retain an inventory checkpoint; the player saves its applied sequence with that inventory.
public final class RewardService {
	private static final Gson JSON = new Gson();
	private static final String KEY = "questsAndPacks";
	public static final AttachmentType<Long> APPLIED =
	    AttachmentRegistry.<Long>builder()
	        .persistent(Codec.LONG)
	        .copyOnDeath()
	        .buildAndRegister(Identifier.fromNamespaceAndPath("smp3", "reward_sequence"));
	private RewardService() {}
	public static void register() {}
	private static final class State {
		long completed, completionDay = Long.MIN_VALUE, paidDay = Long.MIN_VALUE, sequence;
		int completionSlots, paidSlots;
		List<JsonObject> pending = new ArrayList<>(), inventory = new ArrayList<>();
		List<DailyReward> ready = new ArrayList<>();
	}
	private record Stored(JsonObject expected, State state) {}
	private static Stored read(ServerPlayer player) {
		JsonObject json = Smp3AccountStore.rewardState(player.getUUID(), KEY);
		State state = json == null ? new State() : JSON.fromJson(json, State.class);
		if (player.getAttachedOrElse(APPLIED, 0L) < state.sequence)
			apply(player, state);
		return new Stored(json, state);
	}
	private static void commit(ServerPlayer player, Stored stored, Map<Smp3Currency, Integer> credits) {
		Smp3AccountStore.commitReward(player.getUUID(), KEY, stored.expected,
		                              JSON.toJsonTree(stored.state).getAsJsonObject(), credits);
	}
	public record Progress(long completed, long completionDay, int completionSlots, long paidDay, int paidSlots) {
		public boolean completed(long day, int slot) {
			return completionDay == day && (completionSlots & 1 << slot) != 0;
		}
		public boolean paid(long day, int slot) {
			return paidDay > day || paidDay == day && (paidSlots & 1 << slot) != 0;
		}
	}
	public static Progress progress(ServerPlayer player) {
		State state = read(player).state;
		return new Progress(state.completed, state.completionDay, state.completionSlots, state.paidDay,
		                    state.paidSlots);
	}
	public static void complete(ServerPlayer player, long day, int slot, String name, Reward reward) {
		Stored stored = read(player);
		State state = stored.state;
		if (day < state.completionDay || day == state.completionDay && (state.completionSlots & 1 << slot) != 0)
			return;
		if (day != state.completionDay) {
			state.completionDay = day;
			state.completionSlots = 0;
		}
		state.completionSlots |= 1 << slot;
		state.completed = Math.incrementExact(state.completed);
		state.ready.add(new DailyReward(day, slot, name, reward));
		commit(player, stored, Map.of());
	}
	public record DailyReward(long day, int slot, String name, Reward reward) {}
	public static List<DailyReward> ready(ServerPlayer player) { return List.copyOf(read(player).state.ready); }
	public static void grant(ServerPlayer player, DailyReward entitlement) {
		long day = entitlement.day;
		int slot = entitlement.slot;
		Reward reward = entitlement.reward;
		Stored stored = read(player);
		State state = stored.state;
		if (state.paidDay > day || state.paidDay == day && (state.paidSlots & 1 << slot) != 0)
			return;
		if (state.paidDay != day) {
			state.paidDay = day;
			state.paidSlots = 0;
		}
		state.paidSlots |= 1 << slot;
		state.pending.addAll(reward.items());
		state.ready.removeIf(entry -> entry.day == day && entry.slot == slot);
		commit(player, stored,
		       Map.of(Smp3Currency.TONGBAO, reward.tongbao(), Smp3Currency.COFFEE_STAMP, reward.coffeeStamp()));
	}
	public static List<ItemStack> pending(ServerPlayer player) {
		return read(player)
		    .state.pending.stream()
		    .map(item -> RewardItems.stack(item, player.registryAccess()))
		    .toList();
	}
	public static boolean open(ServerPlayer player, InteractionHand hand, ItemStack expected, int option,
	                           RandomGenerator random) {
		Stored stored = read(player);
		if (player.getItemInHand(hand) != expected || expected.isEmpty())
			return false;
		RewardItems.validate(RewardItems.json(expected, player.registryAccess()), player.registryAccess());
		List<JsonObject> contents = RewardItems.contents(expected, option, random);
		List<ItemStack> inventory = inventory(player);
		int slot = -1;
		for (int i = 0; i < inventory.size(); i++)
			if (player.getInventory().getItem(i) == expected) {
				slot = i;
				break;
			}
		if (slot < 0)
			return false;
		inventory.get(slot).shrink(1);
		stored.state.pending.addAll(contents);
		insert(player, stored.state, inventory);
		checkpoint(player, stored, inventory);
		return true;
	}
	public static void deliver(ServerPlayer player) {
		Stored stored = read(player);
		if (stored.state.pending.isEmpty())
			return;
		List<ItemStack> inventory = inventory(player);
		if (insert(player, stored.state, inventory))
			checkpoint(player, stored, inventory);
	}
	private static boolean insert(ServerPlayer player, State state, List<ItemStack> inventory) {
		boolean changed = false;
		var iterator = state.pending.listIterator();
		while (iterator.hasNext()) {
			ItemStack remaining = RewardItems.stack(iterator.next(), player.registryAccess());
			int before = remaining.getCount();
			for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
				for (int slot = 0; slot < 36 && !remaining.isEmpty(); slot++) {
					ItemStack target = inventory.get(slot);
					if (pass == 0 ? target.isEmpty() || !ItemStack.isSameItemSameComponents(target, remaining)
					              : !target.isEmpty())
						continue;
					int space = player.getInventory().getMaxStackSize(remaining) - target.getCount();
					int amount = Math.min(Math.max(0, space), remaining.getCount());
					if (amount == 0)
						continue;
					inventory.set(slot, remaining.copyWithCount(target.getCount() + amount));
					remaining.shrink(amount);
				}
			}
			changed |= before != remaining.getCount();
			if (remaining.isEmpty())
				iterator.remove();
			else
				iterator.set(RewardItems.json(remaining, player.registryAccess()));
		}
		return changed;
	}
	private static List<ItemStack> inventory(ServerPlayer player) {
		List<ItemStack> items = new ArrayList<>();
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
			items.add(player.getInventory().getItem(slot).copy());
		return items;
	}
	private static void checkpoint(ServerPlayer player, Stored stored, List<ItemStack> inventory) {
		stored.state.sequence = Math.incrementExact(stored.state.sequence);
		stored.state.inventory =
		    inventory.stream()
		        .map(stack -> stack.isEmpty() ? new JsonObject() : RewardItems.json(stack, player.registryAccess()))
		        .toList();
		commit(player, stored, Map.of());
		apply(player, stored.state);
	}
	private static void apply(ServerPlayer player, State state) {
		List<ItemStack> inventory =
		    state.inventory.stream()
		        .map(item -> item.isEmpty() ? ItemStack.EMPTY : RewardItems.stack(item, player.registryAccess()))
		        .toList();
		for (int slot = 0; slot < inventory.size(); slot++)
			player.getInventory().setItem(slot, inventory.get(slot));
		player.setAttached(APPLIED, state.sequence);
		player.getInventory().setChanged();
		player.containerMenu.broadcastChanges();
	}
}
