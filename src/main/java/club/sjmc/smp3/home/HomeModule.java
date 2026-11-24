package club.sjmc.smp3.home;

import java.util.List;
import java.util.Map;

import com.mojang.brigadier.arguments.StringArgumentType;
import mod.fuji.core.module.ModuleManager;
import mod.fuji.core.structure.GlobalPos;
import mod.fuji.module.initializer.home.HomeInitializer;
import mod.fuji.module.initializer.home.service.HomeService;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;

public final class HomeModule {
	private HomeModule() {}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(HomePayloads.Catalog.TYPE, HomePayloads.Catalog.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(HomePayloads.Request.TYPE, HomePayloads.Request.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(HomePayloads.Request.TYPE,
		                                            (request, context) -> handle(context.player(), request));
	}

	private static boolean available(ServerPlayer player) {
		var node = player.level().getServer().getCommands().getDispatcher().getRoot().getChild("home");
		return ModuleManager.MODULE_INITIALIZER_BY_CLASS.containsKey(HomeInitializer.class) && node != null &&
		    node.canUse(player.createCommandSourceStack());
	}

	public static void refresh(ServerPlayer player) { send(player, ""); }

	private static void send(ServerPlayer player, String message) {
		if (!ServerPlayNetworking.canSend(player, HomePayloads.Catalog.TYPE))
			return;
		boolean enabled = available(player);
		List<HomePayloads.Entry> entries =
		    enabled ? HomeService.withHomeMap(player)
		                  .entrySet()
		                  .stream()
		                  .sorted(Map.Entry.comparingByKey())
		                  .map(entry -> {
			                  var position = entry.getValue().toBlockPos();
			                  return new HomePayloads.Entry(entry.getKey(), entry.getValue().getLevel(),
							                                position.getX(), position.getY(), position.getZ());
		                  })
		                  .toList()
			        : List.of();
		ServerPlayNetworking.send(
		    player, new HomePayloads.Catalog(entries, enabled, enabled ? message : "家园功能未启用或没有访问权限"));
	}

	private static void handle(ServerPlayer player, HomePayloads.Request request) {
		if (!available(player) || request.action() == HomePayloads.Action.REFRESH) {
			refresh(player);
			return;
		}
		String name = request.name(), replacement = request.newName().strip();
		if (request.action() == HomePayloads.Action.CREATE || request.action() == HomePayloads.Action.RENAME) {
			if (replacement.isEmpty() || replacement.length() > 64 ||
			    replacement.codePoints().anyMatch(Character::isISOControl)) {
				send(player, "名称须为 1–64 个字符，不能含换行或控制字符");
				return;
			}
			if (HomeService.withHomeMap(player).containsKey(replacement)) {
				send(player, "已存在同名家园");
				return;
			}
		}
		if (request.action() != HomePayloads.Action.CREATE && !HomeService.withHomeMap(player).containsKey(name)) {
			send(player, "该家园已不存在，请重新选择");
			return;
		}
		String quoted = StringArgumentType.escapeIfRequired(name);
		String newName = StringArgumentType.escapeIfRequired(replacement);
		String command = switch (request.action()) {
			case TELEPORT -> "home tp " + quoted;
			case CREATE -> "home set " + newName;
			case RENAME -> "home rename " + quoted + " " + newName;
			case UPDATE -> "home set " + quoted + " --override true";
			case DELETE -> "home unset " + quoted;
			case REFRESH -> throw new AssertionError();
		};
		var commands = player.level().getServer().getCommands();
		commands.performPrefixedCommand(player.createCommandSourceStack(), command);
		var homes = HomeService.withHomeMap(player);
		String message = switch (request.action()) {
			case CREATE -> homes.containsKey(replacement) ? "家园已创建" : "新建未完成，请查看聊天提示";
			case RENAME ->
				homes.containsKey(replacement) && !homes.containsKey(name) ? "家园已更名"
				                                                           : "更名未完成，请查看聊天提示";
			case UPDATE ->
				GlobalPos.of(player).equals(homes.get(name)) ? "家园位置已更新" : "更新未完成，请查看聊天提示";
			case DELETE -> !homes.containsKey(name) ? "家园已删除" : "删除未完成，请查看聊天提示";
			case TELEPORT -> "已请求传送";
			case REFRESH -> "";
		};
		send(player, message);
	}
}
