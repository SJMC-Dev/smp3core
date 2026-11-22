package club.sjmc.smp3.menu.ui;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.daily.ui.DailyQuestView;
import club.sjmc.smp3.menu.MenuPayloads;
import club.sjmc.smp3.shop.ShopModule;
import club.sjmc.smp3.sign.ui.SignView;
import club.sjmc.smp3.title.ui.TitleView;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class MenuView {
	private MenuView() {}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(MenuPayloads.Open.TYPE, MenuPayloads.Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MenuPayloads.Request.TYPE, MenuPayloads.Request.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MenuPayloads.Request.TYPE, (request, context) -> {
			try {
				sendData(context.player());
			} catch (RuntimeException error) {
				Smp3Core.LOGGER.error("[SMP3] 菜单数据不可用 player={}", context.player().getUUID(), error);
				context.player().sendSystemMessage(Component.literal("菜单数据不可用，请联系管理员"));
			}
		});
	}

	public static void open(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, MenuPayloads.Open.TYPE)) {
			player.sendSystemMessage(Component.literal("请更新 SMP3 客户端以打开菜单"));
			return;
		}
		try {
			player.closeContainer();
			ServerPlayNetworking.send(player, MenuPayloads.Open.INSTANCE);
			sendData(player);
		} catch (RuntimeException error) {
			Smp3Core.LOGGER.error("[SMP3] 菜单数据不可用 player={}", player.getUUID(), error);
			player.sendSystemMessage(Component.literal("菜单数据不可用，请联系管理员"));
		}
	}

	private static void sendData(ServerPlayer player) {
		SignView.refresh(player);
		TitleView.refresh(player);
		DailyQuestView.refresh(player);
		ShopModule.open(player, false);
	}
}
