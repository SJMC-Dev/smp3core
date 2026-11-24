package club.sjmc.smp3.sign;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.config.JsonConfig;
import club.sjmc.smp3.sign.ui.SignView;
import club.sjmc.smp3.time.ServerDates;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SignModule {
	private static SignConfig config;
	private SignModule() {}

	public static SignConfig config() {
		if (config == null)
			throw new IllegalStateException("签到配置不可用，请检查 sign.json");
		return config;
	}

	public static void reload(MinecraftServer server) throws IOException {
		config = SignConfig.parse(
		    JsonConfig.read(FabricLoader.getInstance().getConfigDir().resolve("smp3/sign.json"), "/sign.json"));
		server.getPlayerList().getPlayers().forEach(SignService::reconcile);
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(SignPayloads.State.TYPE, SignPayloads.State.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SignPayloads.Request.TYPE, SignPayloads.Request.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SignPayloads.Request.TYPE, (request, context) -> {
			ServerPlayer player = context.player();
			try {
				LocalDate date = LocalDate.of(request.year(), request.month(), request.day());
				SignService.sign(player, date).stream().map(SignView::message).forEach(player::sendSystemMessage);
			} catch (DateTimeException error) {
				player.sendSystemMessage(Component.literal("签到日期无效"));
			}
			SignView.refresh(player);
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				reload(server);
				SignData.get(server).nextRank(ServerDates.today().toEpochDay());
			} catch (Exception error) {
				Smp3Core.LOGGER.error("Sign configuration failed", error);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (config != null)
				SignService.reconcile(handler.player);
		});
		ServerTickEvents.END_SERVER_TICK.register(SignService::flushTitles);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			config = null;
			SignService.clear();
		});
	}
}
