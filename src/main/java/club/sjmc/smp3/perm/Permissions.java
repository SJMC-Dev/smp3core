package club.sjmc.smp3.perm;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;

import club.sjmc.smp3.Smp3Core;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

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

	public static boolean isAdmin(CommandSourceStack source, String node) {
		return source.getEntity() == null ||
		    source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)) ||
		    source.getEntity() instanceof ServerPlayer player && has(player, node, true);
	}
	public static CompletableFuture<Void> grant(ServerPlayer player, String node) {
		return grant(player, List.of(node));
	}
	public static CompletableFuture<Void> grant(ServerPlayer player, Collection<String> nodes) {
		try {
			User user = user(player);
			if (user == null)
				throw new IllegalStateException("LuckPerms player data is not loaded: " + player.getUUID());
			nodes.forEach(node -> user.data().add(Node.builder(node).value(true).build()));
			return LuckPermsProvider.get().getUserManager().saveUser(user);
		} catch (RuntimeException | LinkageError error) {
			return CompletableFuture.failedFuture(error);
		}
	}
}
