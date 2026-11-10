package club.sjmc.smp3.gui;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.sign.PlayerSignData;
import club.sjmc.smp3.sign.SignData;
import club.sjmc.smp3.sign.SignDates;
import club.sjmc.smp3.sign.SignRewardGranter;
import club.sjmc.smp3.sign.SignRewards;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 签到界面：6 行 54 格。
 *
 * <p>第一行 slot 4 为信息栏；第二行第一格（slot 9）起按当月天数连续显示日期格；
 * slot 49 为关闭按钮。只有「今天」这一格可以点击签到。</p>
 */
public final class SignGui extends SimpleGui {

	private static final int INFO_SLOT = 4;
	/** 日历第一格：第二行第一格。 */
	private static final int CALENDAR_START = 9;
	private static final int CLOSE_SLOT = 49;

	private final ServerPlayer player;
	private final SignData data;
	private final PlayerSignData state;

	private SignGui(ServerPlayer player, SignData data) {
		super(MenuType.GENERIC_9x6, player, false);
		this.player = player;
		this.data = data;
		this.state = data.of(player.getUUID());
		this.setTitle(Component.literal("签到"));
	}

	public static void open(ServerPlayer player) {
		SignData signData = SignData.get(player.level().getServer());
		new SignGui(player, signData).open();
	}

	@Override
	public boolean open() {
		LocalDate today = SignDates.today();
		SignDates.rotateMonthIfNeeded(state, today);
		populate(today);
		return super.open();
	}

	private void populate(LocalDate today) {
		for (int slot = 0; slot < 54; slot++) {
			clearSlot(slot);
		}
		setSlot(INFO_SLOT, infoElement());
		fillCalendar(today);
		setSlot(CLOSE_SLOT, closeElement());
	}

	private GuiElement infoElement() {
		return new GuiElementBuilder(new ItemStack(Items.CLOCK))
				.setName(Component.literal("签到信息").withStyle(ChatFormatting.GOLD))
				.addLoreLine(Component.literal("当前连续：" + state.streak() + " 天").withStyle(ChatFormatting.GRAY))
				.addLoreLine(Component.literal("本月累计：" + state.monthTotal() + " 天").withStyle(ChatFormatting.GRAY))
				.addLoreLine(Component.literal("开服累计：" + state.serverTotal() + " 天").withStyle(ChatFormatting.GRAY))
				.build();
	}

	private void fillCalendar(LocalDate today) {
		int todayDay = today.getDayOfMonth();
		int month = today.getMonthValue();
		boolean signedToday = state.lastSignEpochDay() == today.toEpochDay();
		int nextStreak = state.lastSignEpochDay() == today.minusDays(1).toEpochDay()
				? state.streak() + 1
				: 1;

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
			color = ChatFormatting.GRAY;
			status = "未到";
			clickable = false;
		} else if (signed) {
			wool = Items.WOOL.pick(DyeColor.LIME);
			color = ChatFormatting.GREEN;
			status = "已签到";
			clickable = false;
		} else if (day == todayDay) {
			wool = Items.WOOL.pick(DyeColor.YELLOW);
			color = ChatFormatting.YELLOW;
			status = "点击签到：今日奖励 " + SignRewards.dailyReward(nextStreak) + " 通宝";
			clickable = true;
		} else {
			wool = Items.WOOL.pick(DyeColor.YELLOW);
			color = ChatFormatting.YELLOW;
			status = "已漏签";
			clickable = false;
		}

		GuiElementBuilder builder = new GuiElementBuilder(new ItemStack(wool))
				.setName(Component.literal(month + "月" + day + "日").withStyle(color))
				.addLoreLine(Component.literal(status).withStyle(ChatFormatting.GRAY));

		if (clickable) {
			builder.setCallback((index, type, action, gui) -> {
				if (type == ClickType.MOUSE_LEFT) {
					signDaily(today);
				}
			});
		}
		return builder.build();
	}

	private GuiElement closeElement() {
		return new GuiElementBuilder(new ItemStack(Items.BARRIER))
				.setName(Component.literal("关闭").withStyle(ChatFormatting.RED))
				.setCallback((index, type, action, gui) -> {
					if (type == ClickType.MOUSE_LEFT) {
						close();
					}
				})
				.build();
	}

	private void signDaily(LocalDate today) {
		if (state.lastSignEpochDay() == today.toEpochDay()) {
			player.sendSystemMessage(Component.literal("今天已经签到过了"));
			return;
		}

		boolean continuous = state.lastSignEpochDay() == today.minusDays(1).toEpochDay();
		state.setStreak(continuous ? state.streak() + 1 : 1);
		int reward = SignRewards.dailyReward(state.streak());
		SignRewardGranter.grant(player, List.of(SignRewards.tongbao(reward)));

		state.setLastSignEpochDay(today.toEpochDay());
		state.setMonthTotal(state.monthTotal() + 1);
		state.setServerTotal(state.serverTotal() + 1);
		state.markMonthSigned(today.getDayOfMonth());

		List<String> notes = new ArrayList<>();
		for (SignRewards.Milestone milestone : SignRewards.MONTH) {
			if (state.monthTotal() >= milestone.days() && !state.isMonthClaimed(milestone.days())) {
				SignRewardGranter.grant(player, milestone.rewards());
				state.markMonthClaimed(milestone.days());
				notes.add("本月累计 " + milestone.days() + " 天");
			}
		}
		for (SignRewards.Milestone milestone : SignRewards.SERVER) {
			if (state.serverTotal() >= milestone.days() && !state.isServerClaimed(milestone.days())) {
				SignRewardGranter.grant(player, milestone.rewards());
				state.markServerClaimed(milestone.days());
				notes.add("开服累计 " + milestone.days() + " 天");
			}
		}
		data.markDirty();

		player.sendSystemMessage(Component.literal(
				"签到成功，获得 " + reward + " 通宝，连续 " + state.streak() + " 天").withStyle(ChatFormatting.GREEN));
		for (String note : notes) {
			player.sendSystemMessage(Component.literal(note + " 的累计奖励已发放").withStyle(ChatFormatting.GOLD));
		}
		Smp3Core.LOGGER.info("[SMP3] 签到：player={}({}) streak={} reward={} monthTotal={} serverTotal={}",
				player.getName().getString(), player.getUUID(), state.streak(), reward,
				state.monthTotal(), state.serverTotal());

		populate(today);
	}
}
