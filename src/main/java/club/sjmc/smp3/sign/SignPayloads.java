package club.sjmc.smp3.sign;

import java.time.LocalDate;
import java.util.List;

import io.netty.buffer.ByteBuf;

import club.sjmc.smp3.time.ServerDates;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class SignPayloads {
	private SignPayloads() {}

	public record State(int year, int month, int today, int signedMask, boolean pending, List<String> rewards)
	    implements CustomPacketPayload {
		public static final Type<State> TYPE = new Type<>(Identifier.fromNamespaceAndPath("smp3", "sign_state"));
		public static final StreamCodec<ByteBuf, State> CODEC = StreamCodec.composite(
		    ByteBufCodecs.VAR_INT, State::year, ByteBufCodecs.VAR_INT, State::month, ByteBufCodecs.VAR_INT,
		    State::today, ByteBufCodecs.VAR_INT, State::signedMask, ByteBufCodecs.BOOL, State::pending,
		    ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(8)), State::rewards, State::new);

		public static State current(ServerPlayer player) {
			LocalDate date = ServerDates.today();
			var data = SignService.state(player);
			int mask = 0;
			for (int day = 1; day <= date.lengthOfMonth(); day++)
				if (data.isMonthSigned(day))
					mask |= 1 << (day - 1);
			int nextStreak = data.lastSignEpochDay() == date.minusDays(1).toEpochDay() ? data.streak() + 1 : 1;
			List<String> rewards = data.isMonthSigned(date.getDayOfMonth())
			                           ? List.of()
			                           : SignModule.config()
			                                 .dailyRewards(nextStreak)
			                                 .entrySet()
			                                 .stream()
			                                 .map(entry -> entry.getKey().name().getString() + " x " + entry.getValue())
			                                 .toList();
			return new State(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), mask, data.hasPendingRewards(),
			                 rewards);
		}

		public boolean signed(int day) { return day >= 1 && day <= 31 && (signedMask & (1 << (day - 1))) != 0; }

		@Override
		public Type<State> type() {
			return TYPE;
		}
	}

	public record Request(int year, int month, int day) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath("smp3", "sign_request"));
		public static final StreamCodec<ByteBuf, Request> CODEC =
		    StreamCodec.composite(ByteBufCodecs.VAR_INT, Request::year, ByteBufCodecs.VAR_INT, Request::month,
			                      ByteBufCodecs.VAR_INT, Request::day, Request::new);

		@Override
		public Type<Request> type() {
			return TYPE;
		}
	}
}
