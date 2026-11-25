package club.sjmc.smp3.daily.ui;

import java.util.ArrayList;
import java.util.List;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.daily.DailyQuestModule;
import club.sjmc.smp3.daily.DailyQuestPayloads;
import club.sjmc.smp3.daily.DailyQuestService;
import club.sjmc.smp3.daily.QuestDay;
import club.sjmc.smp3.item.ItemDefinitions;
import club.sjmc.smp3.item.RewardBundle;
import club.sjmc.smp3.ui.GuiStyle;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class DailyQuestView {
	private DailyQuestView() {}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(DailyQuestPayloads.Catalog.TYPE,
		                                               DailyQuestPayloads.Catalog.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DailyQuestPayloads.Reroll.TYPE, DailyQuestPayloads.Reroll.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DailyQuestPayloads.Request.TYPE,
		                                               DailyQuestPayloads.Request.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DailyQuestPayloads.Request.TYPE,
		                                            (request, context) -> refresh(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(DailyQuestPayloads.Reroll.TYPE,
		                                            (request, context) -> reroll(context.player(), request));
	}

	public static void refresh(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, DailyQuestPayloads.Catalog.TYPE))
			return;
		try {
			var catalog = build(player);
			if (catalog != null)
				ServerPlayNetworking.send(player, catalog);
		} catch (RuntimeException error) {
			Smp3Core.LOGGER.error("[SMP3] 每日任务界面刷新失败 player={}", player.getUUID(), error);
		}
	}

	private static void reroll(ServerPlayer player, DailyQuestPayloads.Reroll request) {
		DailyQuestService service = DailyQuestModule.current();
		if (service == null)
			return;
		try {
			var outcome = service.reroll(player, request.date(), request.slot(), request.token(), false);
			player.sendSystemMessage(
			    GuiStyle.text(QuestMessages.message(outcome), outcome.success() ? GuiStyle.READY : GuiStyle.ERROR));
		} catch (RuntimeException error) {
			Smp3Core.LOGGER.error("[SMP3] 每日任务刷新失败 player={}", player.getUUID(), error);
			player.sendSystemMessage(GuiStyle.text("更换任务失败，请稍后重试", GuiStyle.ERROR));
		}
		refresh(player);
	}

	private static DailyQuestPayloads.Catalog build(ServerPlayer player) {
		DailyQuestService service = DailyQuestModule.current();
		if (service == null)
			return null;
		QuestDay day = service.state(player);
		if (day == null)
			return null;
		List<DailyQuestPayloads.Task> tasks = new ArrayList<>();
		for (int slot = 0; slot < Math.min(4, day.assignments().size()); slot++) {
			QuestDay.Assignment task = day.assignments().get(slot);
			var icons = task.definition.iconStacks(player.registryAccess());
			tasks.add(new DailyQuestPayloads.Task(
			    task.token, limit(task.definition.name(), 128), task.definition.difficulty(), task.tier(), icons,
			    task.progress(), task.target, task.complete(), task.claimed(), day.canReroll(slot, service.config),
			    QuestText.conditions(task), rewards(task, player)));
		}
		return new DailyQuestPayloads.Catalog(day.date, day.remaining(service.config),
		                                      service.config.dailyRerollLimit(), List.copyOf(tasks));
	}

	private static List<Component> rewards(QuestDay.Assignment task, ServerPlayer player) {
		List<Component> lines = new ArrayList<>();
		var reward = task.reward;
		if (reward.tongbao() > 0)
			lines.add(Component.literal("通宝 x " + reward.tongbao()));
		if (reward.coffeeStamp() > 0)
			lines.add(Component.literal("咖啡印章 x " + reward.coffeeStamp()));
		for (var item : reward.items()) {
			var stack = ItemDefinitions.stack(item, player.registryAccess());
			lines.add(stack.getHoverName().copy().append(" x " + stack.getCount()));
		}
		for (var effect : reward.effects())
			lines.add(RewardBundle.description(effect, player.registryAccess()));
		return lines;
	}

	private static String limit(String value, int max) {
		return value.length() <= max ? value : value.substring(0, max);
	}
}
