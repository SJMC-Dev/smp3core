package club.sjmc.smp3.gacha;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.config.JsonConfig;
import club.sjmc.smp3.item.ItemDefinitions;
import club.sjmc.smp3.item.ItemDelivery;
import club.sjmc.smp3.item.RewardBundle;
import club.sjmc.smp3.item.Smp3Items;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

import static club.sjmc.smp3.gacha.GachaConfig.*;

public final class GachaModule {
	public static final AttachmentType<UUID> TICKET_OWNER =
	    AttachmentRegistry.createPersistent(Identifier.fromNamespaceAndPath("smp3", "ticket_owner"), UUIDUtil.CODEC);
	private static GachaModule instance;
	private final MinecraftServer server;
	private final Path configPath;
	private final GachaFiles files;
	private final RandomGenerator random = new Random();
	private final Map<ItemEntity, Long> tickets = new IdentityHashMap<>();
	private final ArrayDeque<ItemEntity> queue = new ArrayDeque<>();
	private final Map<ServerLevel, Long2ObjectOpenHashMap<List<String>>> regions = new IdentityHashMap<>();
	private final Set<UUID> pending = new HashSet<>(), archiving = new HashSet<>();
	private final GachaShows shows;
	private GachaConfig config;
	private boolean publishing, executingCommand;

