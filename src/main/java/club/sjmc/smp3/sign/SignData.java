package club.sjmc.smp3.sign;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 全服签到数据，保存于 {@code world/data/sign-mod.dat}。
 */
public final class SignData extends SavedData {

	private static final Codec<SignData> CODEC = CompoundTag.CODEC.xmap(SignData::fromTag, SignData::toTag);

	public static final SavedDataType<SignData> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("smp3", "sign-mod"),
			SignData::new,
			CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<UUID, PlayerSignData> players = new HashMap<>();

	/** 取（必要时创建）全服签到数据；数据固定存放在主世界。 */
	public static SignData get(net.minecraft.server.MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** 取（必要时创建）玩家数据。 */
	public PlayerSignData of(UUID player) {
		return players.computeIfAbsent(player, key -> new PlayerSignData());
	}

	/** 标记为待保存。 */
	public void markDirty() {
		setDirty();
	}

	private CompoundTag toTag() {
		CompoundTag root = new CompoundTag();
		CompoundTag entries = new CompoundTag();
		players.forEach((uuid, data) -> entries.put(uuid.toString(), data.save()));
		root.put("players", entries);
		return root;
	}

	private static SignData fromTag(CompoundTag root) {
		SignData result = new SignData();
		CompoundTag entries = root.getCompound("players").orElseGet(CompoundTag::new);
		for (String key : entries.keySet()) {
			try {
				UUID owner = UUID.fromString(key);
				CompoundTag value = entries.getCompound(key).orElseGet(CompoundTag::new);
				result.players.put(owner, PlayerSignData.load(value));
			} catch (IllegalArgumentException e) {
				// 损坏的 uuid 键直接跳过，不影响其它玩家数据
			}
		}
		return result;
	}
}
