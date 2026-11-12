package club.sjmc.smp3.daily;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record QuestHudPayload(long date, List<Entry> entries) implements CustomPacketPayload {
	public static final Type<QuestHudPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("smp3", "quest_hud"));
	public static final StreamCodec<ByteBuf, QuestHudPayload> CODEC =
	    StreamCodec.composite(ByteBufCodecs.LONG, QuestHudPayload::date, Entry.CODEC.apply(ByteBufCodecs.list(4)),
		                      QuestHudPayload::entries, QuestHudPayload::new);

	public record Entry(String token, Identifier icon, int progress, int target, boolean claimed) {
		private static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
		    ByteBufCodecs.stringUtf8(36), Entry::token, Identifier.STREAM_CODEC, Entry::icon, ByteBufCodecs.VAR_INT,
		    Entry::progress, ByteBufCodecs.VAR_INT, Entry::target, ByteBufCodecs.BOOL, Entry::claimed, Entry::new);

		public boolean complete() { return progress >= target; }
	}

	public static QuestHudPayload of(QuestDay day) {
		if (day == null)
			return new QuestHudPayload(0, List.of());
		return new QuestHudPayload(
		    day.date,
		    day.assignments.stream()
		        .map(a -> new Entry(a.token, Identifier.parse(a.definition.icon()), a.progress, a.target, a.claimed))
		        .toList());
	}

	@Override
	public Type<QuestHudPayload> type() {
		return TYPE;
	}
}
