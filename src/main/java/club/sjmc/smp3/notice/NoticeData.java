package club.sjmc.smp3.notice;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.Codec;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

final class NoticeData extends SavedData {
	private static final Gson JSON = new Gson();
	static final Codec<NoticeData> CODEC = Codec.STRING.xmap(NoticeData::decode, data -> JSON.toJson(data.players));
	private static final SavedDataType<NoticeData> TYPE =
	    new SavedDataType<>(Identifier.fromNamespaceAndPath("smp3", "notices"), NoticeData::new, CODEC, null);
	private final Map<UUID, Map<String, String>> players = new HashMap<>();

	static NoticeData get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(TYPE); }

	private static NoticeData decode(String text) {
		NoticeData data = new NoticeData();
		Map<UUID, Map<String, String>> saved =
		    JSON.fromJson(text, new TypeToken<Map<UUID, Map<String, String>>>() {}.getType());
		data.players.putAll(saved);
		return data;
	}

	boolean read(UUID player, NoticeCatalog.Entry entry) {
		return entry.revision().equals(players.getOrDefault(player, Map.of()).get(entry.id()));
	}

	boolean complete(UUID player, NoticeCatalog.Entry entry, String revision, boolean confirmed) {
		if (!entry.revision().equals(revision) || entry.acknowledgement() && !confirmed || read(player, entry))
			return false;
		players.computeIfAbsent(player, key -> new HashMap<>()).put(entry.id(), revision);
		setDirty();
		return true;
	}
}
