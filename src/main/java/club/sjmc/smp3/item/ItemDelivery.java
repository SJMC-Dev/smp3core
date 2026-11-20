package club.sjmc.smp3.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
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
	private static final Map<UUID, Delivery> deliveries = new HashMap<>();
	private static int effectTick = -1, effectsLeft;
	private static final int EFFECTS_PER_TICK = 8, EFFECTS_PER_PASS = 4;
	private static final class Delivery {
		long after;
		int cursor;
		boolean failed, deferred;
	}
	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> request(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register(
		    (handler, server) -> deliveries.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			deliveries.clear();
			effectTick = -1;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (var player : server.getPlayerList().getPlayers())
				if (server.getTickCount() % 5 == Math.floorMod(player.getUUID().hashCode(), 5))
					automatic(player);
		});
	}
	public static void request(ServerPlayer player) {
		deliveries.computeIfAbsent(player.getUUID(), ignored -> new Delivery()).after = player.level().getGameTime();
	}
	public static void inventoryChanged(ServerPlayer player) {
		var delivery = deliveries.get(player.getUUID());
		if (delivery != null && !delivery.failed)
			delivery.after = player.level().getGameTime();
	}
	private static void automatic(ServerPlayer player) {
		if (!player.isAlive())
			return;
		var delivery = deliveries.get(player.getUUID());
		if (delivery == null || player.level().getGameTime() < delivery.after)
			return;
		try {
			deliver(player);
			delivery.failed = false;
		} catch (RuntimeException error) {
			delivery.after = player.level().getGameTime() + 100;
			if (!delivery.failed) {
				delivery.failed = true;
				Smp3Core.LOGGER.error("Item delivery failed; retrying player={}", player.getUUID(), error);
				player.sendSystemMessage(Component.literal("物品暂未到账，将自动重试"));
			}
		}
	}
	private static final class State {
		long sequence;
		List<JsonObject> pending = new ArrayList<>(), inventory = new ArrayList<>(), effects = new ArrayList<>();
	}
	private record Stored(JsonObject expected, State state) {}
	private static Stored read(ServerPlayer player) {
		JsonObject json = Smp3AccountStore.rewardState(player.getUUID(), LEDGER_KEY);
		State state = json == null ? new State() : JSON.fromJson(json, State.class);
		state.effects.replaceAll(JsonObject::deepCopy);
		if (player.getAttachedOrElse(APPLIED, 0L) < state.sequence)
			apply(player, state);
		return new Stored(json, state);
	}
	private static State pendingState(ServerPlayer player) {
		JsonObject json = Smp3AccountStore.rewardState(player.getUUID(), LEDGER_KEY, "sequence", "pending", "effects");
		State state = json == null ? new State() : JSON.fromJson(json, State.class);
		return player.getAttachedOrElse(APPLIED, 0L) < state.sequence ? read(player).state : state;
	}

	private static void commit(ServerPlayer player, Stored stored, Consumer<JsonObject> update,
	                           Map<Smp3Currency, Integer> changes) {
		JsonObject next = stored.expected == null ? new JsonObject() : stored.expected.deepCopy();
		JSON.toJsonTree(stored.state).getAsJsonObject().entrySet().forEach(e -> next.add(e.getKey(), e.getValue()));
		update.accept(next);
		Smp3AccountStore.commitChange(player.getUUID(), LEDGER_KEY, stored.expected, next, changes);
		request(player);
	}

	// Quest settlement appends items in the same ledger commit as its currency and receipt.
	public static void queue(JsonObject state, List<JsonObject> items) {
		var pending = state.has("pending") ? state.getAsJsonArray("pending") : new JsonArray();
		items.forEach(item -> pending.add(item.deepCopy()));
		state.add("pending", pending);
	}

	public static void queueEffects(JsonObject state, List<JsonObject> effects) {
		var pending = state.has("effects") ? state.getAsJsonArray("effects") : new JsonArray();
		effects.forEach(effect -> pending.add(effect.deepCopy()));
		state.add("effects", pending);
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
	public static List<JsonObject> pendingEffects(ServerPlayer player) {
		return List.copyOf(pendingState(player).effects);
	}

	static PackItem.OpenResult exchange(ServerPlayer player, InteractionHand hand, ItemStack expected,
	                                    Supplier<RewardBundle> contents) {
		Stored stored = read(player);
		if (player.getItemInHand(hand) != expected || expected.isEmpty())
			return PackItem.OpenResult.CHANGED;
		List<ItemStack> inventory = inventory(player);
		int slot = -1;
		for (int i = 0; i < inventory.size(); i++)
			if (player.getInventory().getItem(i) == expected) {
				slot = i;
				break;
			}
		if (slot < 0)
			return PackItem.OpenResult.CHANGED;
		inventory.get(slot).shrink(1);
		var bundle = contents.get();
		if (bundle.items().isEmpty() && bundle.effects().isEmpty() && bundle.tongbao() == 0 &&
		    bundle.coffeeStamp() == 0)
			return PackItem.OpenResult.EMPTY;
		stored.state.pending.addAll(bundle.items());
		stored.state.effects.addAll(bundle.effects());
		insert(player, stored.state, inventory);
		checkpoint(player, stored, inventory, next -> {}, bundle.credits());
		return PackItem.OpenResult.OPENED;
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
		var pending = pendingState(player);
		if (pending.effects.isEmpty() && pending.pending.isEmpty()) {
			deliveries.remove(player.getUUID());
			return;
		}
		var delivery = deliveries.computeIfAbsent(player.getUUID(), ignored -> new Delivery());
		delivery.deferred = false;
		Stored stored = read(player);
		if (!stored.state.effects.isEmpty())
			stored = deliverEffects(player, stored, delivery);
		if (!stored.state.pending.isEmpty()) {
			List<ItemStack> inventory = inventory(player);
			if (insert(player, stored.state, inventory))
				checkpoint(player, stored, inventory);
		}
		if (stored.state.pending.isEmpty() && stored.state.effects.isEmpty())
			deliveries.remove(player.getUUID());
		else
			delivery.after = player.level().getGameTime() + (delivery.deferred ? 5 : 100);
	}
	private static Stored deliverEffects(ServerPlayer player, Stored stored, Delivery delivery) {
		int tick = player.level().getServer().getTickCount();
		if (effectTick != tick) {
			effectTick = tick;
			effectsLeft = EFFECTS_PER_TICK;
		}
		int index = Math.min(delivery.cursor, stored.state.effects.size() - 1);
		int attempts = 0;
		for (; index < stored.state.effects.size(); attempts++) {
			if (attempts == EFFECTS_PER_PASS || effectsLeft == 0) {
				delivery.deferred = true;
				break;
			}
			effectsLeft--;
			var effect = stored.state.effects.get(index);
			if (effect.get("type").getAsString().equals("equipment")) {
				var equipment = EquipmentSlot.valueOf(effect.get("slot").getAsString().toUpperCase(Locale.ROOT));
				int slot = Inventory.EQUIPMENT_SLOT_MAPPING.int2ObjectEntrySet()
				               .stream()
				               .filter(e -> e.getValue() == equipment)
				               .findFirst()
				               .orElseThrow()
				               .getIntKey();
				var inventory = inventory(player);
				var old = inventory.get(slot);
				if (!old.isEmpty())
					stored.state.pending.add(ItemDefinitions.json(old, player.registryAccess()));
				inventory.set(slot, ItemDefinitions.stack(effect.getAsJsonObject("item"), player.registryAccess()));
				stored.state.effects.remove(index);
				insert(player, stored.state, inventory);
				checkpoint(player, stored, inventory);
			} else {
				var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(effect.get("id").getAsString()));
				if (!effect.has("uuid")) {
					var position = RewardEffects.position(player, type);
					if (position == null) {
						index++;
						continue;
					}
					effect.addProperty("uuid", UUID.randomUUID().toString());
					effect.addProperty("dimension", player.level().dimension().identifier().toString());
					effect.addProperty("position", position.asLong());
					commit(player, stored, next -> {}, Map.of());
					stored = read(player);
					effect = stored.state.effects.get(index);
				}
				if (!RewardEffects.spawn(player, type, effect)) {
					index++;
					continue;
				}
				int left = RewardBundle.number(effect, "count", 1) - 1;
				if (left == 0)
					stored.state.effects.remove(index);
				else {
					effect.addProperty("count", left);
					effect.remove("uuid");
					effect.remove("dimension");
					effect.remove("position");
				}
				commit(player, stored, next -> {}, Map.of());
			}
			stored = read(player);
		}
		delivery.cursor = index < stored.state.effects.size() ? index : 0;
		return stored;
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
