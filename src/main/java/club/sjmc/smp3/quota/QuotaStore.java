package club.sjmc.smp3.quota;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import club.sjmc.smp3.shop.ShopEntry;
import net.minecraft.server.level.ServerPlayer;

/**
 * 每日额度计数（只存内存，服务器重启后清零）。
 *
 * <p>采用「读取即重置」：每次读取额度时比较记录日期与服务器本地日期，
 * 当前日期晚于记录日期则清空该玩家的全部计数；服务器时间回拨（当前日期早于记录日期）不做重置。
 * 不依赖定时任务，跨天后的第一次读取就是重置点。</p>
 */
public final class QuotaStore {

	private static final Map<UUID, PlayerState> STATES = new HashMap<>();

	private QuotaStore() {
	}

	/** 某玩家某条目今日已交易的物品个数（读取时完成跨天重置）。 */
	public static int used(ServerPlayer player, ShopEntry entry) {
		return stateFor(player.getUUID()).usedItems.getOrDefault(entry.id(), 0);
	}

	/** 记账：累加物品个数。 */
	public static void add(ServerPlayer player, ShopEntry entry, int items) {
		PlayerState state = stateFor(player.getUUID());
		state.usedItems.merge(entry.id(), items, Integer::sum);
	}

	private static PlayerState stateFor(UUID playerId) {
		LocalDate today = LocalDate.now();
		PlayerState state = STATES.get(playerId);
		if (state == null) {
			state = new PlayerState(today);
			STATES.put(playerId, state);
			return state;
		}
		if (today.isAfter(state.date)) {
			state.usedItems.clear();
			state.date = today;
		}
		return state;
	}

	private static final class PlayerState {
		private LocalDate date;
		private final Map<String, Integer> usedItems = new HashMap<>();

		private PlayerState(LocalDate date) {
			this.date = date;
		}
	}
}
