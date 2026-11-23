package club.sjmc.smp3.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.mojang.blaze3d.platform.InputConstants;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.daily.DailyQuestPayloads;
import club.sjmc.smp3.daily.QuestHudPayload;
import club.sjmc.smp3.economy.BalanceSync;
import club.sjmc.smp3.menu.MenuPayloads;
import club.sjmc.smp3.notice.NoticePayloads;
import club.sjmc.smp3.shop.ShopPayloads;
import club.sjmc.smp3.sign.SignPayloads;
import club.sjmc.smp3.title.TitlePayloads;
import club.sjmc.smp3.ui.hud.CurrencyHud;
import club.sjmc.smp3.ui.hud.HudLayout;
import club.sjmc.smp3.ui.hud.NoticeHud;
import club.sjmc.smp3.ui.hud.QuestHud;
import club.sjmc.smp3.ui.screen.HudSettingsScreen;
import club.sjmc.smp3.ui.screen.MenuScreen;
import club.sjmc.smp3.ui.screen.NoticeEditorScreen;
import club.sjmc.smp3.ui.state.CurrencyState;
import club.sjmc.smp3.ui.state.ServerBookState;
import club.sjmc.smp3.ui.state.ShopState;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;

public final class Smp3Client implements ClientModInitializer {
	static final QuestHud HUD = new QuestHud();
	static final ServerBookState BOOK = new ServerBookState();
	static final NoticeHud NOTICE = new NoticeHud(BOOK);
	static final CurrencyState BALANCES = new CurrencyState();
	static final CurrencyHud CURRENCY = new CurrencyHud(BALANCES);
	static final ShopState SHOP = new ShopState();
	static HudLayout layout = new HudLayout();

	public static QuestHud hud() { return HUD; }

	public static ServerBookState book() { return BOOK; }

	public static NoticeHud noticeHud() { return NOTICE; }

	public static CurrencyState balances() { return BALANCES; }

	public static CurrencyHud currencyHud() { return CURRENCY; }

	public static ShopState shop() { return SHOP; }

	public static HudLayout layout() { return layout; }
	private static boolean pendingSettings;
	private static NoticePayloads.Editor pendingEditor;
	private static final Path SETTINGS = FabricLoader.getInstance().getConfigDir().resolve("smp3/client.json");
	private static final Gson JSON = new Gson();
	private record
	    Preferences(@SerializedName(value = "hudVisible", alternate = {"questHudVisible"}) Boolean hudVisible,
		            HudLayout layout) {}

