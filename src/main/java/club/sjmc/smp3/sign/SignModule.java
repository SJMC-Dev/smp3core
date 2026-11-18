package club.sjmc.smp3.sign;

import java.io.IOException;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.config.JsonConfig;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

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
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				reload(server);
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
