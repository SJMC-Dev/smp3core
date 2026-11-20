package club.sjmc.smp3.gacha;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import club.sjmc.smp3.Smp3Core;

import net.minecraft.server.MinecraftServer;

import static club.sjmc.smp3.gacha.GachaConfig.JSON;

final class GachaFiles implements AutoCloseable {
	final Path directory;
	private final MinecraftServer server;
	private final ExecutorService worker =
	    Executors.newSingleThreadExecutor(Thread.ofPlatform().name("smp3-gacha-io").factory());
	GachaFiles(MinecraftServer server, Path directory) {
		this.server = server;
		this.directory = directory;
	}
	@FunctionalInterface
	interface Read<T> {
		T run() throws Exception;
	}
	<T> void submit(Read<T> read, Consumer<T> success, Consumer<Exception> failure) {
		worker.execute(() -> {
			try {
				T result = read.run();
				server.execute(() -> success.accept(result));
			} catch (Exception e) {
				server.execute(() -> failure.accept(e));
			}
		});
	}
	boolean consumed(UUID id) { return Files.exists(directory.resolve("consumed").resolve(id.toString())); }
	void archive(UUID owner, GachaLedger.Round round) throws IOException {
		Path receipt = directory.resolve("consumed").resolve(round.id.toString());
		// Retain consumption independently of browsable history and pending account state.
		if (!Files.exists(receipt))
			write(receipt, owner.toString());
		if (!round.draws.isEmpty())
			write(history(owner, round.pool).resolve(String.format("%020d-%s.json", round.firstDraw, round.id)),
			      JSON.toJson(new GachaLedger.History(round.id, round.time, round.firstDraw, round.draws, round.rewards,
			                                          round.commands.stream().map(c -> c.text).toList())));
	}
	private Path history(UUID owner, String pool) {
		if (!pool.matches("[A-Za-z0-9_-]{1,32}"))
			throw new IllegalArgumentException("Invalid pool ID");
		return directory.resolve("history").resolve(owner.toString()).resolve(pool);
	}
	record Page(List<GachaLedger.History> rounds, long pages) {}
	Page history(UUID owner, String pool, int page, int size) throws IOException {
		Path path = history(owner, pool);
		if (!Files.isDirectory(path))
			return new Page(List.of(), 1);
		try (var stream = Files.list(path)) {
			var files = stream.filter(p -> p.getFileName().toString().endsWith(".json"))
			                .sorted(Comparator.reverseOrder())
			                .toList();
			long pages = Math.max(1, (files.size() + (long)size - 1) / size);
			if (page > pages)
				throw new IllegalArgumentException("页码超出范围，末页为 " + pages);
			var results = new ArrayList<GachaLedger.History>();
			for (var file : files.subList((page - 1) * size, Math.min(page * size, files.size())))
				results.add(JSON.fromJson(Files.readString(file), GachaLedger.History.class));
			return new Page(List.copyOf(results), pages);
		}
	}
	static void write(Path file, String text) throws IOException {
		Files.createDirectories(file.getParent());
		Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(temporary, text);
		Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
	}
	@Override
	public void close() {
		worker.shutdown();
		try {
			if (!worker.awaitTermination(10, TimeUnit.SECONDS))
				Smp3Core.LOGGER.warn("Gacha file writes are still finishing");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
