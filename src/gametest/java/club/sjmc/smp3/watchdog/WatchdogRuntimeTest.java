package club.sjmc.smp3.watchdog;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import io.netty.channel.embedded.EmbeddedChannel;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.watchdog.cleanup.ItemCleanupService;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class WatchdogRuntimeTest {
	@GameTest(maxTicks = 16000)
	public void workloads(GameTestHelper test) throws Exception {
		if (!Boolean.getBoolean("smp3.watchdog.benchmark")) {
			test.succeed();
			return;
		}
		var run = new Workload(test);
		boolean idle = Boolean.getBoolean("smp3.watchdog.idle");
		ServerTickEvents.START_SERVER_TICK.register(server -> run.measure());
		var sequence = test.startSequence()
		                   .thenIdle(200)
		                   .thenWaitUntil(() -> test.assertTrue(!run.occupied(), "Previous recording pending"))
		                   .thenExecute(run::initialize);
		for (String scenario :
		     System.getProperty("smp3.watchdog.scenarios", "players,mobs,items,redstone,pistons,hoppers,mixed,producer")
		         .split(",")) {
			for (boolean cleanup :
			     scenario.equals("items") || scenario.equals("producer") ? List.of(false, true) : List.of(false)) {
				if (cleanup && idle)
					continue;
				for (int repeat = 0; repeat < (idle ? 4 : 2); repeat++) {
					for (boolean recording : idle          ? List.of(false)
					                         : repeat == 0 ? List.of(false, true)
					                                       : List.of(true, false)) {
						int repetition = repeat;
						sequence.thenExecute(() -> run.prepare(scenario, cleanup, recording, repetition))
						    .thenWaitUntil(() -> test.assertTrue(run.configured(cleanup), "Cleanup toggle pending"))
						    .thenExecute(run::populate)
						    .thenIdle(100)
						    .thenExecute(run::start)
						    .thenWaitUntil(
						        () -> test.assertTrue(!run.profiling || run.recording(), "Recorder startup pending"))
						    .thenExecute(() -> run.measuring = true)
						    .thenWaitUntil(()
							                   -> test.assertTrue(run.ticks.size() >= 100 && !run.occupied(),
							                                      "Measurement pending"))
						    .thenExecute(run::report);
					}
				}
			}
		}
		sequence.thenExecute(run::close).thenSucceed();
		test.onEachTick(run::tick);
	}

	private static final class Workload {
		final GameTestHelper test;
		final ServerLevel level;
		final WatchdogModule module = WatchdogModule.current();
		final Path file = FabricLoader.getInstance().getConfigDir().resolve("smp3/watchdog.json");
		final List<Entity> entities = new ArrayList<>();
		final List<BlockPos> blocks = new ArrayList<>(), clocks = new ArrayList<>(), pistons = new ArrayList<>();
		final List<ServerPlayer> players = new ArrayList<>();
		final List<EmbeddedChannel> channels = new ArrayList<>();
		final List<Long> ticks = new ArrayList<>();
		final List<String> messages = new ArrayList<>();
		final Field occupied, recorder, config, age;
		final ItemCleanupService cleaner;
		final CommandSourceStack console;
		byte[] original;
		String scenario;
		boolean cleanup, profiling, measuring, populated;
		int repeat, generated, workloadTicks;
		long remaining;
		ItemCleanupService.Statistics before, after;

		Workload(GameTestHelper test) throws Exception {
			this.test = test;
			level = test.getLevel();
			occupied = field(WatchdogModule.class, "occupied");
			recorder = field(WatchdogModule.class, "recorder");
			config = field(WatchdogModule.class, "config");
			age = field(ItemEntity.class, "age");
			cleaner = (ItemCleanupService)read(field(WatchdogModule.class, "cleanup"), module);
			console = level.getServer().createCommandSourceStack().withSource(new CommandSource() {
				public void sendSystemMessage(Component message) { messages.add(message.getString()); }
				public boolean acceptsSuccess() { return true; }
				public boolean acceptsFailure() { return true; }
				public boolean shouldInformAdmins() { return false; }
			});
		}

		void initialize() {
			level.getServer().getCommands().performPrefixedCommand(console, "gamerule spawn_mobs false");
			try {
				original = Files.readAllBytes(file);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			for (int x = -8; x <= -4; x++)
				for (int z = -8; z <= -4; z++)
					level.setChunkForced(x, z, true);
			for (int i = 0; i < 8; i++) {
				var player = (ServerPlayer)test.makeMockServerPlayer(GameType.CREATIVE);
				Connection connection = new Connection(PacketFlow.SERVERBOUND);
				channels.add(new EmbeddedChannel(connection));
				level.getServer().getPlayerList().placeNewPlayer(
				    connection, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
				player.setPos(-72 + i, 100, -72);
				player.setNoGravity(true);
				players.add(player);
			}
		}

		void prepare(String scenario, boolean cleanup, boolean profiling, int repeat) {
			clear();
			this.scenario = scenario;
			this.cleanup = cleanup;
			this.profiling = profiling;
			this.repeat = repeat;
			messages.clear();
			generated = 0;
			workloadTicks = 0;
			before = null;
			ticks.clear();
			try {
				Files.writeString(file, WatchdogConfig.DEFAULTS.replace("240", "60").replace("120", "60"));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			module.change(console, cleanup);
		}

		void populate() {
			populated = true;
			if (scenario.equals("mobs") || scenario.equals("mixed"))
				for (int i = 0; i < 192; i++) {
					var mob = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
					mob.setPos(-127 + i % 14, 100 + i / 196, -127 + i / 14);
					mob.setPersistenceRequired();
					mob.setNoGravity(true);
					level.addFreshEntity(mob);
					entities.add(mob);
				}
			if (List.of("items", "producer", "mixed").contains(scenario))
				for (int i = 0; i < 1024; i++)
					addItem();
			if (scenario.equals("hoppers") || scenario.equals("mixed"))
				for (int i = 0; i < 256; i++) {
					BlockPos pos = new BlockPos(-96 + i % 16, 100, -128 + i / 16);
					place(pos, Blocks.HOPPER.defaultBlockState());
					((HopperBlockEntity)level.getBlockEntity(pos)).setItem(0, new ItemStack(Items.STONE, 64));
				}
			if (scenario.equals("redstone") || scenario.equals("mixed"))
				for (int i = 0; i < 128; i++) {
					BlockPos pos = new BlockPos(-128 + i % 8 * 2, 100 + i / 64 * 3, -96 + i / 8 % 8 * 2);
					place(pos.below(), Blocks.STONE.defaultBlockState());
					place(pos, Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.FACING, Direction.NORTH));
					clocks.add(pos);
				}
			if (scenario.equals("pistons"))
				for (int i = 0; i < 64; i++) {
					BlockPos pos = new BlockPos(-96 + i % 4 * 3, 100 + i / 16 * 5, -96 + i / 4 % 4 * 3);
					place(pos.above(2), Blocks.AIR.defaultBlockState());
					place(pos.above(), Blocks.STONE.defaultBlockState());
					place(pos, Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP));
					pistons.add(pos);
				}
		}

		void place(BlockPos pos, BlockState state) {
			level.setBlockAndUpdate(pos, state);
			blocks.add(pos);
		}

		void addItem() {
			int index = generated++;
			var item = new ItemEntity(level, -111.5 + index % 14, 100, -127.5 + index / 14 % 14,
			                          new ItemStack(Items.COBBLESTONE, 64));
			item.setNoGravity(true);
			item.setDeltaMovement(0, 0, 0);
			try {
				age.setInt(item, 2000);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
			level.addFreshEntity(item);
			entities.add(item);
		}

		void start() {
			if (profiling)
				test.assertTrue(module.top(console, 5, 0) == 1, "Benchmark recording rejected");
		}

		// START_SERVER_TICK precedes world updates; the preceding tick's latency and cleanup are complete.
		void measure() {
			if (!measuring)
				return;
			if (before == null) {
				before = cleaner.statistics();
				return;
			}
			var server = level.getServer();
			ticks.add(server.getTickTimesNanos()[(server.getTickCount() - 1) % 100]);
			if (ticks.size() == 100) {
				after = cleaner.statistics();
				remaining = entities.stream().filter(e -> e instanceof ItemEntity && e.isAlive()).count();
				measuring = false;
			}
		}

		void tick() {
			if (!populated)
				return;
			workloadTicks++;
			if (scenario.equals("producer"))
				for (int i = 0; i < 2; i++)
					addItem();
			if (workloadTicks % 2 == 0)
				for (BlockPos pos : clocks) {
					level.setBlockAndUpdate(pos.south(), workloadTicks % 4 == 0
					                                         ? Blocks.REDSTONE_BLOCK.defaultBlockState()
															 : Blocks.AIR.defaultBlockState());
				}
			if (workloadTicks % 4 == 0)
				for (BlockPos pos : pistons)
					level.setBlockAndUpdate(pos.south(), workloadTicks % 8 == 0
					                                         ? Blocks.REDSTONE_BLOCK.defaultBlockState()
															 : Blocks.AIR.defaultBlockState());

			for (ServerPlayer player : players)
				player.doTick();
			for (EmbeddedChannel channel : channels)
				channel.releaseOutbound();
		}

		void report() {
			long[] sorted = ticks.stream().mapToLong(Long::longValue).sorted().toArray();
			String result = String.format(
			    Locale.ROOT,
			    "WATCHDOG BENCH %s cleanup=%s top=%s baseline=%s repeat=%d mean=%.3f p95=%.3f p99=%.3f "
			        + "remaining=%d removed=%d quantity=%d cleanupMs=%.3f",
			    scenario, cleanup, profiling, Boolean.getBoolean("smp3.watchdog.baseline"), repeat,
			    Arrays.stream(sorted).average().orElseThrow() / 1e6, sorted[94] / 1e6, sorted[98] / 1e6, remaining,
			    after.removedEntities() - before.removedEntities(), after.removedItems() - before.removedItems(),
			    (after.processingNanos() - before.processingNanos()) / 1e6);
			Smp3Core.LOGGER.info(result);
			if (profiling) {
				messages.forEach(line -> Smp3Core.LOGGER.info("WATCHDOG REPORT {}: {}", scenario, line));
				String expected = switch (scenario) {
					case "players" -> "minecraft:player";
					case "mobs" -> "minecraft:zombie";
					case "hoppers" -> "minecraft:hopper";
					case "redstone" -> "minecraft:repeater";
					case "pistons" -> "minecraft:sticky_piston) — block event";
					default -> "minecraft:item";
				};
				test.assertTrue(
				    messages.stream().anyMatch(s -> s.contains(expected) && s.contains("X=-") && s.contains("Z=-")),
				    "Missing workload attribution: " + expected);
			}
		}

		boolean occupied() { return (boolean)read(occupied, module); }
		boolean recording() { return read(recorder, module) != null; }
		boolean configured(boolean enabled) {
			return ((WatchdogConfig)read(config, module)).enabled() == enabled &&
			    messages.stream().anyMatch(s -> s.startsWith("Watchdog configuration applied"));
		}

		void clear() {
			measuring = false;
			populated = false;
			entities.forEach(Entity::discard);
			entities.clear();
			clocks.forEach(pos -> level.setBlockAndUpdate(pos.south(), Blocks.AIR.defaultBlockState()));
			clocks.clear();
			pistons.forEach(pos -> level.setBlockAndUpdate(pos.south(), Blocks.AIR.defaultBlockState()));
			pistons.clear();
			for (BlockPos pos : blocks.reversed()) {
				if (level.getBlockEntity(pos) instanceof HopperBlockEntity hopper)
					hopper.clearContent();
				level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			}
			blocks.clear();
		}

		void close() {
			clear();
			players.forEach(player -> level.getServer().getPlayerList().remove(player));
			channels.forEach(EmbeddedChannel::finishAndReleaseAll);
			for (int x = -8; x <= -4; x++)
				for (int z = -8; z <= -4; z++)
					level.setChunkForced(x, z, false);
			try {
				Files.write(file, original);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			module.change(console, null);
		}
	}

	private static Field field(Class<?> type, String name) throws NoSuchFieldException {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}
	private static Object read(Field field, Object target) {
		try {
			return field.get(target);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
}
