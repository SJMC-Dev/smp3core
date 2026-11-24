package club.sjmc.smp3.home;

import java.io.IOException;
import java.util.List;

import club.sjmc.smp3.Smp3Core;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.server.level.ServerPlayer;

public final class HomeModule {
	private static HomeStore store;
	private static HomeService service;

	private HomeModule() {}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			store = new HomeStore(FabricLoader.getInstance().getConfigDir().resolve("smp3/homes"));
			service = new HomeService(store);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			service = null;
			store = null;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> store.forget(handler.player.getUUID()));
		PayloadTypeRegistry.clientboundPlay().register(HomePayloads.Catalog.TYPE, HomePayloads.Catalog.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(HomePayloads.Request.TYPE, HomePayloads.Request.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(HomePayloads.Request.TYPE,
		                                            (request, context) -> handle(context.player(), request));
	}

	public static void refresh(ServerPlayer player) {
		handle(player, new HomePayloads.Request(HomePayloads.Action.REFRESH, "", ""));
	}

	private static void handle(ServerPlayer player, HomePayloads.Request request) {
		if (!ServerPlayNetworking.canSend(player, HomePayloads.Catalog.TYPE))
			return;
		HomePayloads.Catalog catalog;
		try {
			String message = service.apply(player, request);
			boolean available = HomeService.available(player);
			catalog = new HomePayloads.Catalog(available ? service.entries(player) : List.of(), available, message);
		} catch (IOException | RuntimeException error) {
			Smp3Core.LOGGER.error("[SMP3 Home] Failed to {} for {}", request.action(), player.getUUID(), error);
			catalog = new HomePayloads.Catalog(List.of(), true, "家园操作失败，请联系管理员检查日志");
		}
		ServerPlayNetworking.send(player, catalog);
	}
}
