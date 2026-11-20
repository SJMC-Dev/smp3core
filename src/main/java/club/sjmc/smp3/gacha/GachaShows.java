package club.sjmc.smp3.gacha;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.random.RandomGenerator;

import club.sjmc.smp3.Smp3Core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;

final class GachaShows {
	private final MinecraftServer server;
	private final BiConsumer<UUID, GachaLedger.Round> reveal;
	private final Map<UUID, Session> sessions = new HashMap<>();
	private final Map<String, GachaPreview> previews = new HashMap<>();
	private final Set<String> invalidPreviews = new HashSet<>();
	private final RandomGenerator random = new Random();
	private GachaConfig config;
	private static final class Session {
		UUID owner;
		GachaLedger.Round round;
		GachaAnimation animation;
		int ticks, animationTicks, resultTicks, actionbarTicks;
		boolean revealed;
	}
	GachaShows(MinecraftServer server, BiConsumer<UUID, GachaLedger.Round> reveal) {
		this.server = server;
		this.reveal = reveal;
	}
	void configure(GachaConfig config) {
		this.config = config;
		previews.values().forEach(GachaPreview::clear);
		previews.clear();
		invalidPreviews.clear();
	}
	boolean busy(UUID owner, String pool) {
		return sessions.values().stream().anyMatch(
		    s -> !s.revealed && (s.owner.equals(owner) || s.round.pool.equals(pool)));
	}
	boolean active(UUID id) {
		var session = sessions.get(id);
		return session != null && !session.revealed;
	}
	void start(UUID owner, GachaLedger.Round round, GachaConfig.Pool pool) {
		sessions.values().removeIf(s -> {
			if (s.revealed && s.round.pool.equals(round.pool)) {
				clear(s);
				return true;
			}
			return false;
		});
		hide(round.pool);
		var session = new Session();
		session.owner = owner;
		session.round = round;
		session.animationTicks = config.animationTicks;
		session.resultTicks = config.resultTicks;
		session.actionbarTicks = config.actionbarTicks;
		sessions.put(round.id, session);
		try {
			if (loaded(pool))
				session.animation = new GachaAnimation(pool.level(server), pool, round.draws.size());
		} catch (RuntimeException error) {
			Smp3Core.LOGGER.warn("Gacha animation unavailable; settlement retained", error);
		}
	}
	void tick() {
		var iterator = sessions.values().iterator();
		while (iterator.hasNext()) {
			var session = iterator.next();
			session.ticks++;
			var player = server.getPlayerList().getPlayer(session.owner);
			if (player != null && session.ticks <= Math.min(session.animationTicks, session.actionbarTicks) &&
			    session.ticks % 20 == 1)
				player.connection.send(new ClientboundSetActionBarTextPacket(
				    Component.literal("开始抽奖，请等待结果……").withStyle(ChatFormatting.GOLD)));
			if (!session.revealed && player != null && session.actionbarTicks > 0 &&
			    session.ticks == session.actionbarTicks + 1)
				player.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
			if (!session.revealed && session.ticks >= session.animationTicks &&
			    (session.ticks - session.animationTicks) % 20 == 0) {
				try {
					reveal.accept(session.owner, session.round);
				} catch (RuntimeException error) {
					if (session.ticks == session.animationTicks || session.ticks % 100 == 0)
						Smp3Core.LOGGER.error("Gacha reveal waiting for settlement {}", session.round.id, error);
					continue;
				}
				session.revealed = true;
				session.ticks = session.animationTicks;
				if (player != null && session.actionbarTicks != 0)
					player.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
				if (session.animation != null)
					try {
						session.animation.reveal(session.round.draws);
					} catch (RuntimeException error) {
						clear(session);
						Smp3Core.LOGGER.warn("Gacha reveal effect failed", error);
					}
			}
			if (session.animation != null) {
				try {
					if (session.animation.displays.stream().anyMatch(
					        d -> d.isRemoved() || !d.level().hasChunkAt(d.blockPosition())))
						clear(session);
					else if (session.revealed)
						session.animation.tickRevealed();
					else
						session.animation.tick(session.ticks, random);
				} catch (RuntimeException error) {
					clear(session);
					Smp3Core.LOGGER.warn("Gacha animation stopped", error);
				}
			}
			if (session.revealed && session.ticks >= session.animationTicks + Math.max(3, session.resultTicks)) {
				clear(session);
				iterator.remove();
			}
		}
		if (config == null)
			return;
		if (server.getTickCount() % 20 == 0)
			updatePreviews();
		previews.values().forEach(GachaPreview::tick);
	}
	private boolean loaded(GachaConfig.Pool pool) {
		var level = pool.level(server);
		return level != null && level.getChunkSource().getChunkNow(Mth.floor(pool.displayX()) >> 4,
		                                                           Mth.floor(pool.displayZ()) >> 4) != null;
	}
	private void updatePreviews() {
		config.pools.forEach((id, pool) -> {
			var level = pool.level(server);
			boolean observed = loaded(pool) &&
			                   level.players().stream().anyMatch(
			                       p -> p.distanceToSqr(pool.displayX(), pool.displayY(1), pool.displayZ()) <= 64 * 64);
			if (!observed || sessions.values().stream().anyMatch(s -> s.round.pool.equals(id))) {
				hide(id);
				return;
			}
			if (invalidPreviews.contains(id))
				return;
			try {
				var preview = previews.get(id);
				if (preview == null || preview.removed()) {
					hide(id);
					preview = new GachaPreview(level, pool);
					if (preview.empty())
						invalidPreviews.add(id);
					else
						previews.put(id, preview);
				}
			} catch (RuntimeException error) {
				hide(id);
				invalidPreviews.add(id);
				Smp3Core.LOGGER.warn("Gacha preview disabled until reload {}", id, error);
			}
		});
	}
	private void hide(String pool) {
		var old = previews.remove(pool);
		if (old != null)
			old.clear();
	}
	private static void clear(Session session) {
		if (session.animation != null) {
			session.animation.clear();
			session.animation = null;
		}
	}
	void clear() {
		sessions.values().forEach(GachaShows::clear);
		sessions.clear();
		previews.values().forEach(GachaPreview::clear);
		previews.clear();
	}
}
