package club.sjmc.smp3.title;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class TitlePayloads {
	private TitlePayloads() {}

	public record Entry(String id, String item, Component name, List<Component> description, boolean obtained) {}

	public record Select(String id) implements CustomPacketPayload {
		public static final Type<Select> TYPE = new Type<>(Identifier.fromNamespaceAndPath("smp3", "title_select"));
		public static final StreamCodec<ByteBuf, Select> CODEC =
		    StreamCodec.composite(ByteBufCodecs.stringUtf8(128), Select::id, Select::new);

		@Override
		public Type<Select> type() {
			return TYPE;
		}
	}

	public record Catalog(List<Entry> entries, String activeId) implements CustomPacketPayload {
		private static final StreamCodec<ByteBuf, Entry> ENTRY =
		    StreamCodec.composite(ByteBufCodecs.stringUtf8(128), Entry::id, ByteBufCodecs.stringUtf8(128), Entry::item,
			                      ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC, Entry::name,
			                      ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.apply(ByteBufCodecs.list(8)),
			                      Entry::description, ByteBufCodecs.BOOL, Entry::obtained, Entry::new);
		public static final Type<Catalog> TYPE = new Type<>(Identifier.fromNamespaceAndPath("smp3", "title_catalog"));
		public static final StreamCodec<ByteBuf, Catalog> CODEC =
		    StreamCodec.composite(ENTRY.apply(ByteBufCodecs.list(256)), Catalog::entries, ByteBufCodecs.stringUtf8(128),
			                      Catalog::activeId, Catalog::new);

		@Override
		public Type<Catalog> type() {
			return TYPE;
		}
	}
}
