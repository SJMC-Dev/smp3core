package club.sjmc.smp3.ui.widget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonObject;

import club.sjmc.smp3.daily.QuestDefinition;
import club.sjmc.smp3.item.ItemDefinitions;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 按物品 id 或 Item 缓存 ItemStack，避免每帧解析注册表。
 */
public final class ItemIcon {
	private static final Map<String, ItemStack> BY_ID = new HashMap<>();
	private static final Map<String, ItemStack> BY_JSON = new HashMap<>();
	private static final Map<Item, ItemStack> BY_ITEM = new HashMap<>();
	private ItemIcon() {}

	public static void clear() {
		BY_ID.clear();
		BY_JSON.clear();
		BY_ITEM.clear();
	}

	public static ItemStack frame(List<ItemStack> icons) {
		var level = Minecraft.getInstance().level;
		long frame = level == null ? 0 : level.getGameTime() / QuestDefinition.ICON_CYCLE_TICKS;
		return icons.get(Math.floorMod(frame, icons.size()));
	}

	public static ItemStack of(String id) {
		return BY_ID.computeIfAbsent(id, key -> {
			try {
				return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(key)));
			} catch (RuntimeException ignored) {
				return new ItemStack(Items.AIR);
			}
		});
	}

	public static ItemStack of(JsonObject item) {
		return BY_JSON.computeIfAbsent(
		    item.toString(),
		    _
		    -> ItemDefinitions.stack(item,
			                         Objects.requireNonNull(Minecraft.getInstance().getConnection()).registryAccess()));
	}

	public static ItemStack item(Item item) { return BY_ITEM.computeIfAbsent(item, ItemStack::new); }
}
