package club.sjmc.smp3.daily;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class DailyQuestEvents {
	private DailyQuestEvents() {}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damage) -> {
			if (damage.getEntity() instanceof ServerPlayer player &&
			    (damage.getDirectEntity() == player || damage.getDirectEntity() instanceof Projectile)) {
				Map<String, Set<String>> facts = context(player.level(), entity.blockPosition());
				target(facts, "entity", BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()));
				emit(player, QuestEventType.ENTITY_KILLED.id(), facts);
			}
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer serverPlayer) {
				block(serverPlayer, QuestEventType.BLOCK_BROKEN.id(), pos, state);
				if (harvestable(state))
					block(serverPlayer, QuestEventType.CROP_HARVESTED.id(), pos, state);
			}
		});
	}

	public static boolean harvestable(BlockState state) {
		return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) || state.is(Blocks.PUMPKIN) ||
		    state.is(Blocks.MELON);
	}

	public static void block(ServerPlayer player, String event, BlockPos pos, BlockState state) {
		Map<String, Set<String>> facts = context(player.level(), pos);
		target(facts, "block", BuiltInRegistries.BLOCK.wrapAsHolder(state.getBlock()));
		for (Property<?> property : state.getProperties())
			facts.put("state." + property.getName(), Set.of(propertyValue(state, property)));
		emit(player, event, facts);
	}

	private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	public static void items(ServerPlayer player, String event, Entity source, Collection<ItemStack> stacks) {
		Map<String, Set<String>> facts = context((ServerLevel)source.level(), source.blockPosition());
		target(facts, "entity", BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(source.getType()));
		for (ItemStack stack : stacks)
			if (!stack.isEmpty())
				target(facts, "item", BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()));
		if (facts.containsKey("item"))
			emit(player, event, facts);
	}

	public static void crafted(ServerPlayer player, ItemStack result, int amount) {
		Map<String, Set<String>> facts = context(player.level(), player.blockPosition());
		target(facts, "item", BuiltInRegistries.ITEM.wrapAsHolder(result.getItem()));
		emit(player, new QuestDefinition.Event(QuestEventType.ITEM_CRAFTED.id(), facts, amount));
	}

	static List<QuestDefinition.Event> inventory(ServerPlayer player) {
		var result = new ArrayList<QuestDefinition.Event>();
		var location = context(player.level(), player.blockPosition());
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty())
				continue;
			Map<String, Set<String>> facts = new HashMap<>(location);
			target(facts, "item", BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()));
			result.add(new QuestDefinition.Event(QuestEventType.INVENTORY_CHANGED.id(), facts, stack.getCount()));
		}
		return result;
	}

	public static void explore(ServerPlayer player) {
		emit(player, QuestEventType.BIOME_VISITED.id(), context(player.level(), player.blockPosition()));
	}

	private static Map<String, Set<String>> context(ServerLevel level, BlockPos pos) {
		Map<String, Set<String>> facts = new HashMap<>();
		facts.put("dimension", Set.of(level.dimension().identifier().toString()));
		target(facts, "biome", level.getBiome(pos));
		return facts;
	}

	private static void target(Map<String, Set<String>> facts, String key, Holder<?> holder) {
		Set<String> values = facts.computeIfAbsent(key, ignored -> new HashSet<>());
		holder.unwrapKey().ifPresent(id -> values.add(id.identifier().toString()));
		holder.tags().forEach(tag -> values.add("#" + tag.location()));
	}

	private static void emit(ServerPlayer player, String event, Map<String, Set<String>> facts) {
		emit(player, new QuestDefinition.Event(event, facts));
	}

	private static void emit(ServerPlayer player, QuestDefinition.Event event) {
		DailyQuestService service = DailyQuestModule.current();
		if (service != null && player.isAlive() && !player.isCreative() && !player.isSpectator()) {
			service.record(player, event);
		}
	}
}
