package club.sjmc.smp3.daily.ui;

import java.time.LocalDate;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import club.sjmc.smp3.daily.DailyQuestModule;
import club.sjmc.smp3.daily.DailyQuestService;
import club.sjmc.smp3.daily.QuestDay;
import club.sjmc.smp3.gui.GuiStyle;
import club.sjmc.smp3.time.ServerDates;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
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

	private DailyQuestGui(ServerPlayer player, DailyQuestService service) {
		super(MenuType.GENERIC_9x4, player, false);
		this.service = service;
		setTitle(GuiStyle.text("每日任务", GuiStyle.TITLE));
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
		if (current != shown || current.revision() != revision)
			refresh(current);
	}

	private void refresh(QuestDay current) {
		shown = current;
		revision = shown.revision();
		GuiStyle.fill(this, 36);
		long complete = shown.assignments().stream().filter(QuestDay.Assignment::complete).count();
		long claimed = shown.assignments().stream().filter(a -> a.claimed()).count();
		setSlot(4, GuiStyle.item(Items.CLOCK, "每日任务", GuiStyle.TITLE)
		               .addLoreLine(GuiStyle.text(LocalDate.ofEpochDay(shown.date).toString(), GuiStyle.MUTED))
		               .addLoreLine(GuiStyle.text("已完成 " + complete + "/4", GuiStyle.MUTED))
		               .addLoreLine(GuiStyle.text("奖励已到账 " + claimed + "/4", GuiStyle.MUTED))
		               .addLoreLine(GuiStyle.text("剩余刷新 " + shown.remaining(service.config) + "/" +
		                                              service.config.dailyRerollLimit(),
		                                          GuiStyle.MUTED))
		               .addLoreLine(GuiStyle.text("每日 00:00 重置 (" + ServerDates.zone() + ")", GuiStyle.MUTED))
		               .addLoreLine(GuiStyle.text("完成后自动发放奖励", GuiStyle.MUTED))
		               .build());
		for (int slot = 0; slot < 4; slot++)
			task(slot);
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
		        .item(icon, task.definition.name() + (task.tier() == 0 ? "" : " " + task.tier() + "级"), GuiStyle.TITLE)
		        .addLoreLine(GuiStyle.text("难度 " + task.definition.difficulty(), GuiStyle.MUTED));
		QuestText.lore(task).forEach(objective::addLoreLine);
		setSlot(9 + column, objective.build());
		setSlot(18 + column, GuiStyle
		                         .item(Items.WOOL.pick(task.claimed()    ? DyeColor.LIME
		                                               : task.complete() ? DyeColor.ORANGE
		                                                                 : DyeColor.WHITE),
		                               status, color)
		                         .addLoreLine(GuiStyle.text("通宝 x " + task.reward.tongbao(), GuiStyle.MUTED))
		                         .addLoreLine(GuiStyle.text("咖啡印章 x " + task.reward.coffeeStamp(), GuiStyle.MUTED))
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
		confirmation.setTitle(GuiStyle.text("确认更换任务", GuiStyle.TITLE));
		GuiStyle.fill(confirmation, 27);
		confirmation.setSlot(4, GuiStyle.item(Items.PAPER, task.definition.name(), GuiStyle.TITLE)
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
			return "奖励已到账";
		String state = task.complete() ? "已完成，奖励待发放" : "未完成";
		return task.target == 1 ? state : state + " (" + task.progress() + "/" + task.target + ")";
	}
}
