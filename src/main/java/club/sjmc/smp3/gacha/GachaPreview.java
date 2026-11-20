package club.sjmc.smp3.gacha;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.mojang.math.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import club.sjmc.smp3.item.ItemDefinitions;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import static club.sjmc.smp3.gacha.GachaConfig.*;

/** Server-owned vanilla item display, visible only while a pool is idle and loaded. */
final class GachaPreview {
	private final List<ItemStack> items = new ArrayList<>();
	private Display.ItemDisplay display;
	private int ticks;

	GachaPreview(ServerLevel level, Pool pool) {
		for (Prize prize : pool.rewards.get(Tier.S))
			if (prize.limit != 0) {
				if (prize.displayItem != null) {
					addItem(level, prize.displayItem);
				} else
					for (var grant : prize.grants) {
						if (grant.get("type").getAsString().equals("item")) {
							var item = grant.deepCopy();
							item.remove("type");
							item.addProperty("count", 1);
							addItem(level, item);
						}
					}
			}
		if (items.isEmpty())
			return;
		display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
		display.setPos(pool.displayX(), pool.displayY(1), pool.displayZ());
		display.setItemStack(items.getFirst().copy());
		display.setItemTransform(ItemDisplayContext.FIXED);
		display.setBrightnessOverride(new Brightness(15, 15));
		display.setTransformationInterpolationDuration(2);
		transform();
		if (!level.addFreshEntity(display)) {
			clear();
			throw new IllegalStateException("无法创建 S 级物品展示。");
		}
		display.addTag(GachaAnimation.FX_TAG);
	}
	private void addItem(ServerLevel level, JsonElement json) {
		ItemStack item = ItemDefinitions.stack(json.getAsJsonObject(), level.registryAccess());
		if (item.isEmpty())
			throw new IllegalArgumentException("S 级展示物品不能为空。");
		items.add(item.copyWithCount(1));
	}
	boolean empty() { return items.isEmpty(); }
	boolean removed() { return display == null || display.isRemoved(); }
	void tick() {
		ticks = (ticks + 1) % (240 * items.size());
		if (ticks % 80 == 0)
			display.setItemStack(items.get((ticks / 80) % items.size()).copy());
		if (ticks % 2 == 0)
			transform();
	}
	private void transform() {
		display.setTransformationInterpolationDelay(0);
		display.setTransformation(
		    new Transformation(new Vector3f(), new Quaternionf().rotateY((float)(ticks % 240 * Math.PI * 2 / 240)),
			                   new Vector3f(0.8f), new Quaternionf()));
	}
	void clear() {
		if (display != null)
			display.discard();
	}
}
