package club.sjmc.smp3.ui;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HudSettingsPayload() implements CustomPacketPayload {
	public static final HudSettingsPayload INSTANCE = new HudSettingsPayload();
	public static final Type<HudSettingsPayload> TYPE =
	    new Type<>(Identifier.fromNamespaceAndPath("smp3", "hud_settings"));
	public static final StreamCodec<ByteBuf, HudSettingsPayload> CODEC = StreamCodec.unit(INSTANCE);
	@Override
	public Type<HudSettingsPayload> type() {
		return TYPE;
	}
}
