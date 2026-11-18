package club.sjmc.smp3.daily;

import java.util.function.Supplier;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

public final class QuestActions {
	private static final ThreadLocal<ServerPlayer> PLAYER = new ThreadLocal<>();
	private static final ThreadLocal<Boolean> BUILDING = ThreadLocal.withInitial(() -> false);
	private static final ThreadLocal<BlockState> CROP = new ThreadLocal<>();
	private QuestActions() {}
	public static ServerPlayer player() { return PLAYER.get(); }
	public static BlockState crop() { return CROP.get(); }
	public static <T> T player(ServerPlayer player, Supplier<T> action) {
		var previous = PLAYER.get();
		PLAYER.set(player);
		try {
			return action.get();
		} finally {
			if (previous == null)
				PLAYER.remove();
			else
				PLAYER.set(previous);
		}
	}
	public static boolean building() { return BUILDING.get(); }
	public static <T> T placing(boolean building, Supplier<T> action) {
		boolean previous = BUILDING.get();
		BUILDING.set(building);
		try {
			return action.get();
		} finally {
			if (previous)
				BUILDING.set(true);
			else
				BUILDING.remove();
		}
	}

	public static void crop(BlockState state, Runnable action) {
		if (PLAYER.get() == null) {
			action.run();
			return;
		}
		var previous = CROP.get();
		CROP.set(state);
		try {
			action.run();
		} finally {
			if (previous == null)
				CROP.remove();
			else
				CROP.set(previous);
		}
	}
}
