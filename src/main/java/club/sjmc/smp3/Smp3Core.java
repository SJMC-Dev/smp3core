package club.sjmc.smp3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import club.sjmc.smp3.config.ServerConfig;
import club.sjmc.smp3.daily.DailyQuestModule;
import club.sjmc.smp3.daily.ui.DailyQuestCommand;
import club.sjmc.smp3.economy.Smp3EconomyProvider;
import club.sjmc.smp3.item.Smp3Items;
import club.sjmc.smp3.milestone.MilestoneModule;
import club.sjmc.smp3.milestone.ui.RewardCommand;
import club.sjmc.smp3.notice.NoticeModule;
import club.sjmc.smp3.notice.ui.NoticeCommand;
import club.sjmc.smp3.shop.ShopModule;
import club.sjmc.smp3.shop.ui.ShopCommand;
import club.sjmc.smp3.sign.SignModule;
import club.sjmc.smp3.sign.ui.SignCommand;
import club.sjmc.smp3.time.ServerDates;
import club.sjmc.smp3.ui.HudSettingsPayload;
import club.sjmc.smp3.watchdog.WatchdogModule;
import club.sjmc.smp3.watchdog.ui.SmpPerfCommand;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

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
		ShopModule.register();
		SignModule.register();
		MilestoneModule.register();
		Smp3EconomyProvider.register();

		DailyQuestModule.register();
		WatchdogModule.register();
		NoticeModule.register();
		PayloadTypeRegistry.clientboundPlay().register(HudSettingsPayload.TYPE, HudSettingsPayload.CODEC);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			var root =
			    Commands.literal("smp3")
			        .then(ShopCommand.command())
			        .then(SignCommand.command())
			        .then(RewardCommand.command())
			        .then(SmpPerfCommand.command())
			        .then(NoticeCommand.command())
			        .then(Commands.literal("hud").executes(context -> {
				        var player = context.getSource().getPlayerOrException();
				        if (!ServerPlayNetworking.canSend(player, HudSettingsPayload.TYPE)) {
					        LOGGER.info("[SMP3 HUD] server player={} uuid={} settingsChannel=false",
							            player.getName().getString(), player.getUUID());
					        context.getSource().sendFailure(Component.literal("请更新 SMP3 客户端以打开 HUD 设置"));
					        return 0;
				        }
				        ServerPlayNetworking.send(player, HudSettingsPayload.INSTANCE);
				        return DailyQuestModule.refreshHud(player) ? 1 : 0;
			        }));
			DailyQuestCommand.register(root);
			dispatcher.register(root);
		});

		LOGGER.info("[SMP3] 初始化完成");
	}
}
