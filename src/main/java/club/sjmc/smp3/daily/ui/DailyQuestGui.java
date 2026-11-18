package club.sjmc.smp3.daily.ui;

import java.time.LocalDate;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import club.sjmc.smp3.daily.DailyQuestModule;
import club.sjmc.smp3.daily.DailyQuestService;
import club.sjmc.smp3.daily.QuestDay;
import club.sjmc.smp3.item.ItemDefinitions;
import club.sjmc.smp3.item.ItemDelivery;
import club.sjmc.smp3.item.RewardBundle;
import club.sjmc.smp3.ui.GuiStyle;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class DailyQuestGui extends SimpleGui {
	private final DailyQuestService service;
	private QuestDay shown;
	private int revision = -1;
	private long pending = -1;

	private DailyQuestGui(ServerPlayer player, DailyQuestService service) {
		super(MenuType.GENERIC_9x4, player, false);
		this.service = service;
		setTitle(GuiStyle.windowTitle("每日任务"));
	}

	public static boolean show(ServerPlayer player) {
		DailyQuestService service = DailyQuestModule.current();
		if (service != null && new DailyQuestGui(player, service).open())
			return true;
		player.sendSystemMessage(GuiStyle.text("每日任务不可用，请联系管理员", GuiStyle.ERROR));
		return false;
	}

	@Override
	public boolean open() {
		QuestDay current = service.state(player);
		if (current == null)
			return false;
		refresh(current);
		return super.open();
	}

	@Override
	public void onTick() {
		QuestDay current = service.state(player);
		if (current == null) {
			close();
			return;
		}
		if (current != shown || current.revision() != revision || pending != pendingCount())
			refresh(current);
	}

	private long pendingCount() {
		return ItemDelivery.pending(player).stream().mapToLong(stack -> stack.getCount()).sum() +
		    ItemDelivery.pendingEffects(player).size();
	}
	private void refresh(QuestDay current) {
		pending = pendingCount();
		shown = current;
		revision = shown.revision();
		GuiStyle.fill(this, 36);
		long complete = shown.assignments().stream().filter(QuestDay.Assignment::complete).count();
		long claimed = shown.assignments().stream().filter(a -> a.claimed()).count();
		setSlot(4, GuiStyle.item(Items.CLOCK, "每日任务", GuiStyle.HEADING)
		               .addLoreLine(GuiStyle.text(LocalDate.ofEpochDay(shown.date).toString(), GuiStyle.MUTED))
		               .addLoreLine(GuiStyle.text("已完成 " + complete + "/4", GuiStyle.MUTED))
		               .addLoreLine(GuiStyle.text("奖励已结算 " + claimed + "/4", GuiStyle.MUTED))
		               .addLoreLine(GuiStyle.text("剩余刷新 " + shown.remaining(service.config) + "/" +
		                                              service.config.dailyRerollLimit(),
		                                          GuiStyle.MUTED))
		               .addLoreLine(GuiStyle.resetInfo())
		               .addLoreLine(GuiStyle.text("完成后自动发放奖励", GuiStyle.MUTED))
		               .build());
		for (int slot = 0; slot < 4; slot++)
			task(slot);
		if (pending > 0)
			setSlot(31, GuiStyle.button(Items.CHEST, "待领取物品 x " + pending, GuiStyle.ACTIVE,
			                            () -> QuestRewardsGui.pending(player)));
		setSlot(35, GuiStyle.close(this::close));
	}

	private void task(int slot) {
		QuestDay.Assignment task = shown.assignments().get(slot);
		long date = shown.date;
		int column = 1 + slot * 2;
		DyeColor accent = slot == 0 ? DyeColor.LIGHT_BLUE : slot == 1 ? DyeColor.LIME : DyeColor.WHITE;
		var background =
		    GuiStyle.item(Items.STAINED_GLASS_PANE.pick(accent), " ", GuiStyle.MUTED).hideTooltip().build();
		setSlot(column, background);
		ChatFormatting color = task.claimed() ? GuiStyle.READY : GuiStyle.ACTIVE;
		String status = status(task);
		Item icon = BuiltInRegistries.ITEM.getValue(Identifier.parse(task.definition.icon()));
		GuiElementBuilder objective =
		    GuiStyle
		        .item(icon, task.definition.name() + (task.tier() == 0 ? "" : " " + task.tier() + "级"),
				      GuiStyle.difficulty(task.definition.difficulty()))
		        .addLoreLine(GuiStyle.text("难度 " + task.definition.difficulty(), GuiStyle.MUTED));
		objective.addLoreLine(Component.empty());
		var description = QuestText.lore(task);
		description.stream().limit(4).forEach(objective::addLoreLine);
		if (description.size() > 4)
			objective.addLoreLine(GuiStyle.text("……", GuiStyle.MUTED));
		objective.addLoreLine(Component.empty()).addLoreLine(GuiStyle.text("点击查看详细条件", GuiStyle.ACTION));
		setSlot(9 + column, GuiStyle.onLeftClick(objective, () -> new QuestDetailsGui(player, task).open()).build());
		var rewardIcon = GuiStyle.item(Items.WOOL.pick(task.claimed()    ? DyeColor.LIME
		                                               : task.complete() ? DyeColor.ORANGE
		                                                                 : DyeColor.WHITE),
		                               status, color);
		if (task.definition.objectives().size() > 1) {
			rewardIcon.addLoreLine(Component.empty());
			rewardIcon.addLoreLine(GuiStyle.text(
			    task.definition.completion().equals("any") ? "目标：任选一项" : "目标：全部完成", GuiStyle.HEADING));
			for (int i = 0; i < task.definition.objectives().size(); i++) {
				var goal = task.definition.objectives().get(i);
				int progress = task.goalProgress(i);
				rewardIcon.addLoreLine(GuiStyle.text(goal.name() + " (" + progress + "/" + goal.count() + ")",
				                                     progress >= goal.count() ? GuiStyle.READY : GuiStyle.MUTED));
			}
		}
		if (task.reward.tongbao() > 0 || task.reward.coffeeStamp() > 0 || !task.reward.items().isEmpty() ||
		    !task.reward.effects().isEmpty())
			rewardIcon.addLoreLine(Component.empty()).addLoreLine(GuiStyle.text("奖励", GuiStyle.HEADING));
		if (task.reward.tongbao() > 0)
			rewardIcon.addLoreLine(GuiStyle.text("通宝 x " + task.reward.tongbao(), GuiStyle.MUTED));
		if (task.reward.coffeeStamp() > 0)
			rewardIcon.addLoreLine(GuiStyle.text("咖啡印章 x " + task.reward.coffeeStamp(), GuiStyle.MUTED));
		var rewardItems =
		    task.reward.items().stream().map(item -> ItemDefinitions.stack(item, player.registryAccess())).toList();
		rewardItems.forEach(item
		                    -> rewardIcon.addLoreLine(GuiStyle.text(
		                        item.getHoverName().getString() + " x " + item.getCount(), GuiStyle.MUTED)));
		for (var effect : task.reward.effects())
			rewardIcon.addLoreLine(
			    RewardBundle.description(effect, player.registryAccess()).copy().withStyle(GuiStyle.MUTED));
		if (!rewardItems.isEmpty())
			rewardIcon.addLoreLine(Component.empty()).addLoreLine(GuiStyle.text("点击查看物品奖励", GuiStyle.ACTION));
		setSlot(18 + column, GuiStyle
		                         .onLeftClick(rewardIcon,
		                                      () -> {
			                                      if (!rewardItems.isEmpty())
				                                      QuestRewardsGui.preview(player, rewardItems);
		                                      })
		                         .build());
		if (task.complete()) {
			setSlot(27 + column, background);
			return;
		}
		boolean available = shown.canReroll(slot, service.config);
		String reason = shown.remaining(service.config) == 0 ? "今日刷新次数已用完" : "没有可替换的同难度任务";
		var reroll = GuiStyle.item(available ? Items.ARROW : Items.BARRIER, available ? "更换任务" : reason,
		                           available ? GuiStyle.ACTIVE : GuiStyle.MUTED);
		setSlot(27 + column, GuiStyle
		                         .onLeftClick(reroll,
		                                      () -> {
			                                      if (available) {
				                                      confirm(date, slot, task);
			                                      }
		                                      })
		                         .build());
	}

	private void confirm(long date, int slot, QuestDay.Assignment task) {
		QuestDay current = service.state(player);
		if (current == null) {
			close();
			return;
		}
		if (current.date != date || !current.assignments().get(slot).token.equals(task.token)) {
			refresh(current);
			return;
		}
		SimpleGui confirmation = new SimpleGui(MenuType.GENERIC_9x3, player, false) {
			@Override
			public void onTick() {
				QuestDay latest = service.state(player);
				if (latest == null) {
					close();
					return;
				}
				if (latest.date != date || !latest.assignments().get(slot).token.equals(task.token) ||
				    task.complete()) {
					DailyQuestGui.this.open();
				}
			}
		};
		confirmation.setTitle(GuiStyle.windowTitle("确认更换任务"));
		GuiStyle.fill(confirmation, 27);
		confirmation.setSlot(4, GuiStyle.item(Items.PAPER, task.definition.name(), GuiStyle.HEADING)
		                            .addLoreLine(GuiStyle.text("更换后进度清零", GuiStyle.MUTED))
		                            .addLoreLine(GuiStyle.text(
		                                "消耗 1 次，剩余 " + current.remaining(service.config) + " 次", GuiStyle.MUTED))
		                            .build());
		confirmation.setSlot(12, GuiStyle.button(Items.WOOL.pick(DyeColor.LIME), "确认更换", GuiStyle.READY, () -> {
			feedback(service.reroll(player, date, slot, task.token, false));
			open();
		}));
		confirmation.setSlot(14, GuiStyle.button(Items.WOOL.pick(DyeColor.RED), "取消", GuiStyle.ERROR, this::open));
		confirmation.open();
	}

	private void feedback(DailyQuestService.Outcome result) {
		player.sendSystemMessage(
		    GuiStyle.text(QuestMessages.message(result), result.success() ? GuiStyle.READY : GuiStyle.ERROR));
	}

	private static String status(QuestDay.Assignment task) {
		if (task.claimed())
			return "奖励已结算";
		String state = task.complete() ? "已完成，奖励待发放" : "未完成";
		return task.target == 1 ? state : state + " (" + task.progress() + "/" + task.target + ")";
	}
}
