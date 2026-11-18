package club.sjmc.smp3.notice;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class NoticePayloads {
	private NoticePayloads() {}
	private static final StreamCodec<ByteBuf, String> ID = ByteBufCodecs.stringUtf8(64);
	private static final StreamCodec<ByteBuf, NoticeCatalog.Category> CATEGORY =
	    StreamCodec.composite(ID, NoticeCatalog.Category::id, ByteBufCodecs.stringUtf8(32),
		                      NoticeCatalog.Category::name, NoticeCatalog.Category::new);
	private static final StreamCodec<ByteBuf, NoticeCatalog.Entry> ENTRY = StreamCodec.composite(
	    ID, NoticeCatalog.Entry::id, ID, NoticeCatalog.Entry::category, ByteBufCodecs.stringUtf8(128),
	    NoticeCatalog.Entry::title, ByteBufCodecs.stringUtf8(10), NoticeCatalog.Entry::date, ID,
	    NoticeCatalog.Entry::revision, ByteBufCodecs.VAR_INT, NoticeCatalog.Entry::flags, NoticeCatalog.Entry::new);

	public record Catalog(String title, List<NoticeCatalog.Category> categories, List<NoticeCatalog.Entry> entries,
	                      String openId) implements CustomPacketPayload {
		public static final Type<Catalog> TYPE = new Type<>(Identifier.fromNamespaceAndPath("smp3", "notice_catalog"));
		public static final StreamCodec<ByteBuf, Catalog> CODEC = StreamCodec.composite(
		    ByteBufCodecs.stringUtf8(128), Catalog::title, CATEGORY.apply(ByteBufCodecs.list(16)), Catalog::categories,
		    ENTRY.apply(ByteBufCodecs.list(128)), Catalog::entries, ID, Catalog::openId, Catalog::new);
		@Override
		public Type<Catalog> type() {
			return TYPE;
		}
	}

	public record Body(String id, String revision, String text, boolean markdown) implements CustomPacketPayload {
		public static final Type<Body> TYPE = new Type<>(Identifier.fromNamespaceAndPath("smp3", "notice_body"));
		public static final StreamCodec<ByteBuf, Body> CODEC =
		    StreamCodec.composite(ID, Body::id, ID, Body::revision, ByteBufCodecs.stringUtf8(32768), Body::text,
			                      ByteBufCodecs.BOOL, Body::markdown, Body::new);
		@Override
		public Type<Body> type() {
			return TYPE;
		}
	}

	public record Editor(String session, String draft, String message, List<NoticeCatalog.Category> categories)
	    implements CustomPacketPayload {
		public static final Type<Editor> TYPE = new Type<>(Identifier.fromNamespaceAndPath("smp3", "notice_editor"));
		public static final StreamCodec<ByteBuf, Editor> CODEC = StreamCodec.composite(
		    ID, Editor::session, ByteBufCodecs.stringUtf8(262144), Editor::draft, ByteBufCodecs.stringUtf8(2048),
		    Editor::message, CATEGORY.apply(ByteBufCodecs.list(16)), Editor::categories, Editor::new);
		@Override
		public Type<Editor> type() {
			return TYPE;
		}
	}

	public record Publish(String session, int part, int total, String text) implements CustomPacketPayload {
		public static final Type<Publish> TYPE = new Type<>(Identifier.fromNamespaceAndPath("smp3", "notice_publish"));
		public static final StreamCodec<ByteBuf, Publish> CODEC =
		    StreamCodec.composite(ID, Publish::session, ByteBufCodecs.VAR_INT, Publish::part, ByteBufCodecs.VAR_INT,
			                      Publish::total, ByteBufCodecs.stringUtf8(8192), Publish::text, Publish::new);
		@Override
		public Type<Publish> type() {
			return TYPE;
		}
	}

	public record Delete(String session) implements CustomPacketPayload {
		public static final Type<Delete> TYPE = new Type<>(Identifier.fromNamespaceAndPath("smp3", "notice_delete"));
		public static final StreamCodec<ByteBuf, Delete> CODEC = ID.map(Delete::new, Delete::session);
		@Override
		public Type<Delete> type() {
			return TYPE;
		}
	}

	public enum Action { OPEN, CLOSE, BODY, READ, ACKNOWLEDGE }

	public record Request(Action action, String id, String revision) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath("smp3", "notice_request"));
		public static final StreamCodec<ByteBuf, Request> CODEC =
		    StreamCodec.composite(ByteBufCodecs.VAR_INT.map(i -> Action.values()[i], Action::ordinal), Request::action,
			                      ID, Request::id, ID, Request::revision, Request::new);
		@Override
		public Type<Request> type() {
			return TYPE;
		}
	}
}