	private GachaModule(MinecraftServer server) {
		this.server = server;
		Path directory = FabricLoader.getInstance().getConfigDir().resolve("smp3");
		configPath = directory.resolve("gacha.json");
		files = new GachaFiles(server, directory.resolve("gacha"));
		shows = new GachaShows(server, this::reveal);
	}
	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> instance = new GachaModule(server));
		ServerLifecycleEvents.SERVER_STARTED.register(
		    server -> instance.reload(message -> Smp3Core.LOGGER.info("[Gacha] {}", message)));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			if (success)
				instance.reload(message -> Smp3Core.LOGGER.info("[Gacha] {}", message));
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			instance.shows.clear();
			instance.files.close();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> instance = null);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.isLoadedFromDisk() && entity.entityTags().contains(GachaAnimation.FX_TAG)) {
				entity.discard();
				return;
			}
			if (instance != null && entity instanceof ItemEntity item && draws(item.getItem()) != 0)
				instance.track(item);
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (instance != null && entity instanceof ItemEntity item)
				instance.tickets.remove(item);
		});
		ServerPlayConnectionEvents.JOIN.register(
		    (handler, sender, server) -> instance.pending.add(handler.player.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (instance != null)
				instance.tick();
		});
	}
	public static GachaModule current() {
		if (instance == null)
			throw new IllegalStateException("抽奖模块尚未启动");
		if (instance.executingCommand)
			throw new IllegalStateException("奖励指令不能嵌套调用抽奖指令");
		return instance;
	}
	public GachaConfig config() {
		if (config == null)
			throw new IllegalStateException("抽奖配置不可用，请执行 /smp3 gacha reload");
		return config;
	}
	public static int draws(ItemStack stack) {
		return stack.is(Smp3Items.TICKET_SINGLE) ? 1 : stack.is(Smp3Items.TICKET_TEN) ? 10 : 0;
	}
	public static boolean receiving(ItemEntity item) {
		return instance != null && draws(item.getItem()) != 0 && item.getAttached(TICKET_OWNER) != null &&
		    (instance.config == null || instance.poolAt(item) != null);
	}
	private void track(ItemEntity item) {
		UUID owner = item.getAttached(TICKET_OWNER);
		if (owner != null &&
		    (files.consumed(item.getUUID()) ||
		     GachaLedger.read(owner).data().pending.stream().anyMatch(r -> r.id.equals(item.getUUID())))) {
			item.discard();
			pending.add(owner);
			return;
		}
		if (tickets.putIfAbsent(item, 0L) == null)
			queue.add(item);
	}
	public void reload(Consumer<String> reply) { publish(null, reply); }
	public void edit(Consumer<GachaConfig> edit, Consumer<String> reply) {
		if (publishing) {
			reply.accept("配置正在更新，请稍后重试");
			return;
		}
		var next = config().copy();
		edit.accept(next);
		publish(next, reply);
	}
	private void publish(GachaConfig edited, Consumer<String> reply) {
		if (publishing) {
			reply.accept("配置正在更新，请稍后重试");
			return;
		}
		publishing = true;
		Consumer<Exception> failed = error -> {
			publishing = false;
			reply.accept("抽奖配置未更新：" + error.getMessage());
			Smp3Core.LOGGER.error("Gacha configuration rejected", error);
		};
		files.submit(
		    () -> edited == null ? GachaConfig.parse(JsonConfig.read(configPath, "/gacha.json")) : edited, next -> {
			    try {
				    if (edited == null) {
					    install(next);
					    reply.accept("抽奖配置已重载，进行中的抽奖保持原结果");
				    } else {
					    next.validate(server);
					    files.submit(
					        ()
					            -> {
						        GachaFiles.write(configPath, JSON.toJson(next));
						        return next;
					        },
					        saved
					        -> {
						        try {
							        install(saved);
							        reply.accept("抽奖配置已保存并生效");
						        } catch (RuntimeException error) {
							        failed.accept(error);
						        }
					        },
					        failed);
				    }
			    } catch (RuntimeException error) {
				    failed.accept(error);
			    }
		    }, failed);
	}
	private void install(GachaConfig next) {
		next.validate(server);
		var index = new IdentityHashMap<ServerLevel, Long2ObjectOpenHashMap<List<String>>>();
		next.pools.forEach((id, pool) -> {
			var world = index.computeIfAbsent(pool.level(server), ignored -> new Long2ObjectOpenHashMap<>());
			for (int x = pool.minX >> 4; x <= pool.maxX >> 4; x++)
				for (int z = pool.minZ >> 4; z <= pool.maxZ >> 4; z++)
					world.computeIfAbsent(ChunkPos.pack(x, z), ignored -> new ArrayList<>()).add(id);
		});
		config = next;
		regions.clear();
		regions.putAll(index);
		shows.configure(next);
		publishing = false;
	}
	private String poolAt(ItemEntity item) {
		var world = regions.get(item.level());
		if (world == null)
			return null;
		var ids = world.get(item.chunkPosition().pack());
		if (ids != null)
			for (String id : ids)
				if (config.pools.get(id).contains(item.getX(), item.getY(), item.getZ()))
					return id;
		return null;
	}
	private void tick() {
		if (config == null)
			return;
		int count = Math.min(64, queue.size()), accepted = 0;
		for (int i = 0; i < count; i++) {
			var item = queue.remove();
			Long after = tickets.get(item);
			if (after == null || item.isRemoved()) {
				tickets.remove(item);
				continue;
			}
			queue.add(item);
			UUID owner = item.getAttached(TICKET_OWNER);
			if (owner == null || after > server.getTickCount())
				continue;
			String pool = poolAt(item);
			if (pool == null || !((ServerLevel)item.level()).isPositionEntityTicking(item.blockPosition()))
				continue;
			try {
				accept(item, owner, pool);
			} catch (RuntimeException error) {
				tickets.put(item, server.getTickCount() + 100L);
				Smp3Core.LOGGER.error("Gacha ticket processing failed; item retained {}", item.getUUID(), error);
			}
			if (++accepted == 4)
				break;
		}
		shows.tick();
		if (server.getTickCount() % 20 == 0)
			for (UUID owner : Set.copyOf(pending)) {
				try {
					deliver(owner);
				} catch (RuntimeException error) {
					Smp3Core.LOGGER.error("Gacha settlement pending {}", owner, error);
				}
			}
	}
	private void accept(ItemEntity item, UUID owner, String poolId) {
		var stored = GachaLedger.read(owner);
		if (stored.data().pending.stream().anyMatch(r -> r.id.equals(item.getUUID()))) {
			item.discard();
			pending.add(owner);
			return;
		}
		var player = server.getPlayerList().getPlayer(owner);
		var round = new GachaLedger.Round();
		round.id = item.getUUID();
		round.pool = poolId;
		round.time = System.currentTimeMillis();
		round.playerName = player == null ? owner.toString() : player.getGameProfile().name();
		List<JsonObject> returned = new ArrayList<>();
		boolean refund = player == null || !player.isAlive() || shows.busy(owner, poolId);
		String failure = "抽奖尚未结束，抽奖券已退回";
		if (!refund) {
			try {
				var state = stored.data().pool(poolId);
				var batch =
				    DrawEngine.roll(config.pool(poolId), state.misses, state.obtained, draws(item.getItem()), random);
				var items = new ArrayList<JsonObject>();
				var effects = new ArrayList<JsonObject>();
				int coins = 0, stamps = 0;
				for (var result : batch.results()) {
					List<Bonus> prizes = new ArrayList<>(result.fixedRewards());
					if (result.prize() != null)
						prizes.addFirst(result.prize());
					var labels = new ArrayList<String>();
					var grants = new ArrayList<JsonObject>();
					for (var prize : prizes) {
						grants.addAll(prize.grants);
						labels.add(prize.label);
						prize.commands.forEach(command -> round.commands.add(new GachaLedger.Command(command)));
					}
					var reward = RewardBundle.resolve(grants, random, player);
					if (result.tier() != Tier.B && reward.items().isEmpty() && reward.effects().isEmpty() &&
					    reward.tongbao() == 0 && reward.coffeeStamp() == 0 &&
					    prizes.stream().allMatch(p -> p.commands.isEmpty()))
						throw new IllegalArgumentException("S/A 奖励解析结果为空");
					coins = Math.addExact(coins, reward.tongbao());
					stamps = Math.addExact(stamps, reward.coffeeStamp());
					items.addAll(reward.items());
					effects.addAll(reward.effects());
					round.draws.add(new GachaLedger.Draw(result.tier(), result.prize() == null ? "" : result.prize().id,
					                                     labels.isEmpty() ? "无奖励" : String.join("，", labels),
					                                     result.hardPity(), result.tenGuarantee()));
				}
				round.rewards = new RewardBundle(coins, stamps, items, effects);
				round.rewards.items().forEach(json -> ItemDefinitions.stack(json, player.registryAccess()));
				round.firstDraw = Math.incrementExact(state.total);
				state.total = Math.addExact(state.total, round.draws.size());
				state.misses = batch.missesAfter();
				for (var draw : round.draws)
					if (!draw.prize().isEmpty())
						state.obtained.merge(prizeKey(draw.tier(), draw.prize()), 1L, Math::addExact);
			} catch (RuntimeException error) {
				refund = true;
				stored = GachaLedger.read(owner);
				round.draws.clear();
				round.commands.clear();
				failure = "奖池暂不可用，抽奖券已退回";
				Smp3Core.LOGGER.error("Gacha reward resolution rejected {}", poolId, error);
			}
		}
		int returnedCount = item.getItem().getCount() - (refund ? 0 : 1);
		if (returnedCount > 0)
			returned.add(ItemDefinitions.json(item.getItem().copyWithCount(returnedCount), item.registryAccess()));
		round.delivered = refund;
		stored.data().pending.add(round);
		pending.add(owner);
		if (player != null && player.isAlive())
			GachaLedger.reserve(player, stored, returned);
		else
			GachaLedger.save(owner, stored, returned, List.of(), Map.of());
		item.discard();
		archive(owner, round);
		if (player != null) {
			ItemDelivery.request(player);
			if (refund)
				player.sendSystemMessage(Component.literal(failure));
		}
		if (!refund)
			shows.start(owner, round, config.pool(poolId));
	}
	private void archive(UUID owner, GachaLedger.Round round) {
		if (round.archived || !archiving.add(round.id))
			return;
		var snapshot = JSON.fromJson(JSON.toJsonTree(round), GachaLedger.Round.class);
		files.submit(
		    ()
		        -> {
			    files.archive(owner, snapshot);
			    return round.id;
		    },
		    id
		    -> {
			    archiving.remove(id);
			    try {
				    var stored = GachaLedger.read(owner);
				    stored.round(id).archived = true;
				    GachaLedger.save(owner, stored);
			    } catch (RuntimeException error) {
				    Smp3Core.LOGGER.error("Gacha archive acknowledgement pending {}", id, error);
			    }
		    },
		    error -> {
			    archiving.remove(round.id);
			    Smp3Core.LOGGER.error("Gacha archive pending {}", round.id, error);
		    });
	}
	private boolean settle(UUID owner, UUID id) {
		var stored = GachaLedger.read(owner);
		var round = stored.round(id);
		if (round.delivered)
			return false;
		round.delivered = true;
		var rewards = round.rewards;
		GachaLedger.save(owner, stored, rewards.items(), rewards.effects(), rewards.credits());
		var player = server.getPlayerList().getPlayer(owner);
		if (player != null)
			ItemDelivery.request(player);
		return true;
	}
	private void reveal(UUID owner, GachaLedger.Round round) {
		if (settle(owner, round.id)) {
			try {
				GachaMessages.reveal(server, owner, round);
			} catch (RuntimeException error) {
				Smp3Core.LOGGER.warn("Gacha notification failed {}", round.id, error);
			}
		}
	}
	private void deliver(UUID owner) {
		var data = GachaLedger.read(owner).data();
		for (var round : data.pending) {
			archive(owner, round);
			if (shows.active(round.id))
				continue;
			if (!round.delivered)
				settle(owner, round.id);
			var player = server.getPlayerList().getPlayer(owner);
			if (player == null || !player.isAlive())
				continue;
			for (int i = 0; i < round.commands.size(); i++) {
				var stored = GachaLedger.read(owner);
				var command = stored.round(round.id).commands.get(i);
				if (command.status != GachaLedger.Status.READY)
					continue;
				command.status = GachaLedger.Status.REVIEW;
				GachaLedger.save(owner, stored);
				boolean success = false;
				executingCommand = true;
				try {
					success = GachaCommandRewards.execute(player, command.text);
				} catch (RuntimeException error) {
					Smp3Core.LOGGER.error("Gacha external command needs review {}", round.id, error);
				} finally {
					executingCommand = false;
				}
				if (success) {
					stored = GachaLedger.read(owner);
					stored.round(round.id).commands.get(i).status = GachaLedger.Status.DONE;
					GachaLedger.save(owner, stored);
				} else
					player.sendSystemMessage(Component.literal("抽奖外部奖励待管理员核对，记录 " + round.id));
			}
		}
		if (GachaLedger.read(owner).data().pending.stream().noneMatch(
		        r
		        -> !r.archived || !r.delivered ||
		               r.commands.stream().anyMatch(c -> c.status == GachaLedger.Status.READY)))
			pending.remove(owner);
	}
	public void recover(UUID owner, String pool, UUID id, int action, boolean done) {
		var stored = GachaLedger.read(owner);
		var round = stored.round(id);
		if (!round.pool.equals(pool) || action < 1 || action > round.commands.size())
			throw new IllegalArgumentException("记录或动作序号不匹配");
		var command = round.commands.get(action - 1);
		if (command.status != GachaLedger.Status.REVIEW)
			throw new IllegalArgumentException("只能处理待核对的外部命令");
		command.status = done ? GachaLedger.Status.DONE : GachaLedger.Status.READY;
		GachaLedger.save(owner, stored);
		pending.add(owner);
	}
	public void history(ServerPlayer player, String pool, int page, boolean details) {
		files.submit(() -> files.history(player.getUUID(), pool, page, details ? 5 : 10), result -> {
			var online = server.getPlayerList().getPlayer(player.getUUID());
			if (online != null)
				GachaMessages.history(online, pool, page, result, details);
		}, error -> player.sendSystemMessage(Component.literal("历史读取失败：" + error.getMessage())));
	}
}
