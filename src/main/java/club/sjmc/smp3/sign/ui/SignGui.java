package club.sjmc.smp3.sign.ui;

import java.time.LocalDate;

import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.sign.PlayerSignData;
import club.sjmc.smp3.sign.SignRewards;
import club.sjmc.smp3.sign.SignService;
import club.sjmc.smp3.time.ServerDates;
import club.sjmc.smp3.ui.GuiStyle;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SignGui extends SimpleGui {

	private static final int INFO_SLOT = 4;
	private static final int CALENDAR_START = 9;
	private static final int CLOSE_SLOT = 49;

	private PlayerSignData state;
	private LocalDate shownDate;

	private SignGui(ServerPlayer player) {
		super(MenuType.GENERIC_9x6, player, false);
		setTitle(GuiStyle.windowTitle("签到"));
	}

	public static void open(ServerPlayer player) { new SignGui(player).open(); }

	@Override
	public boolean open() {
		return refresh() && super.open();
	}

	@Override
	public void onTick() {
		if (!ServerDates.today().equals(shownDate)) {
			refresh();
		}
	}

	private boolean refresh() {
		try {
			shownDate = ServerDates.today();
			state = SignService.state(player);
			populate(shownDate);
			return true;
		} catch (RuntimeException e) {
			Smp3Core.LOGGER.error("[SMP3] 签到数据不可用 player={}", player.getUUID(), e);
			player.sendSystemMessage(GuiStyle.text("签到数据不可用，请联系管理员", GuiStyle.ERROR));
			close();
			return false;
		}
	}

	private void populate(LocalDate today) {
		GuiStyle.fill(this, 54);
		setSlot(INFO_SLOT, infoElement());
		fillCalendar(today);
		setSlot(CLOSE_SLOT, GuiStyle.close(this::close));
	}

	private GuiElement infoElement() {
		return new GuiElementBuilder(new ItemStack(Items.CLOCK))
		    .setName(Component.literal("签到信息").withStyle(GuiStyle.HEADING))
		    .addLoreLine(GuiStyle.resetInfo())
		    .addLoreLine(Component.literal("连续 " + state.streak() + " 天").withStyle(GuiStyle.MUTED))
		    .addLoreLine(Component.literal("本月 " + state.monthTotal() + " 天").withStyle(GuiStyle.MUTED))
		    .addLoreLine(Component.literal("累计 " + state.serverTotal() + " 天").withStyle(GuiStyle.MUTED))
		    .build();
	}

	private void fillCalendar(LocalDate today) {
		int todayDay = today.getDayOfMonth();
		int month = today.getMonthValue();
		boolean signedToday = state.lastSignEpochDay() == today.toEpochDay();
		int nextStreak = state.lastSignEpochDay() == today.minusDays(1).toEpochDay() ? state.streak() + 1 : 1;

		for (int day = 1; day <= today.lengthOfMonth(); day++) {
			setSlot(CALENDAR_START + day - 1, dayElement(today, month, day, todayDay, signedToday, nextStreak));
		}
	}

	private GuiElement dayElement(LocalDate today, int month, int day, int todayDay, boolean signedToday,
	                              int nextStreak) {
		boolean signed = state.isMonthSigned(day) || (day == todayDay && signedToday);

		Item wool;
		ChatFormatting color;
		String status;
		boolean clickable;

		if (day > todayDay) {
			wool = Items.WOOL.pick(DyeColor.LIGHT_GRAY);
			color = GuiStyle.MUTED;
			status = "未到";
			clickable = false;
		} else if (signed) {
			wool = Items.WOOL.pick(DyeColor.LIME);
			color = GuiStyle.READY;
			clickable = day == todayDay && state.hasPendingRewards();
			status = "已签到";
		} else if (day == todayDay) {
			wool = Items.WOOL.pick(DyeColor.YELLOW);
			color = GuiStyle.ACTION;
			status = "今日待签到";
			clickable = true;
		} else {
			wool = Items.WOOL.pick(DyeColor.GRAY);
			color = GuiStyle.MUTED;
			status = "已漏签";
			clickable = false;
		}

		GuiElementBuilder builder = new GuiElementBuilder(new ItemStack(wool))
		                                .setName(Component.literal(month + "月" + day + "日").withStyle(color))
		                                .addLoreLine(Component.literal(status).withStyle(color));

		if (clickable) {
			if (!signed)
				builder.addLoreLine(GuiStyle.text("通宝 x " + SignRewards.dailyReward(nextStreak), GuiStyle.MUTED));
			builder.addLoreLine(GuiStyle.text(signed ? "左键重试奖励" : "左键签到", GuiStyle.ACTION));
			GuiStyle.onLeftClick(builder, () -> signDaily(today));
		}
		return builder.build();
	}

	private void signDaily(LocalDate today) {
		SignService.sign(player, today).stream().map(SignGui::message).forEach(player::sendSystemMessage);
		refresh();
	}

	private static Component message(SignService.Outcome result) {
		String label = (result.month().isEmpty() ? "开服累计 " : result.month() + " 月累计 ") + result.days() + " 天";
		return switch (result.status()) {
			case DATE_CHANGED -> GuiStyle.text("签到日期已更新，请重新操作", GuiStyle.ACTIVE);
			case SIGNED ->
				GuiStyle.text("签到成功，获得 " + result.coins() + " 通宝，连续 " + result.days() + " 天",
				              GuiStyle.READY);
			case ALREADY_SIGNED -> Component.literal("今天已经签到过了");
			case SAVE_FAILED -> GuiStyle.text("签到或奖励保存失败，请稍后重试", GuiStyle.ERROR);
			case REVIEW -> GuiStyle.text(label + " 奖励待管理员核对", GuiStyle.ERROR);
			case RETRY -> GuiStyle.text(label + " 奖励未发放，可点击今日签到重试", GuiStyle.ERROR);
			case MILESTONE_GRANTED ->
				GuiStyle.text((result.month().isEmpty() ? "开服累计 " : "本月累计 ") + result.days() +
				                  " 天 的累计奖励已发放",
				              GuiStyle.HEADING);
		};
	}
}
