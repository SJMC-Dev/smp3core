package club.sjmc.smp3.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.economy.Smp3Currency;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

// Ledger transactions retain an inventory checkpoint; the player saves its applied sequence with that inventory.
public final class ItemDelivery {
	private static final Gson JSON = new Gson();
	public static final String LEDGER_KEY = "questsAndPacks";
	public static final AttachmentType<Long> APPLIED =
	    AttachmentRegistry.<Long>builder()
	        .persistent(Codec.LONG)
	        .copyOnDeath()
	        .buildAndRegister(Identifier.fromNamespaceAndPath("smp3", "reward_sequence"));
	private ItemDelivery() {}
	private static final Map<UUID, Long> retries = new HashMap<>();
	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> automatic(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> retries.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> retries.clear());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 5 == 0)
				server.getPlayerList().getPlayers().forEach(ItemDelivery::automatic);
		});
	}
	private static void automatic(ServerPlayer player) {
		long now = player.level().getGameTime();
		if (now < retries.getOrDefault(player.getUUID(), 0L))
			return;
		try {
			deliver(player);
			retries.remove(player.getUUID());
		} catch (RuntimeException error) {
			if (retries.put(player.getUUID(), now + 100) == null) {
				Smp3Core.LOGGER.error("Item delivery failed; retrying player={}", player.getUUID(), error);
				player.sendSystemMessage(Component.literal("物品暂未到账，将自动重试"));
			}
		}
	}
	private static final class State {
		long sequence;
		List<JsonObject> pending = new ArrayList<>(), inventory = new ArrayList<>();
	}
	private record Stored(JsonObject expected, State state) {}
	private static Stored read(ServerPlayer player) {
		JsonObject json = Smp3AccountStore.rewardState(player.getUUID(), LEDGER_KEY);
		State state = json == null ? new State() : JSON.fromJson(json, State.class);
		if (player.getAttachedOrElse(APPLIED, 0L) < state.sequence)
			apply(player, state);
		return new Stored(json, state);
	}
	private static State pendingState(ServerPlayer player) {
		JsonObject json = Smp3AccountStore.rewardState(player.getUUID(), LEDGER_KEY, "sequence", "pending");
		State state = json == null ? new State() : JSON.fromJson(json, State.class);
		return player.getAttachedOrElse(APPLIED, 0L) < state.sequence ? read(player).state : state;
	}

	private static void commit(ServerPlayer player, Stored stored, Consumer<JsonObject> update,
	                           Map<Smp3Currency, Integer> changes) {
		JsonObject next = stored.expected == null ? new JsonObject() : stored.expected.deepCopy();
		JSON.toJsonTree(stored.state).getAsJsonObject().entrySet().forEach(e -> next.add(e.getKey(), e.getValue()));
		update.accept(next);
		Smp3AccountStore.commitChange(player.getUUID(), LEDGER_KEY, stored.expected, next, changes);
	}

	// Quest settlement appends items in the same ledger commit as its currency and receipt.
	public static void queue(JsonObject state, List<JsonObject> items) {
		var pending = state.has("pending") ? state.getAsJsonArray("pending") : new JsonArray();
		items.forEach(item -> pending.add(item.deepCopy()));
		state.add("pending", pending);
	}

	public static boolean give(ServerPlayer player, ItemStack stack) {
		if (player.getInventory().add(stack) || stack.isEmpty())
			return false;
		player.spawnAtLocation(player.level(), stack);
		return true;
	}

	public static List<ItemStack> pending(ServerPlayer player) {
		return pendingState(player)
		    .pending.stream()
		    .map(item -> ItemDefinitions.stack(item, player.registryAccess()))
		    .toList();
	}
	static boolean exchange(ServerPlayer player, InteractionHand hand, ItemStack expected,
	                        Supplier<List<JsonObject>> contents) {
		Stored stored = read(player);
		if (player.getItemInHand(hand) != expected || expected.isEmpty())
			return false;
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
		stored.state.pending.addAll(contents.get());
		insert(player, stored.state, inventory);
		checkpoint(player, stored, inventory);
		return true;
	}
	public static boolean transact(ServerPlayer player, Predicate<List<ItemStack>> inventoryChange,
	                               List<JsonObject> received, Consumer<JsonObject> update,
	                               Map<Smp3Currency, Integer> changes) {
		Stored stored = read(player);
		List<ItemStack> inventory = inventory(player);
		if (!inventoryChange.test(inventory))
			return false;
		stored.state.pending.addAll(received);
		insert(player, stored.state, inventory);
		checkpoint(player, stored, inventory, update, changes);
		return true;
	}

	public static void deliver(ServerPlayer player) {
		if (pendingState(player).pending.isEmpty())
			return;
		Stored stored = read(player);
		List<ItemStack> inventory = inventory(player);
		if (insert(player, stored.state, inventory))
			checkpoint(player, stored, inventory);
	}
	private static boolean insert(ServerPlayer player, State state, List<ItemStack> inventory) {
		boolean changed = false;
		var iterator = state.pending.listIterator();
		while (iterator.hasNext()) {
			ItemStack remaining = ItemDefinitions.stack(iterator.next(), player.registryAccess());
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
				iterator.set(ItemDefinitions.json(remaining, player.registryAccess()));
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
		checkpoint(player, stored, inventory, state -> {}, Map.of());
	}
	private static void checkpoint(ServerPlayer player, Stored stored, List<ItemStack> inventory,
	                               Consumer<JsonObject> update, Map<Smp3Currency, Integer> changes) {
		stored.state.sequence = Math.incrementExact(stored.state.sequence);
		stored.state.inventory =
		    inventory.stream()
		        .map(stack -> stack.isEmpty() ? new JsonObject() : ItemDefinitions.json(stack, player.registryAccess()))
		        .toList();
		commit(player, stored, update, changes);
		apply(player, stored.state);
	}
	private static void apply(ServerPlayer player, State state) {
		List<ItemStack> inventory =
		    state.inventory.stream()
		        .map(item -> item.isEmpty() ? ItemStack.EMPTY : ItemDefinitions.stack(item, player.registryAccess()))
		        .toList();
		for (int slot = 0; slot < inventory.size(); slot++)
			player.getInventory().setItem(slot, inventory.get(slot));
		player.setAttached(APPLIED, state.sequence);
		player.getInventory().setChanged();
		player.containerMenu.broadcastChanges();
	}
}
