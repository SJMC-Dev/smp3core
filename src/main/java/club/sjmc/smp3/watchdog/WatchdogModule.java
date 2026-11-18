package club.sjmc.smp3.watchdog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.watchdog.cleanup.ItemCleanupService;
import club.sjmc.smp3.watchdog.hotspot.HotspotRecorder;
import club.sjmc.smp3.watchdog.hotspot.HotspotReport;
import club.sjmc.smp3.watchdog.hotspot.JvmPauses;

import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;

public final class WatchdogModule {
	private static volatile WatchdogModule instance;
	private final MinecraftServer server;
	private final Path file;
	private final ThreadPoolExecutor worker =
	    new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16), runnable -> {
		    Thread thread = new Thread(runnable, "smp3-watchdog");
		    thread.setDaemon(true);
		    return thread;
	    });
	private final ItemCleanupService cleanup = new ItemCleanupService();
	private final Object commitLock = new Object();
	private volatile boolean running = true;
	private volatile JvmPauses jvm;
	private HotspotRecorder recorder;
	private boolean occupied;
	private CommandSourceStack recipient;
	private double minimum;
	private ItemCleanupService.Statistics cleanupStart;
	private WatchdogConfig config;

	private WatchdogModule(MinecraftServer server) {
		this.server = server;
		this.file = FabricLoader.getInstance().getConfigDir().resolve("smp3/watchdog.json");
	}

	public static WatchdogModule current() { return instance; }

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> instance = new WatchdogModule(server));
		ServerLifecycleEvents.SERVER_STARTED.register(
		    server -> instance.change(server.createCommandSourceStack(), null));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			WatchdogModule current = instance;
			if (current == null)
				return;
			synchronized (current.commitLock) { current.running = false; }
			current.recorder = null;
			current.worker.shutdownNow();
			JvmPauses pauses = current.jvm;
			if (pauses != null)
				Thread.ofVirtual().start(pauses::close);
			current.cleanup.clear();
			instance = null;
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (instance != null && entity instanceof ItemEntity item)
				instance.cleanup.add(item);
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (instance != null && entity instanceof ItemEntity item)
				instance.cleanup.remove(item);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (instance != null)
				instance.cleanup.tick();
		});
		CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
			WatchdogModule current = instance;
			if (!client && current != null && current.config != null)
				current.change(current.server.createCommandSourceStack(), null);
		});
	}

	public static void moved(ItemEntity item) {
		WatchdogModule current = instance;
		if (current != null && item.level() instanceof ServerLevel)
			current.cleanup.moved(item);
	}

	public static HotspotRecorder active(Level level) {
		WatchdogModule current = instance;
		if (current == null)
			return null;
		HotspotRecorder data = current.recorder;
		if (data == null || !(level instanceof ServerLevel) || !current.server.isSameThread())
			return null;
		return data.active() ? data : null;
	}

	public static void beginTick(MinecraftServer server) {
		WatchdogModule current = instance;
		if (current != null && current.server == server && current.recorder != null)
			current.recorder.beginTick();
	}

	public static void endTick(MinecraftServer server) {
		WatchdogModule current = instance;
		if (current == null || current.server != server || current.recorder == null)
			return;
		HotspotRecorder data = current.recorder;
		data.endTick();
		if (!data.expired())
			return;
		current.recorder = null;
		var cleanupEnd = current.cleanup.statistics();
		if (!current.submit(current.recipient, () -> current.finish(data, cleanupEnd))) {
			JvmPauses pauses = current.jvm;
			current.jvm = null;
			if (pauses != null)
				Thread.ofVirtual().start(pauses::close);
			current.occupied = false;
			current.recipient = null;
		}
	}

	public int top(CommandSourceStack source, int seconds, double minimum) {
		if (!Double.isFinite(minimum) || minimum < 0) {
			source.sendFailure(Component.literal("minimumMillisPerTick must be finite and nonnegative"));
			return 0;
		}
		if (occupied) {
			source.sendFailure(
			    Component.literal("A server-wide recording is already starting, recording or preparing its report."));
			return 0;
		}
		occupied = true;
		recipient = source;
		this.minimum = minimum;
		source.sendSuccess(
		    () -> Component.literal("SMP3 top starting: " + seconds + "s…").withStyle(ChatFormatting.AQUA), false);
		boolean queued = submit(source, () -> {
			try {
				try {
					jvm = new JvmPauses();
				} catch (RuntimeException e) {
					Smp3Core.LOGGER.warn("[SMP3] JVM pause recording unavailable", e);
				}
				if (!running) {
					if (jvm != null) {
						jvm.close();
						jvm = null;
					}
					return;
				}
				HotspotRecorder data = new HotspotRecorder(seconds);
				server.execute(() -> {
					if (!running)
						return;
					cleanupStart = cleanup.statistics();
					recorder = data;
				});
			} catch (RuntimeException e) {
				failRecording(e);
			}
		});
		if (!queued) {
			occupied = false;
			recipient = null;
		}
		return queued ? 1 : 0;
	}

	private void finish(HotspotRecorder data, ItemCleanupService.Statistics cleanupEnd) {
		long started = System.nanoTime();
		try {
			List<HotspotReport.Pause> pauses = List.of();
			boolean available = jvm != null;
			if (available) {
				try {
					pauses = jvm.finish(data);
				} catch (IOException | RuntimeException e) {
					available = false;
					Smp3Core.LOGGER.warn("[SMP3] JVM pause report unavailable", e);
				} finally {
					jvm.close();
					jvm = null;
				}
			}
			List<Component> lines = new ArrayList<>(HotspotReport.format(data, pauses, minimum, available));
			String details =
			    String.format(Locale.ROOT,
				              "During recording: %d entities / %d items removed.\nCleanup work: %.3f ms total"
			                      + "\nReport preparation: %.3f ms on worker thread",
				              cleanupEnd.removedEntities() - cleanupStart.removedEntities(),
				              cleanupEnd.removedItems() - cleanupStart.removedItems(),
				              (cleanupEnd.processingNanos() - cleanupStart.processingNanos()) / 1e6,
				              (System.nanoTime() - started) / 1e6);
			lines.add(
			    Component
			        .literal(String.format(Locale.ROOT, "Cleanup: %d entities removed | %d loaded",
					                       cleanupEnd.removedEntities() - cleanupStart.removedEntities(),
					                       cleanupEnd.tracked()))
			        .withStyle(ChatFormatting.GRAY)
			        .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(details)))));

			if (!running)
				return;
			server.execute(() -> {
				if (!running)
					return;
				try {
					lines.forEach(line -> send(recipient, line));
				} finally {
					occupied = false;
					recipient = null;
				}
			});
		} catch (RuntimeException e) {
			failRecording(e);
		}
	}

	private void failRecording(Exception error) {
		JvmPauses pauses = jvm;
		if (pauses != null) {
			pauses.close();
			jvm = null;
		}
		if (running)
			server.execute(() -> {
				if (!running)
					return;
				recipient.sendFailure(Component.literal("Recording failed: " + error.getMessage()));
				occupied = false;
				recipient = null;
				recorder = null;
			});
	}

	public int status(CommandSourceStack source) {
		if (config == null) {
			source.sendFailure(Component.literal("Cleanup configuration is not loaded. Use /smp3 perf reload."));
			return 0;
		}
		send(source, Component.literal("Automatic cleanup: ")
		                 .withStyle(ChatFormatting.AQUA)
		                 .append(Component.literal(config.enabled() ? "on" : "off")
		                             .withStyle(config.enabled() ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
		boolean whitelistFirst = config.mode() == WatchdogConfig.Mode.WHITELIST_FIRST;
		send(source,
		     Component.literal("Mode: ")
		         .withStyle(ChatFormatting.GRAY)
		         .append(Component.literal(whitelistFirst ? "whitelist-first" : "blacklist-first")
		                     .withStyle(ChatFormatting.WHITE))
		         .withStyle(style
		                    -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(
		                        whitelistFirst ? "Unlisted items can be cleared. Whitelist overrides blacklist."
								               : "Unlisted items are protected. Blacklist overrides whitelist.")))));
		send(source,
		     Component.literal("Blacklist: " + config.blacklist().size() + " | Whitelist: " + config.whitelist().size())
		         .withStyle(ChatFormatting.GRAY)
		         .withStyle(style
		                    -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(
		                        "Configured item IDs / tags\nBlacklist: " + preview(config.blacklist()) +
		                        "\nWhitelist: " + preview(config.whitelist()))))));
		String rules = config.rules()
		                   .stream()
		                   .limit(3)
		                   .map(rule -> ">=" + rule.entitiesPerChunk() + "/chunk: " + rule.minimumAgeSeconds() + "s")
		                   .collect(Collectors.joining("; "));
		send(source,
		     Component.literal("Rules: ")
		         .withStyle(ChatFormatting.GRAY)
		         .append(Component
		                     .literal(rules +
		                              (config.rules().size() > 3 ? "; +" + (config.rules().size() - 3) + " more" : ""))
		                     .withStyle(ChatFormatting.WHITE))
		         .withStyle(style
		                    -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(
		                        "Dropped entity count per chunk: minimum age in ticking seconds.\nThe highest "
		                        + "reached threshold applies.\nOnly entity-ticking chunks are cleaned.")))));
		return 1;
	}

	private static String preview(List<String> entries) {
		return entries.isEmpty() ? "none"
		                         : entries.stream().limit(8).collect(Collectors.joining(", ")) +
		                               (entries.size() > 8 ? ", +" + (entries.size() - 8) + " more" : "");
	}

	private static void send(CommandSourceStack source, Component line) {
		source.sendSuccess(()
		                       -> !(source.getEntity() instanceof ServerPlayer) &&
		                                  line.getStyle().getHoverEvent() instanceof HoverEvent.ShowText details
		                              ? line.copy().append("\n").append(details.value())
									  : line,
		                   false);
	}

	public int change(CommandSourceStack source, Boolean enabled) {
		source.sendSuccess(
		    () -> Component.literal("Watchdog configuration update queued.").withStyle(ChatFormatting.GRAY), false);
		boolean queued = submit(source, () -> {
			try {
				boolean missing = !Files.exists(file);
				if (!missing && Files.size(file) > 262_144)
					throw new IOException("watchdog.json exceeds 256 KiB");
				String text = missing ? WatchdogConfig.DEFAULTS : Files.readString(file);
				if (enabled != null) {
					var root = JsonParser.parseString(text).getAsJsonObject();
					root.addProperty("enabled", enabled);
					text = new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n";
				}
				WatchdogConfig next = WatchdogConfig.parse(text);
				var policy = server
				                 .submit(() -> {
					                 requireRunning();
					                 return next.resolve();
				                 })
				                 .get();
				if (enabled != null || missing)
					write(text);
				requireRunning();
				server
				    .submit(() -> {
					    if (!running)
						    return;
					    config = next;
					    cleanup.configure(policy);
					    source.sendSuccess(()
						                       -> Component
						                              .literal("Watchdog configuration applied; cleanup " +
						                                       (next.enabled() ? "on" : "off"))
						                              .withStyle(ChatFormatting.GREEN),
						                   false);
				    })
				    .get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				Smp3Core.LOGGER.warn("[SMP3] Watchdog configuration update failed", e);
				if (running)
					server.execute(() -> {
						if (running)
							source.sendFailure(
							    Component.literal("Watchdog configuration unchanged: " + e.getMessage()));
					});
			}
		});
		return queued ? 1 : 0;
	}

	private void write(String text) throws IOException {
		requireRunning();
		Files.createDirectories(file.getParent());
		Path temporary = Files.createTempFile(file.getParent(), "watchdog-", ".tmp");
		try {
			Files.writeString(temporary, text);
			synchronized (commitLock) {
				requireRunning();
				Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private void requireRunning() {
		if (!running || Thread.currentThread().isInterrupted())
			throw new IllegalStateException("Server is stopping");
	}

	private boolean submit(CommandSourceStack source, Runnable task) {
		try {
			worker.execute(task);
			return true;
		} catch (RejectedExecutionException e) {
			source.sendFailure(Component.literal("Watchdog work queue is full or stopping; retry shortly."));
			return false;
		}
	}
}
