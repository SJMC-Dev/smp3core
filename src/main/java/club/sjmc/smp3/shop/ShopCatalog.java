package club.sjmc.smp3.shop;

import java.util.ArrayList;
import java.util.List;

import club.sjmc.smp3.Smp3Core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class ShopCatalog {

	private static final int STACK = 64;
	private static final int UNLIMITED = ShopEntry.UNLIMITED;

	private static List<ShopSheet> sheets = List.of();

	private ShopCatalog() {}

	public static void init() {
		if (!sheets.isEmpty()) {
			return;
		}

		int[] skipped = {0};
		CatBuilder ore = new CatBuilder("ore", "矿石", "recycle", TradeDirection.SELL, skipped)
		                     .sell("coal_ore", 1, 1, 64)
		                     .sell("deepslate_coal_ore", 1, 100, 1)
		                     .sell("iron_ore", 1, 1, 64)
		                     .sell("deepslate_iron_ore", 1, 1, 64)
		                     .sell("copper_ore", 1, 1, 64)
		                     .sell("deepslate_copper_ore", 1, 2, 64)
		                     .sell("gold_ore", 1, 2, 64)
		                     .sell("deepslate_gold_ore", 1, 2, 64)
		                     .sell("redstone_ore", 1, 4, 16)
		                     .sell("deepslate_redstone_ore", 1, 2, 64)
		                     .sell("lapis_ore", 1, 4, 16)
		                     .sell("deepslate_lapis_ore", 1, 4, 16)
		                     .sell("emerald_ore", 1, 100, 1)
		                     .sell("deepslate_emerald_ore", 1, 1000, 1)
		                     .sell("diamond", 1, 4, 16)
		                     .sell("diamond_ore", 1, 100, 1)
		                     .sell("deepslate_diamond_ore", 1, 10, 16)
		                     .sell("ancient_debris", 1, 50, 4)
		                     .sell("netherite_scrap", 1, 50, 4)
		                     .sell("netherite_ingot", 1, 200, 1);

		CatBuilder block = new CatBuilder("block", "自然方块", "recycle", TradeDirection.SELL, skipped)
		                       .sell("deepslate", STACK, 2, UNLIMITED)
		                       .sell("cobbled_deepslate", STACK, 2, UNLIMITED)
		                       .sell("granite", STACK, 1, UNLIMITED)
		                       .sell("andesite", STACK, 1, UNLIMITED)
		                       .sell("diorite", STACK, 1, UNLIMITED)
		                       .sell("tuff", STACK, 1, UNLIMITED)
		                       .sell("calcite", STACK, 4, UNLIMITED)
		                       .sell("netherrack", STACK, 1, UNLIMITED)
		                       .sell("soul_soil", STACK, 1, UNLIMITED)
		                       .sell("end_stone", STACK, 2, UNLIMITED)
		                       .sell("spore_blossom", 1, 2, 64)
		                       .sell("dead_bush", 1, 2, 64)
		                       .sell("cobweb", 1, 2, 64);

		CatBuilder explore =
		    new CatBuilder("explore", "探索资源", "recycle", TradeDirection.SELL, skipped)
		        .sell("echo_shard", 1, 100, 1)
		        .sell("disc_fragment_5", 1, 100, 1)
		        .sell("enchanted_golden_apple", 1, 500, 1)
		        .sell("heart_of_the_sea", 1, 100, 1)
		        .sell("sponge", 1, 5, 16)
		        .tag("sherds", "任意陶片", Items.PRIZE_POTTERY_SHERD, ItemTags.DECORATED_POT_SHERDS, 1, 25, 4)
		        .sell("iron_horse_armor", 1, 100, 1)
		        .sell("copper_horse_armor", 1, 100, 1)
		        .sell("golden_horse_armor", 1, 100, 1)
		        .sell("diamond_horse_armor", 1, 100, 1);

		ShopSheet recycle = new ShopSheet("回收商店", List.of(ore.build(), block.build(), explore.build()));

		sheets = List.of(recycle);

		int entryCount = sheets.stream().mapToInt(ShopSheet::entryCount).sum();
		Smp3Core.LOGGER.info("[SMP3] 已加载 {} 个商店、{} 个条目（跳过 {} 个无法解析的物品）", sheets.size(),
		                     entryCount, skipped[0]);
	}

	public static List<ShopSheet> sheets() { return sheets; }

	private static final class CatBuilder {
		private final String id;
		private final String name;
		private final String sheetId;
		private final TradeDirection direction;
		private final int[] skipped;
		private final List<ShopEntry> entries = new ArrayList<>();

		private CatBuilder(String id, String name, String sheetId, TradeDirection direction, int[] skipped) {
			this.id = id;
			this.name = name;
			this.sheetId = sheetId;
			this.direction = direction;
			this.skipped = skipped;
		}

		private CatBuilder item(String itemId, int amount, int price, int normalLimit, int advancedLimit) {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(itemId));
			if (item == Items.AIR) {
				skipped[0]++;
				Smp3Core.LOGGER.error("[SMP3] 条目物品不存在，已跳过：{} / {}", itemId, id);
				return this;
			}
			entries.add(new ShopEntry(sheetId + "." + id + "." + itemId, direction, null, item, item, null, amount,
			                          price, normalLimit, advancedLimit));
			return this;
		}

		private CatBuilder sell(String itemId, int amount, int price, int limit) {
			return item(itemId, amount, price, limit, limit);
		}

		private CatBuilder tag(String tagId, String displayName, Item icon, TagKey<Item> tag, int amount, int price,
		                       int limit) {
			entries.add(new ShopEntry(sheetId + "." + id + "." + tagId, direction, displayName, icon, null, tag, amount,
			                          price, limit, limit));
			return this;
		}

		private ShopCategory build() { return new ShopCategory(name, entries); }
	}
}
