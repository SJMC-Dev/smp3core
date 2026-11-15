package club.sjmc.smp3.watchdog;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import club.sjmc.smp3.watchdog.cleanup.ItemCleanupService;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

public final class WatchdogGameTest {
	@GameTest(maxTicks = 100000)
	public void commandsAndCleanup(GameTestHelper test) throws Exception {
		if (Boolean.getBoolean("smp3.watchdog.baseline")) {
			test.succeed();
			return;
		}
		var module = WatchdogModule.current();
		var configField = WatchdogModule.class.getDeclaredField("config");
		configField.setAccessible(true);
		var occupied = WatchdogModule.class.getDeclaredField("occupied");
		occupied.setAccessible(true);
		Path file = FabricLoader.getInstance().getConfigDir().resolve("smp3/watchdog.json");
		List<String> messages = new ArrayList<>();
		boolean[] reportRejected = new boolean[1];
		CommandSourceStack console =
		    test.getLevel().getServer().createCommandSourceStack().withSource(new CommandSource() {
			    public void sendSystemMessage(Component message) {
				    messages.add(message.getString());
				    if (message.getString().startsWith("SMP3 top:"))
					    reportRejected[0] =
					        module.top(test.getLevel().getServer().createCommandSourceStack(), 5, 0) == 0;
			    }
			    public boolean acceptsSuccess() { return true; }
			    public boolean acceptsFailure() { return true; }
			    public boolean shouldInformAdmins() { return false; }
		    });
		var dispatcher = test.getLevel().getServer().getCommands().getDispatcher();
		var player = (ServerPlayer)test.makeMockServerPlayer(GameType.SURVIVAL);
		try {
			dispatcher.execute("smp3perf cleanup on", player.createCommandSourceStack());
			throw new AssertionError("Unauthorized watchdog command accepted");
		} catch (CommandSyntaxException expected) {
		}
		List<ItemEntity> items = new ArrayList<>();
		byte[][] original = new byte[1][];
		var writable = new AtomicReference<Set<PosixFilePermission>>();
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			try {
				if (writable.get() != null)
					Files.setPosixFilePermissions(file.getParent(), writable.get());
				if (original[0] != null)
					Files.write(file, original[0]);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		test.startSequence()
		    .thenWaitUntil(
		        () -> test.assertTrue(read(configField, module) != null, "Waiting for initial watchdog config"))
		    .thenExecute(() -> {
			    try {
				    original[0] = Files.readAllBytes(file);
				    Files.writeString(file,
					                  WatchdogConfig.DEFAULTS
					                      .replace("\"blacklist\": []",
					                               "\"blacklist\": [\"minecraft:diamond\", \"minecraft:cobblestone\"]")
					                      .replace("\"whitelist\": []", "\"whitelist\": [\"minecraft:cobblestone\"]")
					                      .replace("128", "2")
					                      .replace("240", "60"));
			    } catch (Exception e) {
				    throw new RuntimeException(e);
			    }
			    module.change(console, true);
		    })
		    .thenWaitUntil(()
			                   -> test.assertTrue(((WatchdogConfig)read(configField, module)).enabled(),
			                                      "Waiting for enabled cleanup"))
		    .thenExecute(() -> {
			    var policy = ((WatchdogConfig)read(configField, module)).resolve();
			    test.assertTrue(!policy.eligible(Items.DIAMOND) && policy.eligible(Items.COBBLESTONE) &&
				                    policy.eligible(Items.STONE),
				                "List precedence changed");
			    var tagged = WatchdogConfig
				                 .parse(WatchdogConfig.DEFAULTS.replace("\"blacklist\": []",
				                                                        "\"blacklist\": [\"#minecraft:planks\"]"))
				                 .resolve();
			    test.assertTrue(!tagged.eligible(Items.OAK_PLANKS) && tagged.eligible(Items.STONE),
				                "Tag resolution changed");
			    test.assertTrue(policy.minimumAge(256) == 2400 && policy.minimumAge(255) == 1200 &&
				                    policy.minimumAge(1) == Integer.MAX_VALUE,
				                "Threshold fallback changed");
			    for (Item item : List.of(Items.DIAMOND, Items.COBBLESTONE, Items.STONE, Items.GOLD_INGOT)) {
				    BlockPos pos = test.absolutePos(new BlockPos(1, 2, 1));
				    ItemEntity entity = new ItemEntity(test.getLevel(), pos.getX() + .5, pos.getY(), pos.getZ() + .5,
					                                   new ItemStack(item, 64));
				    entity.setNoGravity(true);
				    entity.setDeltaMovement(0, 0, 0);
				    if (item == Items.GOLD_INGOT)
					    entity.setUnlimitedLifetime();
				    else if (item != Items.STONE) {
					    try {
						    var age = ItemEntity.class.getDeclaredField("age");
						    age.setAccessible(true);
						    age.setInt(entity, 2000);
					    } catch (Exception e) {
						    throw new RuntimeException(e);
					    }
				    }
				    test.getLevel().addFreshEntity(entity);
				    items.add(entity);
			    }
		    })
		    .thenWaitUntil(() -> test.assertTrue(items.get(1).isRemoved(), "Eligible old item was not cleaned"))
		    .thenExecute(() -> {
			    test.assertTrue(!items.get(0).isRemoved() && !items.get(2).isRemoved() && !items.get(3).isRemoved(),
				                "Cleanup removed protected, young or non-expiring item");
			    module.change(console, false);
			    module.change(console, true);
			    module.change(console, false);
		    })
		    .thenWaitUntil(()
			                   -> test.assertTrue(messages.stream().filter(s -> s.endsWith("cleanup off")).count() == 2,
			                                      "Ordered toggles not complete"))
		    .thenExecute(() -> {
			    try {
				    if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
					    writable.set(Files.getPosixFilePermissions(file.getParent()));
					    Files.setPosixFilePermissions(file.getParent(), PosixFilePermissions.fromString("r-xr-xr-x"));
					    if (Files.isWritable(file.getParent())) {
						    Files.setPosixFilePermissions(file.getParent(), writable.get());
						    writable.set(null);
					    } else
						    module.change(console, true);
				    }
			    } catch (Exception e) {
				    throw new RuntimeException(e);
			    }
		    })
		    .thenWaitUntil(()
			                   -> test.assertTrue(writable.get() == null ||
			                                          messages.stream().anyMatch(
		                                                  s -> s.startsWith("Watchdog configuration unchanged:")),
			                                      "Write failure not reported"))
		    .thenExecute(() -> {
			    try {
				    if (writable.get() != null)
					    Files.setPosixFilePermissions(file.getParent(), writable.get());
				    test.assertTrue(!((WatchdogConfig)read(configField, module)).enabled(),
					                "Write failure changed runtime state");
				    messages.clear();
				    test.assertTrue(!WatchdogConfig.parse(Files.readString(file)).enabled(),
					                "Toggle was not persisted");
				    Files.writeString(file, "{}");
			    } catch (Exception e) {
				    throw new RuntimeException(e);
			    }
			    module.change(console, null);
		    })
		    .thenWaitUntil(()
			                   -> test.assertTrue(
		                           messages.stream().anyMatch(s -> s.startsWith("Watchdog configuration unchanged:")),
		                           "Invalid reload did not report failure"))
		    .thenExecute(() -> {
			    test.assertTrue(!((WatchdogConfig)read(configField, module)).enabled(),
				                "Invalid reload changed runtime state");
			    test.assertTrue(module.top(console, 5, 0) == 1, "Recording failed to start");
			    test.assertTrue(module.top(console.withEntity(player), 5, 0) == 0,
				                "Second source started a conflicting recording");
			    try {
				    dispatcher.execute("seed", console);
			    } catch (CommandSyntaxException e) {
				    throw new RuntimeException(e);
			    }
		    })
		    .thenWaitUntil(() -> test.assertTrue(!(boolean)read(occupied, module), "Recording/report still active"))
		    .thenExecute(() -> {
			    test.assertTrue(messages.stream().anyMatch(s -> s.contains("Dropped item (minecraft:item)")),
				                "Item timing hook/report missing");
			    test.assertTrue(reportRejected[0], "Second recording accepted during report delivery");
			    items.forEach(ItemEntity::discard);
			    var ordered = new ItemCleanupService();
			    ordered.configure(
			        new WatchdogConfig.Policy(true, Set.of(), Set.of(), List.of(new WatchdogConfig.Rule(3, 0))));
			    List<ItemEntity> candidates = new ArrayList<>();
			    for (int i = 0; i < 3; i++) {
				    BlockPos pos = test.absolutePos(new BlockPos(1, 2, 1));
				    var item = new ItemEntity(test.getLevel(), pos.getX(), pos.getY(), pos.getZ(),
					                          new ItemStack(Items.STONE, i == 2 ? 1 : 64));
				    try {
					    var age = ItemEntity.class.getDeclaredField("age");
					    age.setAccessible(true);
					    age.setInt(item, i == 0 ? 0 : 2000);
				    } catch (Exception e) {
					    throw new RuntimeException(e);
				    }
				    test.getLevel().addFreshEntity(item);
				    candidates.add(item);
				    ordered.add(item);
			    }
			    ordered.tick();
			    test.assertTrue(candidates.get(2).isRemoved() && !candidates.get(1).isRemoved() &&
				                    !candidates.get(0).isRemoved(),
				                "Cleanup ordering or threshold recheck changed");
			    candidates.forEach(ItemEntity::discard);
			    ordered.clear();
			    player.discard();
			    try {
				    Files.write(file, original[0]);
			    } catch (Exception e) {
				    throw new RuntimeException(e);
			    }
			    module.change(console, null);
		    })
		    .thenWaitUntil(()
			                   -> test.assertTrue(((WatchdogConfig)read(configField, module)).blacklist().isEmpty(),
			                                      "Config restore pending"))
		    .thenSucceed();
	}

	private static Object read(Field field, Object target) {
		try {
			return field.get(target);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
}
