package club.sjmc.smp3.home;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.home.HomePayloads.Action;
import club.sjmc.smp3.home.HomeStore.Location;
import club.sjmc.smp3.perm.Permissions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

final class HomeService {
	record Result(boolean success, String message) {}
	private final HomeStore store;

	HomeService(HomeStore store) { this.store = store; }

	static boolean available(ServerPlayer player) {
		return Permissions.has(player, "smp.home.use", true, true) ||
		    Permissions.isAdmin(player.createCommandSourceStack(), "smp.home.use");
	}

	List<HomePayloads.Entry> entries(ServerPlayer player) throws IOException {
		return store.homes(player.getUUID())
		    .entrySet()
		    .stream()
		    .sorted(Map.Entry.comparingByKey())
		    .map(entry -> {
			    var home = entry.getValue();
			    var pos = BlockPos.containing(home.x(), home.y(), home.z());
			    return new HomePayloads.Entry(entry.getKey(), home.dimension(), pos.getX(), pos.getY(), pos.getZ());
		    })
		    .toList();
	}

	Result apply(ServerPlayer player, HomePayloads.Request request) throws IOException {
		if (!available(player))
			return new Result(false, "没有家园访问权限");
		if (request.action() == Action.REFRESH)
			return new Result(true, "");
		var homes = new HashMap<>(store.homes(player.getUUID()));
		String name = request.name(), replacement = request.newName().strip();
		if (request.action() == Action.TELEPORT && name.isEmpty()) {
			if (homes.isEmpty())
				return new Result(false, "尚未设置家园，请使用 /sethome <名称>");
			name = homes.size() == 1 ? homes.keySet().iterator().next() : "home";
			if (!homes.containsKey(name))
				return new Result(false, "有多个家园，请使用 /home <名称> 选择");
		}
		if (request.action() == Action.CREATE || request.action() == Action.RENAME) {
			if (replacement.isEmpty() || replacement.length() > 64 ||
			    replacement.codePoints().anyMatch(Character::isISOControl))
				return new Result(false, "名称须为 1–64 个字符，不能含换行或控制字符");
			if (homes.containsKey(replacement))
				return new Result(false, "已存在同名家园");
		}
		if (request.action() != Action.CREATE && !homes.containsKey(name))
			return new Result(false, "该家园已不存在，请重新选择");
		if (request.action() == Action.TELEPORT)
			return teleport(player, homes.get(name));
		int limit = Smp3Core.serverConfig().homeLimit();
		if (request.action() == Action.CREATE && homes.size() >= limit)
			return new Result(false, "家园数量已达上限（" + limit + "）");
		String message = switch (request.action()) {
			case CREATE -> {
				homes.put(replacement, location(player));
				yield "家园已创建";
			}
			case RENAME -> {
				homes.put(replacement, homes.remove(name));
				yield "家园已更名";
			}
			case UPDATE -> {
				homes.put(name, location(player));
				yield "家园位置已更新";
			}
			case DELETE -> {
				homes.remove(name);
				yield "家园已删除";
			}
			case REFRESH, TELEPORT -> throw new AssertionError();
		};
		store.save(player.getUUID(), homes);
		return new Result(true, message);
	}

	private static Location location(ServerPlayer player) {
		return new Location(player.level().dimension().identifier().toString(), player.getX(), player.getY(),
		                    player.getZ(), player.getYRot(), player.getXRot());
	}

	private static Result teleport(ServerPlayer player, Location home) {
		var level = player.level().getServer().getLevel(
		    ResourceKey.create(Registries.DIMENSION, Identifier.parse(home.dimension())));
		if (level == null)
			return new Result(false, "家园所在维度不可用");
		if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(home.x(), home.y(), home.z())))
			return new Result(false, "家园位置位于世界边界之外");
		boolean success =
		    player.teleportTo(level, home.x(), home.y(), home.z(), Set.of(), home.yaw(), home.pitch(), true);
		return new Result(success, success ? "已传送到家园" : "当前无法传送");
	}
}
