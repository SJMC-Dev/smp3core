package club.sjmc.smp3.sign.ui;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.sign.SignPayloads;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SignView {
	private SignView() {}

	public static void open(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, SignPayloads.State.TYPE)) {
			// 旧客户端兼容：新客户端统一走 MenuScreen 的签到面板，最低客户端版本提升后删除。
			SignGui.open(player);
			return;
		}
		try {
			player.closeContainer();
			ServerPlayNetworking.send(player, SignPayloads.State.current(player, true));
		} catch (RuntimeException error) {
			Smp3Core.LOGGER.error("[SMP3] 签到界面数据不可用 player={}", player.getUUID(), error);
			player.sendSystemMessage(Component.literal("签到数据不可用，请联系管理员"));
		}
	}

	public static void refresh(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, SignPayloads.State.TYPE))
			return;
		try {
			ServerPlayNetworking.send(player, SignPayloads.State.current(player, false));
		} catch (RuntimeException error) {
			Smp3Core.LOGGER.error("[SMP3] 签到界面刷新失败 player={}", player.getUUID(), error);
		}
	}
}
