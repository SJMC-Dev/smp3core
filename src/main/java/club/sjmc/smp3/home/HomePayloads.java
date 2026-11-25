package club.sjmc.smp3.home;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class HomePayloads {
	private HomePayloads() {}
	public enum Action { REFRESH, TELEPORT, CREATE, RENAME, UPDATE, DELETE }
	public record Entry(String name, String dimension, int x, int y, int z) {}

	public record Catalog(List<Entry> entries, boolean available, String message,
	                      int limit) implements CustomPacketPayload {
		private static final StreamCodec<ByteBuf, Entry> ENTRY = StreamCodec.composite(
		    ByteBufCodecs.stringUtf8(256), Entry::name, ByteBufCodecs.stringUtf8(256), Entry::dimension,
		    ByteBufCodecs.INT, Entry::x, ByteBufCodecs.INT, Entry::y, ByteBufCodecs.INT, Entry::z, Entry::new);
		public static final Type<Catalog> TYPE = new Type<>(Identifier.fromNamespaceAndPath("smp3", "home_catalog"));
		public static final StreamCodec<ByteBuf, Catalog> CODEC =
		    StreamCodec.composite(ENTRY.apply(ByteBufCodecs.list(1024)), Catalog::entries, ByteBufCodecs.BOOL,
			                      Catalog::available, ByteBufCodecs.stringUtf8(512), Catalog::message,
			                      ByteBufCodecs.INT, Catalog::limit, Catalog::new);
		@Override
		public Type<Catalog> type() {
			return TYPE;
		}
	}

	public record Request(Action action, String name, String newName) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath("smp3", "home_request"));
		public static final StreamCodec<ByteBuf, Request> CODEC =
		    StreamCodec.composite(ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal), Request::action,
			                      ByteBufCodecs.stringUtf8(256), Request::name, ByteBufCodecs.stringUtf8(256),
			                      Request::newName, Request::new);
		@Override
		public Type<Request> type() {
			return TYPE;
		}
	}
}
