package club.sjmc.smp3.menu;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class MenuPayloads {
	private MenuPayloads() {}

	public record Open() implements CustomPacketPayload {
		public static final Open INSTANCE = new Open();
		public static final Type<Open> TYPE = new Type<>(Identifier.fromNamespaceAndPath("smp3", "menu_open"));
		public static final StreamCodec<ByteBuf, Open> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}
}
