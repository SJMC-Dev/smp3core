package club.sjmc.smp3.economy;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.netty.buffer.ByteBuf;

import club.sjmc.smp3.Smp3Core;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class BalanceSync {
	public enum View { NOTICE, SHOP }
	private static final class Subscription {
		final EnumSet<View> views = EnumSet.noneOf(View.class);
		Payload value;
	}
	private static final Map<UUID, Subscription> subscribers = new HashMap<>();
	private BalanceSync() {}

	public record Payload(long coins, long stamps, boolean initial) implements CustomPacketPayload {
		public static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("smp3", "balances"));
		public static final StreamCodec<ByteBuf, Payload> CODEC =
		    StreamCodec.composite(ByteBufCodecs.LONG, Payload::coins, ByteBufCodecs.LONG, Payload::stamps,
			                      ByteBufCodecs.BOOL, Payload::initial, Payload::new);
		@Override
		public Type<Payload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> subscribers.clear());
		ServerPlayConnectionEvents.DISCONNECT.register(
		    (handler, server) -> subscribers.remove(handler.player.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (subscribers.isEmpty() || server.getTickCount() % 20 != 0)
				return;
			for (var subscription : subscribers.entrySet()) {
				var player = server.getPlayerList().getPlayer(subscription.getKey());
				if (player == null)
					continue;
				refresh(player);
			}
		});
	}

	public static void subscribe(ServerPlayer player, View view, boolean open) {
		if (!open) {
			Subscription subscription = subscribers.get(player.getUUID());
			if (subscription != null) {
				subscription.views.remove(view);
				if (subscription.views.isEmpty())
					subscribers.remove(player.getUUID());
			}
		} else if (ServerPlayNetworking.canSend(player, Payload.TYPE)) {
			Subscription subscription = subscribers.computeIfAbsent(player.getUUID(), id -> new Subscription());
			subscription.views.add(view);
			subscription.value = snapshot(player, true);
			ServerPlayNetworking.send(player, subscription.value);
		}
	}
	public static void refresh(ServerPlayer player) {
		Subscription subscription = subscribers.get(player.getUUID());
		if (subscription == null)
			return;
		Payload next = snapshot(player, false), previous = subscription.value;
		if (next.coins != previous.coins || next.stamps != previous.stamps) {
			ServerPlayNetworking.send(player, next);
			subscription.value = next;
		}
	}

	private static Payload snapshot(ServerPlayer player, boolean initial) {
		try {
			return new Payload(
			    Smp3AccountStore.balance(player.getUUID(), Smp3Currency.TONGBAO) / Smp3Currency.RAW_PER_UNIT,
			    Smp3AccountStore.balance(player.getUUID(), Smp3Currency.COFFEE_STAMP) / Smp3Currency.RAW_PER_UNIT,
			    initial);
		} catch (RuntimeException e) {
			if (initial)
				Smp3Core.LOGGER.error("[SMP3] 无法读取玩家余额", e);
			return new Payload(-1, -1, initial);
		}
	}
}
