package club.sjmc.smp3.sign.ui;

import java.util.stream.Collectors;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.sign.SignPayloads;
import club.sjmc.smp3.sign.SignService;
import club.sjmc.smp3.ui.GuiStyle;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SignView {
	private SignView() {}

	public static void refresh(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, SignPayloads.State.TYPE))
			return;
		try {
			ServerPlayNetworking.send(player, SignPayloads.State.current(player));
		} catch (RuntimeException error) {
			Smp3Core.LOGGER.error("[SMP3] 签到界面刷新失败 player={}", player.getUUID(), error);
		}
	}

	public static Component message(SignService.Outcome result) {
		String label = (result.month().isEmpty() ? "累计 " : result.month() + " 月累计 ") + result.days() + " 天";
		return switch (result.status()) {
			case DATE_CHANGED -> GuiStyle.text("签到日期已更新，请重新操作", GuiStyle.ACTIVE);
			case SIGNED ->
				GuiStyle.text("签到成功，获得 " +
				                  result.rewards()
				                      .entrySet()
				                      .stream()
				                      .map(entry -> entry.getKey().name().getString() + " x " + entry.getValue())
				                      .collect(Collectors.joining("、")) +
				                  "，连续 " + result.days() + " 天",
				              GuiStyle.READY);
			case ALREADY_SIGNED -> Component.literal("今天已经签到过了");
			case SAVE_FAILED -> GuiStyle.text("签到或奖励保存失败，请稍后重试", GuiStyle.ERROR);
			case REVIEW -> GuiStyle.text(label + " 奖励待管理员核对", GuiStyle.ERROR);
			case RETRY -> GuiStyle.text(label + " 奖励未发放，可点击今日签到重试", GuiStyle.ERROR);
			case MILESTONE_GRANTED ->
				GuiStyle.text((result.month().isEmpty() ? "累计 " : "本月累计 ") + result.days() +
				                  " 天 的累计奖励已发放",
				              GuiStyle.HEADING);
		};
	}
}