	@Override
	public void onInitializeClient() {
		loadPreferences();
		ClientPlayNetworking.registerGlobalReceiver(ShopPayloads.Catalog.TYPE,
		                                            (payload, context) -> SHOP.accept(payload));
		ClientPlayNetworking.registerGlobalReceiver(ShopPayloads.State.TYPE,
		                                            (payload, context) -> SHOP.accept(payload));
		ClientPlayNetworking.registerGlobalReceiver(NoticePayloads.Editor.TYPE, (payload, context) -> {
			if (!payload.draft().isEmpty())
				pendingEditor = payload;
			else if (context.client().gui.screen() instanceof NoticeEditorScreen screen)
				screen.result(payload);
		});
		ClientPlayNetworking.registerGlobalReceiver(HudSettingsPayload.TYPE, (request, context) -> {
			Smp3Core.LOGGER.info("[SMP3 HUD] client settings visible={} rows={}", HUD.visible, HUD.state.rows.size());
			pendingSettings = true;
		});
		ClientPlayNetworking.registerGlobalReceiver(NoticePayloads.Catalog.TYPE,
		                                            (catalog, context) -> BOOK.accept(catalog));
		ClientPlayNetworking.registerGlobalReceiver(NoticePayloads.Body.TYPE, (body, context) -> BOOK.accept(body));
		ClientPlayNetworking.registerGlobalReceiver(SignPayloads.State.TYPE, (state, context) -> {
			var client = context.client();
			if (state.open())
				openMenu(1, menu -> menu.acceptSign(state));
			else if (client.gui.screen() instanceof MenuScreen menu)
				menu.acceptSign(state);
		});
		ClientPlayNetworking.registerGlobalReceiver(TitlePayloads.Catalog.TYPE, (catalog, context) -> {
			var client = context.client();
			if (catalog.open())
				openMenu(4, menu -> menu.acceptTitles(catalog));
			else if (client.gui.screen() instanceof MenuScreen menu)
				menu.acceptTitles(catalog);
		});
		ClientPlayNetworking.registerGlobalReceiver(DailyQuestPayloads.Catalog.TYPE, (catalog, context) -> {
			var client = context.client();
			if (catalog.open())
				openMenu(2, menu -> menu.acceptQuests(catalog));
			else if (client.gui.screen() instanceof MenuScreen menu)
				menu.acceptQuests(catalog);
		});
		ClientPlayNetworking.registerGlobalReceiver(MenuPayloads.Open.TYPE, (payload, context) -> {
			var client = context.client();
			if (client.player != null)
				client.gui.setScreen(new MenuScreen());
		});
		ClientPlayNetworking.registerGlobalReceiver(BalanceSync.Payload.TYPE,
		                                            (balance, context) -> BALANCES.accept(balance));
		var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("smp3", "main"));
		KeyMapping toggle = KeyMappingHelper.registerKeyMapping(
		    new KeyMapping("key.smp3.quest_hud", InputConstants.UNKNOWN.getValue(), category));
		ClientPlayNetworking.registerGlobalReceiver(QuestHudPayload.TYPE, (snapshot, context) -> {
			int before = HUD.state.rows.size();
			HUD.state.accept(snapshot, HUD.visible);
			if (snapshot.diagnostic())
				Smp3Core.LOGGER.info(
				    "[SMP3 HUD] client snapshot date={} entries={} unclaimed={} visible={} rowsBefore={} rowsAfter={}",
				    snapshot.date(), snapshot.entries().size(),
				    snapshot.entries().stream().filter(e -> !e.claimed()).count(), HUD.visible, before,
				    HUD.state.rows.size());
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (ClientPlayNetworking.canSend(QuestHudPayload.Request.TYPE))
				ClientPlayNetworking.send(QuestHudPayload.Request.INSTANCE);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			HUD.state.clear();
			BOOK.clear();
			BALANCES.clear();
			SHOP.clear();
			pendingSettings = false;
			pendingEditor = null;
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, QuestHudPayload.TYPE.id(), HUD::render);
		KeyMapping book = KeyMappingHelper.registerKeyMapping(
		    new KeyMapping("key.smp3.server_info", InputConstants.UNKNOWN.getValue(), category));
		KeyMapping settings = KeyMappingHelper.registerKeyMapping(
		    new KeyMapping("key.smp3.hud_settings", InputConstants.UNKNOWN.getValue(), category));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (SHOP.pendingOpen && client.player != null && client.level != null && canOpenMenu(client)) {
				SHOP.pendingOpen = false;
				if (client.gui.screen() instanceof MenuScreen menu)
					menu.openTab(3);
				else
					openMenu(3, menu -> {});
			}
			if (pendingEditor != null && client.player != null && client.level != null && canOpenMenu(client)) {
				client.gui.setScreen(new NoticeEditorScreen(pendingEditor));
				pendingEditor = null;
			}
			if (BOOK.pendingOpen != null && client.player != null && client.level != null && canOpenMenu(client)) {
				boolean required = !BOOK.pendingOpen.equals("*");
				if (required) {
					BOOK.category = 0;
					for (int i = 0; i < BOOK.messages.size(); i++)
						if (BOOK.messages.get(i).id().equals(BOOK.pendingOpen))
							BOOK.selected = i;
				}
				BOOK.pendingOpen = null;
				openMenu(0, menu -> {});
			}
			if (pendingSettings && client.player != null && client.level != null &&
			    (client.gui.screen() == null || client.gui.screen() instanceof ChatScreen)) {
				pendingSettings = false;
				client.gui.setScreen(new HudSettingsScreen());
			}
			while (toggle.consumeClick())
				if (client.player != null && client.gui.screen() == null)
					toggleHud();
			while (book.consumeClick())
				if (client.player != null && client.gui.screen() == null)
					openBook();
			while (settings.consumeClick())
				if (client.player != null && client.gui.screen() == null)
					client.gui.setScreen(new HudSettingsScreen());
			if (client.gui.hud.isHidden())
				HUD.state.finishAnimations();
		});
	}

	public static void toggleHud() {
		HUD.visible = !HUD.visible;
		HUD.state.setVisible(HUD.visible);
		savePreferences();
	}

	public static void openBook() { openMenu(0, menu -> {}); }

	private static void openMenu(int tab, Consumer<MenuScreen> action) {
		var client = Minecraft.getInstance();
		if (client.player == null)
			return;
		var menu = new MenuScreen();
		client.gui.setScreen(menu);
		menu.openTab(tab);
		action.accept(menu);
		if (ClientPlayNetworking.canSend(MenuPayloads.Request.TYPE))
			ClientPlayNetworking.send(MenuPayloads.Request.INSTANCE);
	}

	private static boolean canOpenMenu(Minecraft client) {
		var screen = client.gui.screen();
		return screen == null || screen instanceof ChatScreen || screen instanceof MenuScreen;
	}

	private static void loadPreferences() {
		if (!Files.exists(SETTINGS))
			return;
		try {
			Preferences preferences = JSON.fromJson(Files.readString(SETTINGS), Preferences.class);
			if (preferences != null) {
				if (preferences.hudVisible != null)
					HUD.visible = preferences.hudVisible;
				if (preferences.layout != null)
					layout = preferences.layout;
			}
		} catch (IOException | RuntimeException e) {
			Smp3Core.LOGGER.error("[SMP3] 读取 HUD 显示设置失败，使用默认值", e);
		}
	}

	public static void savePreferences() {
		try {
			Files.createDirectories(SETTINGS.getParent());
			Files.writeString(SETTINGS, JSON.toJson(new Preferences(HUD.visible, layout)));
		} catch (IOException e) {
			Smp3Core.LOGGER.error("[SMP3] 保存 HUD 显示设置失败", e);
		}
	}
}
