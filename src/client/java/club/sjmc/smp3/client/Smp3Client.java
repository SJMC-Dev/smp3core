package club.sjmc.smp3.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.daily.QuestHudPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class Smp3Client implements ClientModInitializer {
	static final QuestHud HUD = new QuestHud();
	private static final Path SETTINGS = FabricLoader.getInstance().getConfigDir().resolve("smp3/client.json");
	private static final Gson JSON = new Gson();
	private record Preferences(boolean questHudVisible) {}

	@Override
	public void onInitializeClient() {
		loadPreferences();
		KeyMapping toggle = KeyMappingHelper.registerKeyMapping(
		    new KeyMapping("key.smp3.quest_hud", InputConstants.KEY_H,
			               KeyMapping.Category.register(Identifier.fromNamespaceAndPath("smp3", "main"))));
		ClientPlayNetworking.registerGlobalReceiver(QuestHudPayload.TYPE,
		                                            (snapshot, context) -> HUD.state.accept(snapshot, HUD.visible));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HUD.state.clear());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, QuestHudPayload.TYPE.id(), HUD::render);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggle.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) {
					HUD.visible = !HUD.visible;
					HUD.state.setVisible(HUD.visible);
					savePreferences();
				}
			}
			if (client.gui.hud.isHidden())
				HUD.state.finishAnimations();
		});
	}

	private static void loadPreferences() {
		if (!Files.exists(SETTINGS))
			return;
		try {
			Preferences preferences = JSON.fromJson(Files.readString(SETTINGS), Preferences.class);
			if (preferences != null)
				HUD.visible = preferences.questHudVisible;
		} catch (IOException | RuntimeException e) {
			Smp3Core.LOGGER.error("[SMP3] 读取 HUD 显示设置失败，使用默认值", e);
		}
	}

	private static void savePreferences() {
		try {
			Files.createDirectories(SETTINGS.getParent());
			Files.writeString(SETTINGS, JSON.toJson(new Preferences(HUD.visible)));
		} catch (IOException e) {
			Smp3Core.LOGGER.error("[SMP3] 保存 HUD 显示设置失败", e);
		}
	}
}
