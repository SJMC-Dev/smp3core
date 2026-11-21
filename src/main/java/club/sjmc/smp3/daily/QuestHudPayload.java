package club.sjmc.smp3.daily;

import java.util.List;

import com.google.gson.JsonObject;

import club.sjmc.smp3.item.ItemDefinitions;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record QuestHudPayload(long date, List<Entry> entries, boolean diagnostic) implements CustomPacketPayload {
	/** 通道名带版本：旧客户端不认领该通道，不会解码错位，只会看到更新提示。 */
	public static final Type<QuestHudPayload> TYPE =
	    new Type<>(Identifier.fromNamespaceAndPath("smp3", "quest_hud2"));
	public static final StreamCodec<RegistryFriendlyByteBuf, QuestHudPayload> CODEC = StreamCodec.composite(
	    ByteBufCodecs.LONG, QuestHudPayload::date, Entry.CODEC.apply(ByteBufCodecs.list(4)), QuestHudPayload::entries,
	    ByteBufCodecs.BOOL, QuestHudPayload::diagnostic, QuestHudPayload::new);

	public record Request() implements CustomPacketPayload {
		public static final Request INSTANCE = new Request();
		public static final Type<Request> TYPE =
		    new Type<>(Identifier.fromNamespaceAndPath("smp3", "quest_hud_request"));
		public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Request> type() {
			return TYPE;
		}
	}

	public record Entry(String token, List<ItemStack> icons, int progress, int target, boolean claimed) {
		private static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
		    ByteBufCodecs.stringUtf8(36), Entry::token, ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(8)),
		    Entry::icons, ByteBufCodecs.VAR_INT, Entry::progress, ByteBufCodecs.VAR_INT, Entry::target,
		    ByteBufCodecs.BOOL, Entry::claimed, Entry::new);

		public boolean complete() { return progress >= target; }
	}

	public static QuestHudPayload of(QuestDay day, RegistryAccess registries) {
		if (day == null)
			return new QuestHudPayload(0, List.of(), false);
		return new QuestHudPayload(day.date,
		                           day.assignments.stream().map(task -> entry(task, registries)).toList(), false);
	}

	private static Entry entry(QuestDay.Assignment task, RegistryAccess registries) {
		List<ItemStack> icons = stacks(task.definition.icons(), registries);
		var goals = task.definition.objectives();
		if (goals.size() == 1 || !task.definition.completion().equals("any"))
			return new Entry(task.token, icons, task.progress, task.target, task.claimed);
		int best = 0;
		for (int i = 1; i < goals.size(); i++)
			if ((long)task.goalProgress(i) * goals.get(best).count() >
			    (long)task.goalProgress(best) * goals.get(i).count())
				best = i;
		var goal = goals.get(best);
		var conditions = goal.objective().conditions();
		List<ItemStack> derived = List.of();
		for (String field : List.of("item", "block")) {
			if (!conditions.has(field))
				continue;
			String value = QuestDefinition.values(conditions.get(field)).getFirst();
			if (value.startsWith("#"))
				continue;
			var id = Identifier.parse(value);
			var item = field.equals("item") ? BuiltInRegistries.ITEM.getValue(id)
			                                : BuiltInRegistries.BLOCK.getValue(id).asItem();
			if (item != Items.AIR) {
				derived = List.of(new ItemStack(item));
				break;
			}
		}
		return new Entry(task.token, derived.isEmpty() ? icons : derived, task.goalProgress(best), goal.count(),
		                 task.claimed);
	}

	private static List<ItemStack> stacks(List<JsonObject> icons, RegistryAccess registries) {
		return icons.stream().map(icon -> ItemDefinitions.stack(icon, registries).copyWithCount(1)).toList();
	}

	@Override
	public Type<QuestHudPayload> type() {
		return TYPE;
	}
}
