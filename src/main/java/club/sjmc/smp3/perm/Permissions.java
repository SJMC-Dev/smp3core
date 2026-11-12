package club.sjmc.smp3.perm;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;

import club.sjmc.smp3.Smp3Core;

import net.minecraft.server.level.ServerPlayer;

public final class Permissions {
	private static boolean warned;

	private Permissions() {}

	public static User user(ServerPlayer player) {
		return LuckPermsProvider.get().getUserManager().getUser(player.getUUID());
	}

	public static boolean has(ServerPlayer player, String node, boolean warn) {
		try {
			User user = user(player);
			return user != null && user.getCachedData().getPermissionData().checkPermission(node).asBoolean();
		} catch (Throwable e) {
			if (warn && !warned) {
				warned = true;
				Smp3Core.LOGGER.warn("[SMP3] LuckPerms 不可用，全部玩家按普通身份处理：{}", e.toString());
			}
			return false;
		}
	}

	public static void grant(ServerPlayer player, String node) {
		User user = user(player);
		if (user == null) {
			throw new IllegalStateException("LuckPerms player data is not loaded: " + player.getUUID());
		}
		user.data().add(Node.builder(node).value(true).build());
		LuckPermsProvider.get().getUserManager().saveUser(user).join();
	}
}
