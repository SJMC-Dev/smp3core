package club.sjmc.smp3.notice;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.economy.BalanceSync;
import club.sjmc.smp3.perm.Permissions;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

public final class NoticeModule {
	private static NoticeService instance;
	private NoticeModule() {}
	public static NoticeService current() { return instance; }

	public static boolean mayEdit(ServerPlayer player) {
		return player.createCommandSourceStack().permissions().hasPermission(
		           new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)) ||
		    Permissions.has(player, "smp.notice.admin", false);
	}

	public static void register() {
		BalanceSync.register();
		PayloadTypeRegistry.serverboundPlay().register(NoticePayloads.Delete.TYPE, NoticePayloads.Delete.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(
		    NoticePayloads.Delete.TYPE, (request, context) -> instance.delete(context.player(), request.session()));
		PayloadTypeRegistry.clientboundPlay().register(NoticePayloads.Editor.TYPE, NoticePayloads.Editor.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(NoticePayloads.Publish.TYPE, NoticePayloads.Publish.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(NoticePayloads.Publish.TYPE,
		                                            (request, context) -> instance.publish(context.player(), request));
		PayloadTypeRegistry.clientboundPlay().register(NoticePayloads.Catalog.TYPE, NoticePayloads.Catalog.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(NoticePayloads.Body.TYPE, NoticePayloads.Body.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(NoticePayloads.Request.TYPE, NoticePayloads.Request.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(NoticePayloads.Request.TYPE,
		                                            (request, context) -> instance.receive(context.player(), request));
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			instance =
			    new NoticeService(server, FabricLoader.getInstance().getConfigDir().resolve("smp3/notices.json"));
			instance.reload(message -> Smp3Core.LOGGER.info("[SMP3] {}", message));
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> instance.close());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> instance = null);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> instance.join(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> instance.leave(handler.player.getUUID()));
	}
}
