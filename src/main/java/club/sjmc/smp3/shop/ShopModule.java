package club.sjmc.smp3.shop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.random.RandomGenerator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.time.ServerDates;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ShopModule {
	private static ShopModule current;
	private final MinecraftServer server;
	private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
	private final Path path = FabricLoader.getInstance().getConfigDir().resolve("smp3/shops.json");
	private ShopCatalog catalog, source;
	private ShopRotation rotation;
	private LocalDate day;
	private boolean refreshFailed;
	private final ShopSessions sessions = new ShopSessions();
	private boolean loading;

	private ShopModule(MinecraftServer server) { this.server = server; }
	public static ShopCatalog catalog() { return current.catalog; }
	public static boolean open(ServerPlayer player) { return open(player, true); }
	public static boolean open(ServerPlayer player, boolean open) {
		if (!refresh())
			return false;
		if (open)
			player.closeContainer();
		return current.sessions.open(player, open);
	}
	private void publish(JsonObject json) throws IOException {
		ShopCatalog candidate = ShopCatalog.parse(json, server.registryAccess());
		if (rotation == null)
			rotation = new ShopRotation(path.resolveSibling("shop-rotation.json"));
		publish(candidate);
		source = candidate;
	}
	private void publish(ShopCatalog candidate) throws IOException {
		LocalDate today = ServerDates.today();
		var selection = rotation.select(candidate, today, RandomGenerator.getDefault());
		var parts = ShopSessions.encode(selection.catalog(), server.registryAccess());
		rotation.commit(selection);
		catalog = selection.catalog();
		day = today;
		refreshFailed = false;
		sessions.catalog(server, parts);
	}
	static boolean refresh() {
		if (current.source == null)
			return false;
		if (!ServerDates.today().isAfter(current.day))
			return true;
		try {
			current.publish(current.source);
			return true;
		} catch (IOException | RuntimeException error) {
			if (!current.refreshFailed)
				Smp3Core.LOGGER.error("Shop rotation refresh failed; trading paused until retry", error);
			current.refreshFailed = true;
			return false;
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(ShopPayloads.Catalog.TYPE, ShopPayloads.Catalog.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ShopPayloads.State.TYPE, ShopPayloads.State.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ShopPayloads.Request.TYPE, ShopPayloads.Request.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(
		    ShopPayloads.Request.TYPE, (request, context) -> current.sessions.request(context.player(), request));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ShopTitles.deliver(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> current.sessions.close(handler.player));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (current != null && server.getTickCount() % 20 == 0 && refresh())
				current.sessions.tick(server);
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			current = new ShopModule(server);
			try {
				current.publish(current.read());
			} catch (Exception error) {
				Smp3Core.LOGGER.error("Shop configuration failed", error);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ShopTitles.clear();
			current.worker.shutdownNow();
			current = null;
		});
	}
	private JsonObject read() throws IOException {
		if (!Files.exists(path)) {
			Files.createDirectories(path.getParent());
			try (var input = ShopModule.class.getResourceAsStream("/shops.json")) {
				Files.copy(input, path);
			}
		}
		try (var reader = Files.newBufferedReader(path)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}
	public static boolean reload(Consumer<String> success, Consumer<String> failure) {
		ShopModule module = current;
		if (module.loading)
			return false;
		module.loading = true;
		module.worker.execute(() -> {
			try {
				JsonObject json = module.read();
				module.server.execute(() -> {
					if (current != module)
						return;
					try {
						module.publish(json);
						success.accept("商店已重载：" + module.catalog.shops().size() + " 个商店，" +
						               module.catalog.entries().size() + " 项交易");
					} catch (IOException | RuntimeException error) {
						module.failed(error, failure);
					} finally {
						module.loading = false;
					}
				});
			} catch (Exception error) {
				module.server.execute(() -> {
					if (current != module)
						return;
					module.loading = false;
					module.failed(error, failure);
				});
			}
		});
		return true;
	}
	private void failed(Exception error, Consumer<String> failure) {
		Smp3Core.LOGGER.error("Shop reload failed", error);
		failure.accept("商店配置错误：" + error.getMessage());
	}
}
