package club.sjmc.smp3.daily;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class DailyQuestEvents {
	private DailyQuestEvents() {}
	private static final Set<UUID> inventoryChanges = new HashSet<>();
	static boolean active(ServerPlayer player, String event) {
		var service = DailyQuestModule.current();
		var day = service == null ? null : service.current(player);
		return day != null && day.assignments.stream().anyMatch(a -> !a.complete() && a.tracks(event));
	}
	public static void teleported(ServerPlayer player) { QuestMovement.forget(player.getUUID()); }
	public static void inventoryChanged(ServerPlayer player) { inventoryChanges.add(player.getUUID()); }
	static void forgetInventory(UUID player) { inventoryChanges.remove(player); }
	static void clearInventories() { inventoryChanges.clear(); }
	static void flushInventories(MinecraftServer server) {
		if (inventoryChanges.isEmpty())
			return;
		var changed = Set.copyOf(inventoryChanges);
		inventoryChanges.clear();
		for (UUID id : changed) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null)
				DailyQuestModule.current().inventoryChanged(player);
		}
	}
	static void inventoryLocation(ServerPlayer player, QuestDay day) {
		if (day == null)
			return;
		GlobalPos location = GlobalPos.of(player.level().dimension(), player.blockPosition());
		boolean moved = !location.equals(day.inventoryLocation);
		if (day.assignments.stream().anyMatch(a -> !a.complete() && a.definition.objectives().stream().anyMatch(g -> {
			    return g.event().equals(QuestEventType.INVENTORY_CHANGED.id()) &&
				    (g.hasCondition("structure") || moved && (g.hasCondition("biome") || g.hasCondition("dimension")));
		    }))) {
			day.inventoryLocation = location;
			inventoryChanged(player);
		}
	}

	public static void register() {
		ServerPlayerEvents.AFTER_RESPAWN.register((previous, player, alive) -> inventoryChanged(player));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damage) -> {
			if (damage.getEntity() instanceof ServerPlayer player &&
			    (damage.getDirectEntity() == player || damage.getDirectEntity() instanceof Projectile)) {
				Map<String, Set<String>> facts = context(player, player.level(), entity.blockPosition());
				target(facts, "entity", BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()));
				emit(player, QuestEventType.ENTITY_KILLED.id(), facts);
			}
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer serverPlayer) {
				block(serverPlayer, QuestEventType.BLOCK_BROKEN.id(), pos, state);
			}
		});
	}

	public static void entity(ServerPlayer player, String event, Entity entity) {
		var facts = context(player, player.level(), entity.blockPosition());
		target(facts, "entity", BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()));
		emit(player, event, facts);
	}

	public static void block(ServerPlayer player, String event, BlockPos pos, BlockState state) {
		Map<String, Set<String>> facts = context(player, player.level(), pos);
		blockFacts(facts, state);
		emit(player, event, facts);
	}

	static void blockFacts(Map<String, Set<String>> facts, BlockState state) {
		target(facts, "block", BuiltInRegistries.BLOCK.wrapAsHolder(state.getBlock()));
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
		if (id.contains("_wall_coral_fan"))
			facts.get("block").add(id.replace("_wall_coral_fan", "_coral_fan"));
		for (Property<?> property : state.getProperties())
			facts.put("state." + property.getName(), Set.of(propertyValue(state, property)));
	}

	private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	public static void items(ServerPlayer player, String event, Entity source, Collection<ItemStack> stacks) {
		Map<String, Set<String>> facts = context(player, (ServerLevel)source.level(), source.blockPosition());
		target(facts, "entity", BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(source.getType()));
		if (source instanceof Villager villager)
			target(facts, "profession", villager.getVillagerData().profession());
		for (ItemStack stack : stacks)
			if (!stack.isEmpty()) {
				target(facts, "item", BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()));
				if (event.equals(QuestEventType.FISH_CAUGHT.id()))
					facts.computeIfAbsent("loot", key -> new HashSet<>()).add(fishingLoot(stack));
			}
		if (facts.containsKey("item"))
			emit(player, event, facts);
	}
	private static String fishingLoot(ItemStack stack) {
		if (stack.is(ItemTags.FISHES))
			return "fish";
		return stack.is(Items.NAME_TAG) || stack.is(Items.SADDLE) || stack.is(Items.BOW) ||
		        stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.NAUTILUS_SHELL) ||
		        stack.is(Items.FISHING_ROD) && stack.isEnchanted()
		    ? "treasure"
			: "junk";
	}

	public static void crafted(ServerPlayer player, ItemStack result, int amount, String source) {
		crafted(player, result, amount, source, ItemStack.EMPTY);
	}

	public static void crafted(ServerPlayer player, ItemStack result, int amount, String source, ItemStack template) {
		Map<String, Set<String>> facts = context(player, player.level(), player.blockPosition());
		item(facts, result, source.equals("loom"));
		facts.put("source", Set.of(source));
		if (!template.isEmpty())
			target(facts, "template", BuiltInRegistries.ITEM.wrapAsHolder(template.getItem()));
		emit(player, new QuestDefinition.Event(QuestEventType.ITEM_CRAFTED.id(), facts, amount));
	}

	private static void item(Map<String, Set<String>> facts, ItemStack stack, boolean latestPattern) {
		target(facts, "item", BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()));
		var potion = stack.get(DataComponents.POTION_CONTENTS);
		if (potion != null) {
			potion.potion().ifPresent(p -> target(facts, "potion", p));
			potion.getAllEffects().forEach(e -> target(facts, "effect", e.getEffect()));
		}
		for (var effect :
		     stack.getOrDefault(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY).effects())
			target(facts, "effect", effect.effect());
		var patterns = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers();
		if (latestPattern && !patterns.isEmpty())
			target(facts, "pattern", patterns.getLast().pattern());
		else
			for (var pattern : patterns)
				target(facts, "pattern", pattern.pattern());
	}

	static List<QuestDefinition.Event> inventory(ServerPlayer player) {
		var result = new ArrayList<QuestDefinition.Event>();
		var location = context(player, player.level(), player.blockPosition());
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty())
				continue;
			Map<String, Set<String>> facts = new HashMap<>(location);
			item(facts, stack, false);
			result.add(new QuestDefinition.Event(QuestEventType.INVENTORY_CHANGED.id(), facts, stack.getCount()));
		}
		return result;
	}

	public static void explore(ServerPlayer player) {
		var facts = context(player, player.level(), player.blockPosition());
		emit(player, QuestEventType.BIOME_VISITED.id(), facts);
		if (facts.containsKey("structure"))
			emit(player, QuestEventType.STRUCTURE_VISITED.id(), facts);
	}

	public static Map<String, Set<String>> context(ServerPlayer player, ServerLevel level, BlockPos pos) {
		Map<String, Set<String>> facts = new HashMap<>();
		facts.put("dimension", Set.of(level.dimension().identifier().toString()));
		target(facts, "biome", level.getBiome(pos));
		var service = DailyQuestModule.current();
		QuestDay day = service == null ? null : service.current(player);
		if (day != null && day.assignments.stream().anyMatch(
		                       a
		                       -> !a.complete() && a.definition.objectives().stream().anyMatch(
		                                               g
		                                               -> g.event().equals(QuestEventType.STRUCTURE_VISITED.id()) ||
		                                                      g.hasCondition("structure"))))
			structures(level, pos, facts);
		return facts;
	}

	private static void structures(ServerLevel level, BlockPos pos, Map<String, Set<String>> facts) {
		var chunks = level.getChunkSource();
		var chunk = chunks.getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
		if (chunk == null)
			return;
		var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		chunk.getAllReferences().forEach((structure, references) -> {
			for (long reference : references) {
				// Structure origins may be in unloaded chunks; never load them for a quest check.
				var origin = chunks.getChunkNow(ChunkPos.getX(reference), ChunkPos.getZ(reference));
				if (origin == null)
					continue;
				var start = origin.getStartForStructure(structure);
				if (start != null && start.isValid() && start.getBoundingBox().isInside(pos)) {
					target(facts, "structure", registry.wrapAsHolder(structure));
					break;
				}
			}
		});
	}

	public static void target(Map<String, Set<String>> facts, String key, Holder<?> holder) {
		Set<String> values = facts.computeIfAbsent(key, ignored -> new HashSet<>());
		holder.unwrapKey().ifPresent(id -> values.add(id.identifier().toString()));
		holder.tags().forEach(tag -> values.add("#" + tag.location()));
	}

	public static void emit(ServerPlayer player, String event, Map<String, Set<String>> facts) {
		emit(player, new QuestDefinition.Event(event, facts));
	}

	public static void emit(ServerPlayer player, QuestDefinition.Event event) {
		DailyQuestService service = DailyQuestModule.current();
		if (service != null && player.isAlive() && !player.isCreative() && !player.isSpectator()) {
			service.record(player, event);
		}
	}
}
