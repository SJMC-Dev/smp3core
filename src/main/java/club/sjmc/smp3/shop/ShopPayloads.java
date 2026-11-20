package club.sjmc.smp3.shop;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class ShopPayloads {
	public static final Gson JSON = new Gson();
	public static final int PART_SIZE = 8192, MAX_PARTS = 128;
	private static final StreamCodec<ByteBuf, String> ID = ByteBufCodecs.stringUtf8(128);
	private ShopPayloads() {}
	public record Entry(String id, JsonObject icon, List<String> description, boolean buy, int amount, int price,
	                    String currency, boolean title, String availability) {}
	public record Node(String id, String parent, String name, String icon, List<Entry> entries) {}
	public record View(List<Node> nodes) {}

	public record Catalog(long session, int part, int total, String text) implements CustomPacketPayload {
		public static final Type<Catalog> TYPE = new Type<>(Identifier.fromNamespaceAndPath("smp3", "shop_catalog"));
		public static final StreamCodec<ByteBuf, Catalog> CODEC = StreamCodec.composite(
		    ByteBufCodecs.VAR_LONG, Catalog::session, ByteBufCodecs.VAR_INT, Catalog::part, ByteBufCodecs.VAR_INT,
		    Catalog::total, ByteBufCodecs.stringUtf8(PART_SIZE), Catalog::text, Catalog::new);
		@Override
		public Type<Catalog> type() {
			return TYPE;
		}
	}
	public enum Action { SELECT, TRADE, CLOSE }
	public record Request(long session, Action action, String entry, long version, int batches)
	    implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath("smp3", "shop_request"));
		public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
		    ByteBufCodecs.VAR_LONG, Request::session,
		    ByteBufCodecs.VAR_INT.map(i -> Action.values()[i], Action::ordinal), Request::action, ID, Request::entry,
		    ByteBufCodecs.VAR_LONG, Request::version, ByteBufCodecs.VAR_INT, Request::batches, Request::new);
		@Override
		public Type<Request> type() {
			return TYPE;
		}
	}
	private static final StreamCodec<ByteBuf, ShopService.Quote> QUOTE = StreamCodec.composite(
	    ByteBufCodecs.VAR_LONG, ShopService.Quote::owned, ByteBufCodecs.VAR_LONG, ShopService.Quote::used,
	    ByteBufCodecs.VAR_INT, ShopService.Quote::limit, ByteBufCodecs.VAR_INT, ShopService.Quote::maximum,
	    ByteBufCodecs.VAR_LONG, ShopService.Quote::balance, ByteBufCodecs.BOOL, ShopService.Quote::weekly,
	    ByteBufCodecs.BOOL, ShopService.Quote::pendingTitle, ShopService.Quote::new);
	public record State(long session, String entry, long version, ShopService.Quote quote, String message)
	    implements CustomPacketPayload {
		public static final Type<State> TYPE = new Type<>(Identifier.fromNamespaceAndPath("smp3", "shop_state"));
		public static final StreamCodec<ByteBuf, State> CODEC = StreamCodec.composite(
		    ByteBufCodecs.VAR_LONG, State::session, ID, State::entry, ByteBufCodecs.VAR_LONG, State::version, QUOTE,
		    State::quote, ByteBufCodecs.stringUtf8(512), State::message, State::new);
		@Override
		public Type<State> type() {
			return TYPE;
		}
	}
}
