package club.sjmc.smp3.daily;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class DailyQuestPayloads {
	private DailyQuestPayloads() {}

	public record Task(String token, String name, String difficulty, int tier, String icon, int progress, int target,
	                   boolean complete, boolean claimed, boolean rerollAvailable, List<String> conditions,
	                   List<String> rewards) {}

	public record Catalog(long date, int remaining, int limit, boolean open, List<Task> tasks)
	    implements CustomPacketPayload {
		private static final StreamCodec<ByteBuf, Task> TASK = StreamCodec.composite(
		    ByteBufCodecs.stringUtf8(64), Task::token, ByteBufCodecs.stringUtf8(128), Task::name,
		    ByteBufCodecs.stringUtf8(8), Task::difficulty, ByteBufCodecs.VAR_INT, Task::tier,
		    ByteBufCodecs.stringUtf8(128), Task::icon, ByteBufCodecs.VAR_INT, Task::progress, ByteBufCodecs.VAR_INT,
		    Task::target, ByteBufCodecs.BOOL, Task::complete, ByteBufCodecs.BOOL, Task::claimed, ByteBufCodecs.BOOL,
		    Task::rerollAvailable, ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(32)), Task::conditions,
		    ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(32)), Task::rewards, Task::new);
		public static final Type<Catalog> TYPE =
		    new Type<>(Identifier.fromNamespaceAndPath("smp3", "dailyquest_state"));
		public static final StreamCodec<ByteBuf, Catalog> CODEC = StreamCodec.composite(
		    ByteBufCodecs.VAR_LONG, Catalog::date, ByteBufCodecs.VAR_INT, Catalog::remaining, ByteBufCodecs.VAR_INT,
		    Catalog::limit, ByteBufCodecs.BOOL, Catalog::open, TASK.apply(ByteBufCodecs.list(4)), Catalog::tasks,
		    Catalog::new);

		@Override
		public Type<Catalog> type() {
			return TYPE;
		}
	}

	public record Reroll(long date, int slot, String token) implements CustomPacketPayload {
		public static final Type<Reroll> TYPE =
		    new Type<>(Identifier.fromNamespaceAndPath("smp3", "dailyquest_reroll"));
		public static final StreamCodec<ByteBuf, Reroll> CODEC = StreamCodec.composite(
		    ByteBufCodecs.VAR_LONG, Reroll::date, ByteBufCodecs.VAR_INT, Reroll::slot,
		    ByteBufCodecs.stringUtf8(64), Reroll::token, Reroll::new);

		@Override
		public Type<Reroll> type() {
			return TYPE;
		}
	}
}
