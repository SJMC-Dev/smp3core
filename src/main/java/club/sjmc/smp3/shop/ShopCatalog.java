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

/**
 * 全部商店条目数据（硬编码，不读取任何配置文件）。
 *
 * <p>数值来源：{@code 【数值】商店列表.xlsx} 的「建筑材料商店」「回收商店」两个工作表；
 * 物品 id 已于 Minecraft 26.3 注册表逐个核对。额度统一换算为物品个数，{@code -1} 表示不限。</p>
 */
public final class ShopCatalog {

	private static final int STACK = 64;
	private static final int UNLIMITED = ShopEntry.UNLIMITED;

	private static List<ShopSheet> sheets = List.of();

	private ShopCatalog() {
	}

	/** 在模组初始化时构建条目表（此时注册表已就绪）。 */
	public static void init() {
		if (!sheets.isEmpty()) {
			return;
		}

		int[] skipped = {0};

		/* 建筑材料商店暂时停用；恢复时删除本注释块即可
		CatBuilder stone = new CatBuilder("stone", "石类", "build", TradeDirection.BUY, skipped)
				.item("granite", STACK, 2, 4 * STACK, UNLIMITED)
				.item("diorite", STACK, 2, 4 * STACK, UNLIMITED)
				.item("andesite", STACK, 2, 4 * STACK, UNLIMITED)
				.item("tuff", STACK, 2, 4 * STACK, UNLIMITED)
				.item("deepslate", STACK, 4, 4 * STACK, UNLIMITED)
				.item("packed_mud", STACK, 8, 1 * STACK, 4 * STACK)
				.item("dripstone_block", STACK, 8, 1 * STACK, 4 * STACK)
				.item("calcite", STACK, 16, 1 * STACK, 4 * STACK)
				.item("cinnabar", STACK, 8, 1 * STACK, 4 * STACK)
				.item("sulfur", STACK, 8, 1 * STACK, 4 * STACK)
				.item("netherrack", STACK, 2, 4 * STACK, UNLIMITED)
				.item("blackstone", STACK, 4, 4 * STACK, UNLIMITED)
				.item("end_stone", STACK, 8, 4 * STACK, UNLIMITED)
				.item("quartz_block", STACK, 8, 1 * STACK, UNLIMITED);

		CatBuilder nature = new CatBuilder("nature", "自然", "build", TradeDirection.BUY, skipped)
				.item("grass_block", STACK, 2, 4 * STACK, UNLIMITED)
				.item("podzol", STACK, 8, 0, 1 * STACK)
				.item("mycelium", STACK, 16, 0, 1 * STACK)
				.item("crimson_nylium", STACK, 8, 0, 1 * STACK)
				.item("warped_nylium", STACK, 8, 0, 1 * STACK)
				.item("soul_soil", STACK, 2, 4 * STACK, UNLIMITED)
				.item("ice", STACK, 4, 4 * STACK, UNLIMITED)
				.item("amethyst_block", STACK, 16, 0, 1 * STACK)
				.item("clay_ball", STACK, 1, 16 * STACK, UNLIMITED);

		CatBuilder light = new CatBuilder("light", "发光", "build", TradeDirection.BUY, skipped)
				.item("glowstone", STACK, 8, 1 * STACK, 2 * STACK)
				.item("sea_lantern", STACK, 8, 1 * STACK, 2 * STACK)
				.item("shroomlight", STACK, 16, 0, 1 * STACK)
				.item("end_rod", STACK, 32, 0, 1 * STACK)
				.item("pearlescent_froglight", STACK, 16, 0, 1 * STACK)
				.item("ochre_froglight", STACK, 16, 0, 1 * STACK)
				.item("verdant_froglight", STACK, 16, 0, 1 * STACK)
				// 涂蜡铜灯：表格「1/个 or 32/32 个」取后者 —— 每次买 32 个，价格 32 通宝
				.item("waxed_copper_lantern", 32, 32, 0, 32)
				.item("waxed_exposed_copper_lantern", 32, 32, 0, 32)
				.item("waxed_weathered_copper_lantern", 32, 32, 0, 32)
				.item("waxed_oxidized_copper_lantern", 32, 32, 0, 32);

		CatBuilder dye = new CatBuilder("dye", "染料", "build", TradeDirection.BUY, skipped)
				.item("white_dye", STACK, 4, 0, 2 * STACK)
				.item("light_gray_dye", STACK, 4, 0, 2 * STACK)
				.item("gray_dye", STACK, 4, 0, 2 * STACK)
				.item("black_dye", STACK, 4, 0, 2 * STACK)
				.item("brown_dye", STACK, 4, 0, 2 * STACK)
				.item("red_dye", STACK, 4, 0, 2 * STACK)
				.item("orange_dye", STACK, 4, 0, 2 * STACK)
				.item("yellow_dye", STACK, 4, 0, 2 * STACK)
				.item("lime_dye", STACK, 4, 0, 2 * STACK)
				.item("green_dye", STACK, 4, 0, 2 * STACK)
				.item("cyan_dye", STACK, 4, 0, 2 * STACK)
				.item("light_blue_dye", STACK, 4, 0, 2 * STACK)
				.item("blue_dye", STACK, 4, 0, 2 * STACK)
				.item("purple_dye", STACK, 4, 0, 2 * STACK)
				.item("magenta_dye", STACK, 4, 0, 2 * STACK);

		ShopSheet build = new ShopSheet("建筑材料商店", List.of(stone.build(), nature.build(), light.build(), dye.build()));
		*/

		// 回收商店：所有身份共用「每日个人上限」一列
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

		CatBuilder explore = new CatBuilder("explore", "探索资源", "recycle", TradeDirection.SELL, skipped)
				.sell("echo_shard", 1, 100, 1)
				.sell("disc_fragment_5", 1, 100, 1)
				.sell("enchanted_golden_apple", 1, 500, 1)
				.sell("heart_of_the_sea", 1, 100, 1)
				.sell("sponge", 1, 5, 16)
				// 任意陶片：按物品标签匹配所有陶片
				.tag("sherds", "任意陶片", Items.PRIZE_POTTERY_SHERD, ItemTags.DECORATED_POT_SHERDS, 1, 25, 4)
				.sell("iron_horse_armor", 1, 100, 1)
				.sell("copper_horse_armor", 1, 100, 1)
				.sell("golden_horse_armor", 1, 100, 1)
				.sell("diamond_horse_armor", 1, 100, 1);

		ShopSheet recycle = new ShopSheet("回收商店", List.of(ore.build(), block.build(), explore.build()));

		sheets = List.of(recycle);

		int entryCount = sheets.stream().mapToInt(ShopSheet::entryCount).sum();
		Smp3Core.LOGGER.info("[SMP3] 已加载 {} 个商店、{} 个条目（跳过 {} 个无法解析的物品）",
				sheets.size(), entryCount, skipped[0]);
	}

	public static List<ShopSheet> sheets() {
		return sheets;
	}

	/** 按类别累积条目，自动生成条目 id。 */
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
				// 注册表对未知 id 返回默认值 AIR，这里视为数据错误并跳过该条目
				skipped[0]++;
				Smp3Core.LOGGER.error("[SMP3] 条目物品不存在，已跳过：{} / {}", itemId, id);
				return this;
			}
			entries.add(new ShopEntry(sheetId + "." + id + "." + itemId, direction, null,
					item, item, null, amount, price, normalLimit, advancedLimit));
			return this;
		}

		/** 回收条目：普通/进阶共用同一上限。 */
		private CatBuilder sell(String itemId, int amount, int price, int limit) {
			return item(itemId, amount, price, limit, limit);
		}

		private CatBuilder tag(String tagId, String displayName, Item icon, TagKey<Item> tag,
				int amount, int price, int limit) {
			entries.add(new ShopEntry(sheetId + "." + id + "." + tagId, direction, displayName,
					icon, null, tag, amount, price, limit, limit));
			return this;
		}

		private ShopCategory build() {
			return new ShopCategory(name, entries);
		}
	}
}
