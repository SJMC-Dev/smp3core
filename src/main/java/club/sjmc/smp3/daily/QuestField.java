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
