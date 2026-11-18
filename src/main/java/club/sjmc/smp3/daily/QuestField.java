package club.sjmc.smp3.daily;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

public enum QuestField {
	BLOCK("block", "方块", Registries.BLOCK),
	ENTITY("entity", "生物", Registries.ENTITY_TYPE),
	ITEM("item", "物品", Registries.ITEM),
	BIOME("biome", "群系", Registries.BIOME),
	STRUCTURE("structure", "结构", Registries.STRUCTURE),
	POTION("potion", "药水", Registries.POTION),
	EFFECT("effect", "效果", Registries.MOB_EFFECT),
	PATTERN("pattern", "旗帜图案", Registries.BANNER_PATTERN),
	TEMPLATE("template", "锻造模板", Registries.ITEM),
	RESULT("result", "转化结果", Registries.ENTITY_TYPE),
	PROFESSION("profession", "职业", Registries.VILLAGER_PROFESSION),
	ACTION("action", "交互", null),
	MOVEMENT("movement", "移动方式", null),
	VARIANT("variant", "变体", null),
	SOURCE("source", "加工方式", null),
	DIMENSION("dimension", "维度", null),
	BLOCK_STATE("block_state", "方块状态", null);

	private final String id;
	private final String label;
	private final ResourceKey<? extends Registry<?>> registry;

	QuestField(String id, String label, ResourceKey<? extends Registry<?>> registry) {
		this.id = id;
		this.label = label;
		this.registry = registry;
	}

	public Registry<?> registry(RegistryAccess access) { return access.lookupOrThrow(registry); }

	public static QuestField find(String id) {
		for (var field : values()) {
			if (field.id.equals(id)) {
				return field;
			}
		}
		throw new IllegalArgumentException("Unknown quest field: " + id);
	}

	public String label() { return label; }
}
