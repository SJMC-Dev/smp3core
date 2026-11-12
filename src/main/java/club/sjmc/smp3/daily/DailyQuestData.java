package club.sjmc.smp3.daily;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.Codec;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

final class DailyQuestData extends SavedData {
	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
	private static final SavedDataType<DailyQuestData> TYPE =
	    new SavedDataType<>(Identifier.fromNamespaceAndPath("smp3", "dailyquests"), DailyQuestData::new,
		                    Codec.STRING.xmap(DailyQuestData::decode, data -> JSON.toJson(data.players)), null);

	final Map<UUID, QuestDay> players = new HashMap<>();

	private static DailyQuestData decode(String text) {
		DailyQuestData data = new DailyQuestData();
		Map<UUID, QuestDay> saved = JSON.fromJson(text, new TypeToken<Map<UUID, QuestDay>>() {}.getType());
		for (QuestDay day : saved.values()) {
			if (day.pendingRewards == null)
				day.pendingRewards = new ArrayList<>();
		}
		data.players.putAll(saved);
		return data;
	}

	public static DailyQuestData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}
}
