package club.sjmc.smp3.sign;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.perm.Permissions;
import club.sjmc.smp3.time.ServerDates;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SignService {
	public enum Status { DATE_CHANGED, SIGNED, ALREADY_SIGNED, SAVE_FAILED, REVIEW, RETRY, MILESTONE_GRANTED }

	public record Outcome(Status status, int days, Map<Smp3Currency, Integer> rewards, String month, int rank) {
		Outcome(Status status) { this(status, 0, Map.of(), "", 0); }
		Outcome(Status status, int days, Map<Smp3Currency, Integer> rewards, String month) {
			this(status, days, rewards, month, 0);
		}
	}

	private SignService() {}
	private record TitleKey(UUID player, String claim, int delivery) {}
	private static final Map<TitleKey, CompletableFuture<Void>> titles = new HashMap<>();
	static void clear() { titles.clear(); }
	static void flushTitles(MinecraftServer server) {
		if (titles.isEmpty())
			return;
		var ready = titles.entrySet()
		                .stream()
		                .filter(e -> e.getValue().isDone())
		                .map(e -> e.getKey().player())
		                .distinct()
		                .toList();
		for (UUID id : ready) {
			var player = server.getPlayerList().getPlayer(id);
			if (player == null)
				titles.keySet().removeIf(key -> key.player().equals(id));
			else
				reconcile(player);
		}
	}
	public static void reconcile(ServerPlayer player) {
		try {
			var next = SignData.copy(state(player));
			boolean changed = next.queueMilestones(SignModule.config());
			changed |= next.queueMissingTitles(SignModule.config(),
			                                   title -> Permissions.has(player, "fuji.title.obtain." + title, true));
			if (changed)
				commit(player, next, Map.of());
			deliverPending(player, ServerDates.today(), new ArrayList<>());
		} catch (RuntimeException error) {
			titles.keySet().removeIf(key -> key.player().equals(player.getUUID()));
			Smp3Core.LOGGER.error("Check-in rewards pending for {}", player.getUUID(), error);
		}
	}

	public static PlayerSignData state(ServerPlayer player) { return state(player, ServerDates.today()); }

	private static PlayerSignData state(ServerPlayer player, LocalDate today) {
		SignModule.config();
		SignData data = SignData.get(player.level().getServer());
		PlayerSignData state = data.of(player.getUUID());
		PlayerSignData next = SignData.copy(state);
		if (next.rotateMonth(today)) {
			commit(player, next, Map.of());
			return next;
		}
		return state;
	}

	public static List<Outcome> sign(ServerPlayer player, LocalDate shownDate) {
		List<Outcome> outcomes = new ArrayList<>();
		try {
			LocalDate today = ServerDates.today();
			PlayerSignData state = state(player, today);
			if (!today.equals(shownDate) || today.toEpochDay() < state.lastSignEpochDay()) {
				return List.of(new Outcome(Status.DATE_CHANGED));
			}
			if (today.toEpochDay() > state.lastSignEpochDay()) {
				PlayerSignData next = SignData.copy(state);
				next.sign(today);
				next.dailyRank(SignData.get(player.level().getServer()).nextRank(today.toEpochDay()));
				var config = SignModule.config();
				var reward = config.dailyRewards(next.streak());
				commit(player, next, reward);
				outcomes.add(new Outcome(Status.SIGNED, next.streak(), reward, "", next.dailyRank()));
				Smp3Core.LOGGER.info(
				    "[SMP3] 签到：player={}({}) streak={} reward={} monthTotal={} serverTotal={}",
				    player.getName().getString(), player.getUUID(), next.streak(),
				    reward.entrySet().stream().map(entry -> entry.getKey().id() + "=" + entry.getValue()).toList(),
				    next.monthTotal(), next.serverTotal());
			} else if (!state.hasPendingRewards()) {
				return List.of(new Outcome(Status.ALREADY_SIGNED));
			}
			deliverPending(player, today, outcomes);
		} catch (RuntimeException e) {
			Smp3Core.LOGGER.error("[SMP3] 签到提交失败 player={}", player.getUUID(), e);
			outcomes.add(new Outcome(Status.SAVE_FAILED));
		}
		return outcomes;
	}

	private static void deliverPending(ServerPlayer player, LocalDate today, List<Outcome> outcomes) {
		PlayerSignData state = SignData.copy(state(player, today));
		for (var claim : List.copyOf(state.pending)) {
			Map<Smp3Currency, Integer> credits = new HashMap<>();
			for (var delivery : claim.deliveries) {
				if (delivery.status == PlayerSignData.Status.READY &&
				    delivery.reward.kind() == SignReward.Kind.CURRENCY) {
					credits.merge(Smp3Currency.byId(delivery.reward.value()), delivery.reward.amount(), Math::addExact);
					delivery.status = PlayerSignData.Status.DONE;
				}
			}
			if (!credits.isEmpty()) {
				commit(player, state, credits);
			}
			for (var delivery : claim.deliveries) {
				if (delivery.status == PlayerSignData.Status.DONE) {
					continue;
				}
				if (delivery.reward.kind() == SignReward.Kind.TITLE) {
					var key = new TitleKey(player.getUUID(), claim.id, claim.deliveries.indexOf(delivery));
					var future = titles.get(key);
					if (future == null) {
						delivery.status = PlayerSignData.Status.IN_FLIGHT;
						commit(player, state, Map.of());
						future = Permissions.grant(player, "fuji.title.obtain." + delivery.reward.value());
						titles.put(key, future);
					}
					if (!future.isDone())
						continue;
					try {
						future.join();
						delivery.status = PlayerSignData.Status.DONE;
					} catch (RuntimeException error) {
						delivery.status = PlayerSignData.Status.READY;
						outcomes.add(new Outcome(Status.RETRY, claim.days, Map.of(), claim.month));
						Smp3Core.LOGGER.error("Check-in title pending for {}", player.getUUID(), error);
					}
					titles.remove(key);
					commit(player, state, Map.of());
					continue;
				}
				if (delivery.status == PlayerSignData.Status.IN_FLIGHT) {
					outcomes.add(new Outcome(Status.REVIEW, claim.days, Map.of(), claim.month));
					continue;
				}
				// Persist intent before external effects; interrupted deliveries must not be repeated blindly.
				delivery.status = PlayerSignData.Status.IN_FLIGHT;
				commit(player, state, Map.of());
				var result = SignRewardGranter.grant(player, delivery.reward);
				delivery.status = switch (result) {
					case SUCCESS -> PlayerSignData.Status.DONE;
					case RETRY -> PlayerSignData.Status.READY;
					case REVIEW -> PlayerSignData.Status.IN_FLIGHT;
				};
				commit(player, state, Map.of());
				if (result != SignRewardGranter.Result.SUCCESS) {
					outcomes.add(new Outcome(result == SignRewardGranter.Result.RETRY ? Status.RETRY : Status.REVIEW,
					                         claim.days, Map.of(), claim.month));
				}
			}
			if (claim.deliveries.stream().allMatch(d -> d.status == PlayerSignData.Status.DONE)) {
				state.finish(claim);
				commit(player, state, Map.of());
				outcomes.add(new Outcome(Status.MILESTONE_GRANTED, claim.days, Map.of(), claim.month));
			}
		}
	}

	private static void commit(ServerPlayer player, PlayerSignData state, Map<Smp3Currency, Integer> credits) {
		SignData data = SignData.get(player.level().getServer());
		data.commit(player.getUUID(), state.receipt, state, credits);
	}
}
