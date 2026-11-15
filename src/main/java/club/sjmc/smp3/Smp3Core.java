package club.sjmc.smp3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import club.sjmc.smp3.config.ServerConfig;
import club.sjmc.smp3.daily.DailyQuestModule;
import club.sjmc.smp3.daily.ui.DailyQuestCommand;
import club.sjmc.smp3.economy.Smp3EconomyProvider;
import club.sjmc.smp3.item.Smp3Items;
import club.sjmc.smp3.shop.ShopCatalog;
import club.sjmc.smp3.shop.ui.ShopCommand;
import club.sjmc.smp3.sign.ui.SignCommand;
import club.sjmc.smp3.time.ServerDates;
import club.sjmc.smp3.watchdog.WatchdogModule;
import club.sjmc.smp3.watchdog.ui.SmpPerfCommand;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class Smp3Core implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("SMP3-Core");

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			try {
				ServerDates.configure(
				    ServerConfig.load(FabricLoader.getInstance().getConfigDir().resolve("smp3/server.json")));
			} catch (Exception e) {
				throw new IllegalStateException("Invalid SMP3 server configuration", e);
			}
		});
		Smp3Items.init();
		ShopCatalog.init();
		Smp3EconomyProvider.register();

		DailyQuestModule.register();
		WatchdogModule.register();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			ShopCommand.register(dispatcher);
			SignCommand.register(dispatcher);
			DailyQuestCommand.register(dispatcher);
			SmpPerfCommand.register(dispatcher);
		});

		LOGGER.info("[SMP3] 初始化完成");
	}
}
