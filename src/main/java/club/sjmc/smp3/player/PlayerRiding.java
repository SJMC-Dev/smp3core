package club.sjmc.smp3.player;

import club.sjmc.smp3.Smp3Core;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class PlayerRiding {
	private PlayerRiding() {}

	public static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty() ||
			    !(target instanceof Player) || player.isSpectator())
				return InteractionResult.PASS;
			if (level.isClientSide())
				return InteractionResult.SUCCESS;
			return mount((ServerPlayer)player, (ServerPlayer)target) ? InteractionResult.SUCCESS
			                                                         : InteractionResult.FAIL;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> detach(handler.player));
	}

	static boolean mount(ServerPlayer rider, ServerPlayer target) {
		if (rider == target || rider.isPassenger() || rider.isVehicle() || !rider.isAlive() || rider.isSpectator() ||
		    rider.isShiftKeyDown() || !target.isAlive() || target.isSpectator())
			return false;
		Entity base = target;
		while (base.getVehicle() instanceof Player)
			base = base.getVehicle();
		if (base.isPassenger())
			return false;
		Entity top = base;
		int count = 1;
		while (top.getFirstPassenger() instanceof Player passenger) {
			if (passenger.isSpectator() || !passenger.isAlive())
				return false;
			top = passenger;
			count++;
		}
		if (top.isVehicle())
			return false;
		if (count >= Smp3Core.serverConfig().playerStackLimit()) {
			rider.sendOverlayMessage(Component.literal("这组玩家已达到叠坐人数上限"));
			return false;
		}
		return rider.startRiding(top);
	}

	public static void detach(Player player) {
		if (player.getVehicle() instanceof Player)
			player.stopRiding();
		for (Entity passenger : player.getPassengers())
			if (passenger instanceof Player other) {
				detach(other);
			}
	}
}
