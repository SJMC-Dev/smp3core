package club.sjmc.smp3.daily;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class DailyQuestPayloads {
	private DailyQuestPayloads() {}

	public record Task(String token, String name, String difficulty, int tier, List<ItemStack> icons, int progress,
	                   int target, boolean complete, boolean claimed, boolean rerollAvailable,
	                   List<Component> conditions, List<Component> rewards) {}

	public record Catalog(long date, int remaining, int limit, List<Task> tasks) implements CustomPacketPayload {
		private static final StreamCodec<RegistryFriendlyByteBuf, Task> TASK = StreamCodec.composite(
		    ByteBufCodecs.stringUtf8(64), Task::token, ByteBufCodecs.stringUtf8(128), Task::name,
		    ByteBufCodecs.stringUtf8(8), Task::difficulty, ByteBufCodecs.VAR_INT, Task::tier,
		    ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(QuestDefinition.MAX_ICONS)), Task::icons,
		    ByteBufCodecs.VAR_INT, Task::progress, ByteBufCodecs.VAR_INT, Task::target, ByteBufCodecs.BOOL,
		    Task::complete, ByteBufCodecs.BOOL, Task::claimed, ByteBufCodecs.BOOL, Task::rerollAvailable,
		    ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()), Task::conditions,
		    ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()), Task::rewards, Task::new);
		public static final Type<Catalog> TYPE =
		    new Type<>(Identifier.fromNamespaceAndPath("smp3", "dailyquest_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Catalog> CODEC = StreamCodec.composite(
		    ByteBufCodecs.VAR_LONG, Catalog::date, ByteBufCodecs.VAR_INT, Catalog::remaining, ByteBufCodecs.VAR_INT,
		    Catalog::limit, TASK.apply(ByteBufCodecs.list(4)), Catalog::tasks, Catalog::new);

		@Override
		public Type<Catalog> type() {
			return TYPE;
		}
	}

	public record Request() implements CustomPacketPayload {
		public static final Request INSTANCE = new Request();
		public static final Type<Request> TYPE =
		    new Type<>(Identifier.fromNamespaceAndPath("smp3", "dailyquest_request"));
		public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Request> type() {
			return TYPE;
		}
	}

	public record Reroll(long date, int slot, String token) implements CustomPacketPayload {
		public static final Type<Reroll> TYPE =
		    new Type<>(Identifier.fromNamespaceAndPath("smp3", "dailyquest_reroll"));
		public static final StreamCodec<ByteBuf, Reroll> CODEC =
		    StreamCodec.composite(ByteBufCodecs.VAR_LONG, Reroll::date, ByteBufCodecs.VAR_INT, Reroll::slot,
			                      ByteBufCodecs.stringUtf8(64), Reroll::token, Reroll::new);

		@Override
		public Type<Reroll> type() {
			return TYPE;
		}
	}
}
