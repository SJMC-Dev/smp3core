package club.sjmc.smp3.gacha;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import club.sjmc.smp3.economy.Smp3AccountStore;
import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.item.ItemDelivery;
import club.sjmc.smp3.item.Smp3Items;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

public final class GachaChecks {
	public static void run(ClientGameTestContext context, TestSingleplayerContext world) {
		try {
			var reply = new AtomicReference<String>();
			var previous = new AtomicReference<GachaConfig>();
			var ticket = new AtomicReference<ItemEntity>();
			var entitySave = new AtomicReference<CompoundTag>();
			var failedAt = new AtomicInteger();
			var pool = new AtomicReference<GachaConfig.Pool>();
			var original = new AtomicReference<String>();
			var path = FabricLoader.getInstance().getConfigDir().resolve("smp3/gacha.json");
			var owner = world.getServer().computeOnServer(server -> world.getConnection().getServerPlayer().getUUID());
			world.getServer().runOnServer(server -> {
				original.set(Files.readString(path));
				var player = world.getConnection().getServerPlayer();
				previous.set(GachaModule.current().config());
				var rules = previous.get().newPool();
				rules.sProbability = 0;
				rules.aProbability = 0;
				rules.pity = 3;
				var limited = rules.rewards.get(GachaConfig.Tier.S).getFirst();
				limited.limit = 1;
				limited.commands.add("execute if entity @s[tag=smp3_gacha_test] run time query gametime");
				limited.commands.add("execute if entity @s[tag=smp3_gacha_test] run time query gametime");
				var batch = DrawEngine.roll(rules, 0, Map.of(), 10, new Random(1));
				if (batch.results().stream().filter(r -> r.tier() == GachaConfig.Tier.S).count() != 3 ||
				    batch.results().stream().filter(r -> r.tier() == GachaConfig.Tier.S && r.prize() != null).count() !=
				        1 ||
				    batch.results().getLast().tier() != GachaConfig.Tier.A || batch.missesAfter() != 1)
					throw new AssertionError("Gacha pity, last-draw guarantee or personal limit failed");
				if (DrawEngine.roll(rules, 2, Map.of(), 1, new Random(1)).results().getFirst().tier() !=
				    GachaConfig.Tier.S)
					throw new AssertionError("Hard pity boundary failed");
				if (!GachaCommandRewards.execute(player, "time query gametime"))
					throw new AssertionError("Fuji replaced the external reward callback");
				var pos = player.blockPosition().offset(0, 0, 3);
				rules.dimension = player.level().dimension().identifier().toString();
				rules.minX = rules.maxX = pos.getX();
				rules.minY = rules.maxY = pos.getY();
				rules.minZ = rules.maxZ = pos.getZ();
				pool.set(rules);
				GachaModule.current().edit(config -> {
					config.pools.clear();
					config.pools.put("check", rules);
					config.animationTicks = 100;
				}, reply::set);
			});
			context.waitFor(client -> reply.get() != null);
			world.getServer().runOnServer(server -> {
				if (!GachaModule.current().config().pools.containsKey("check"))
					throw new AssertionError(reply.get());
				var player = world.getConnection().getServerPlayer();
				Smp3AccountStore.setBalance(owner, Smp3Currency.TONGBAO, 0);
				Smp3AccountStore.setBalance(owner, Smp3Currency.COFFEE_STAMP, 0);
				for (int i = 0; i < 36; i++)
					player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
				var p = pool.get();
				var item = new ItemEntity(player.level(), p.minX + .5, p.minY + .5, p.minZ + .5,
				                          new ItemStack(Smp3Items.TICKET_TEN, 2));
				item.setNoGravity(true);
				item.setDeltaMovement(Vec3.ZERO);
				item.setNeverPickUp();
				item.setThrower(player);
				var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.registryAccess());
				item.saveWithoutId(saved);
				entitySave.set(saved.buildResult());
				Files.createDirectory(path.resolveSibling("accounts").resolve(owner + ".json.tmp"));
				failedAt.set(server.getTickCount());
				player.level().addFreshEntity(item);
				ticket.set(item);
			});
			world.getServer().waitFor(server -> server.getTickCount() > failedAt.get() + 3);
			world.getServer().runOnServer(server -> {
				if (ticket.get().isRemoved() || GachaLedger.read(owner).data().pool("check").total != 0)
					throw new AssertionError("Failed account save consumed the ticket or advanced counters");
				Files.delete(path.resolveSibling("accounts").resolve(owner + ".json.tmp"));
			});
			world.getServer().waitFor(server -> GachaLedger.read(owner).data().pool("check").total == 10);
			context.runOnClient(client -> {
				client.player.setYRot(0);
				client.player.setXRot(-20);
			});
			context.waitTicks(5);
			context.takeScreenshot("gacha-ten-draw");
			reply.set(null);
			world.getServer().runOnServer(server -> {
				if (!ticket.get().isRemoved())
					throw new AssertionError("Accepted ticket still exists");
				var saved = GachaLedger.read(owner)
				                .data()
				                .pending.stream()
				                .filter(r -> r.id.equals(ticket.get().getUUID()))
				                .findFirst()
				                .orElseThrow();
				if (saved.delivered)
					throw new AssertionError("Rewards delivered before reveal");
				GachaModule.current().edit(config -> config.pools.clear(), reply::set);
			});
			context.waitFor(client -> reply.get() != null);
			world.getServer().waitFor(server -> Smp3AccountStore.balance(owner, Smp3Currency.COFFEE_STAMP) == 6000);
			world.getServer().runOnServer(server -> {
				var player = world.getConnection().getServerPlayer();
				var state = GachaLedger.read(owner).data().pool("check");
				if (state.total != 10 || state.misses != 1 || state.obtained.get("S/mace") != 1 ||
				    Smp3AccountStore.balance(owner, Smp3Currency.TONGBAO) != 12000)
					throw new AssertionError("Reload changed an accepted draw");
				var pending = ItemDelivery.pending(player);
				if (pending.stream().filter(i -> i.is(Smp3Items.TICKET_TEN)).mapToInt(ItemStack::getCount).sum() != 1 ||
				    pending.stream().filter(i -> i.is(Items.MACE)).mapToInt(ItemStack::getCount).sum() != 1)
					throw new AssertionError("Full inventory lost reward or remainder ticket");
				player.getInventory().clearContent();
				ItemDelivery.deliver(player);
				if (!ItemDelivery.pending(player).isEmpty())
					throw new AssertionError("Queued gacha items did not deliver");
				Files.writeString(path, "{invalid");
				reply.set(null);
				GachaModule.current().reload(reply::set);
			});
			context.waitFor(client -> reply.get() != null);
			world.getServer().runOnServer(server -> {
				if (!GachaModule.current().config().pools.isEmpty())
					throw new AssertionError("Invalid reload replaced active config");
				Files.writeString(path, original.get());
				reply.set(null);
				GachaModule.current().reload(reply::set);
			});
			context.waitFor(client -> reply.get() != null);
			world.getServer().waitFor(server -> {
				var commands = GachaLedger.read(owner).round(ticket.get().getUUID()).commands;
				return commands.stream().allMatch(c -> c.status == GachaLedger.Status.REVIEW);
			});
			world.getServer().runOnServer(server -> {
				world.getConnection().getServerPlayer().addTag("smp3_gacha_test");
				GachaModule.current().recover(owner, "check", ticket.get().getUUID(), 1, false);
				GachaModule.current().recover(owner, "check", ticket.get().getUUID(), 2, true);
			});
			world.getServer().waitFor(server -> GachaLedger.read(owner).data().pending.isEmpty());
			world.getServer().runOnServer(server -> {
				world.getConnection().getServerPlayer().removeTag("smp3_gacha_test");
				if (Smp3AccountStore.balance(owner, Smp3Currency.COFFEE_STAMP) != 6000)
					throw new AssertionError("External command recovery repeated native rewards");
			});
			world.getServer().runOnServer(server -> {
				var player = world.getConnection().getServerPlayer();
				var replay = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(),
				                            new ItemStack(Smp3Items.TICKET_TEN, 2));
				replay.load(
				    TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), entitySave.get()));
				player.level().addFreshEntity(replay);
				if (!replay.isRemoved() || GachaLedger.read(owner).data().pool("check").total != 10)
					throw new AssertionError("Restored consumed ticket was not rejected after history archival");
			});
		} catch (IOException error) {
			throw new UncheckedIOException(error);
		}
	}
}
