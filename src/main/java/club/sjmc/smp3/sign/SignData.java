package club.sjmc.smp3.sign;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;

import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.economy.Smp3Currency;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

final class SignData extends SavedData {
	static final Gson JSON = new Gson();

	private static final Codec<SignData> CODEC = CompoundTag.CODEC.xmap(SignData::fromTag, SignData::toTag);

	public static final SavedDataType<SignData> TYPE =
	    new SavedDataType<>(Identifier.fromNamespaceAndPath("smp3", "sign-mod"), SignData::new, CODEC,
		                    DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<UUID, PlayerSignData> players = new HashMap<>();
	private boolean accountsLoaded;
	private long rankDay = PlayerSignData.NEVER_SIGNED;
	private int lastRank;

	int nextRank(long day) {
		if (!accountsLoaded) {
			Smp3AccountStore.forEachRewardState(
			    "sign", (owner, state) -> players.put(owner, JSON.fromJson(state, PlayerSignData.class)));
			accountsLoaded = true;
		}
		if (rankDay != day) {
			rankDay = day;
			int signedPlayers = 0;
			lastRank = 0;
			for (var state : players.values()) {
				if (state.lastSignEpochDay() != day)
					continue;
				signedPlayers++;
				lastRank = Math.max(lastRank, state.dailyRank());
			}
			lastRank = Math.max(lastRank, signedPlayers);
		}
		return Math.addExact(lastRank, 1);
	}

	public static SignData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public PlayerSignData of(UUID player) {
		JsonObject committed = Smp3AccountStore.rewardState(player, "sign");
		PlayerSignData cached = players.get(player);
		if (committed != null && !committed.equals(JSON.toJsonTree(cached))) {
			cached = JSON.fromJson(committed, PlayerSignData.class);
			players.put(player, cached);
			setDirty();
		}
		cached = players.computeIfAbsent(player, key -> new PlayerSignData());
		cached.receipt = committed;
		return cached;
	}

	void commit(UUID player, JsonObject expected, PlayerSignData state, Map<Smp3Currency, Integer> credits) {
		JsonObject next = JSON.toJsonTree(state).getAsJsonObject();
		Smp3AccountStore.commitReward(player, "sign", expected, next, credits);
		state.receipt = next;
		if (state.lastSignEpochDay() == rankDay)
			lastRank = Math.max(lastRank, state.dailyRank());
		players.put(player, state);
		setDirty();
	}

	static PlayerSignData copy(PlayerSignData state) {
		PlayerSignData copy = JSON.fromJson(JSON.toJsonTree(state), PlayerSignData.class);
		copy.receipt = state.receipt;
		return copy;
	}

	private CompoundTag toTag() {
		CompoundTag root = new CompoundTag();
		CompoundTag entries = new CompoundTag();
		players.forEach((uuid, data) -> entries.put(uuid.toString(), data.save()));
		root.put("players", entries);
		return root;
	}

	static SignData fromTag(CompoundTag root) {
		SignData result = new SignData();
		CompoundTag entries = root.getCompound("players").orElseGet(CompoundTag::new);
		for (String key : entries.keySet()) {
			try {
				UUID owner = UUID.fromString(key);
				CompoundTag value = entries.getCompound(key).orElseGet(CompoundTag::new);
				result.players.put(owner, PlayerSignData.load(value));
			} catch (IllegalArgumentException e) {
			}
		}
		return result;
	}
}
