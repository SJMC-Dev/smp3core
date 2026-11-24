package club.sjmc.smp3.player;

import java.util.ArrayList;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

public final class PlayerRidingChecks {
	public static void run(GameTestHelper test, ServerPlayer base) {
		var server = base.level().getServer();
		var riders = new ArrayList<ServerPlayer>();
		var channels = new ArrayList<EmbeddedChannel>();
		try {
			for (int i = 0; i < 3; i++) {
				var player = new ServerPlayer(server, base.level(), new GameProfile(UUID.randomUUID(), "rider" + i),
				                              ClientInformation.createDefault());
				var connection = new Connection(PacketFlow.SERVERBOUND);
				channels.add(new EmbeddedChannel(connection));
				server.getPlayerList().placeNewPlayer(
				    connection, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
				player.setGameMode(GameType.SURVIVAL);
				player.setPos(base.position());
				riders.add(player);
			}
			var middle = riders.get(0);
			var top = riders.get(1);
			test.assertTrue(PlayerRiding.mount(middle, base) && PlayerRiding.mount(top, base) &&
			                    top.getVehicle() == middle,
			                "Player stack did not use the topmost passenger");
			test.assertTrue(!PlayerRiding.mount(base, top) && !PlayerRiding.mount(riders.get(2), base),
			                "Player stack allowed a cycle or exceeded its limit");
			middle.setGameMode(GameType.SPECTATOR);
			test.assertTrue(!base.isVehicle() && !middle.isPassenger() && !middle.isVehicle() && !top.isPassenger(),
			                "Spectator transition left a player mounted");
			test.assertTrue(!PlayerRiding.mount(riders.get(2), middle), "Spectator accepted a passenger");
			test.assertTrue(PlayerRiding.mount(riders.get(2), base), "Vacated seat could not be reused");
			base.setShiftKeyDown(true);
			base.doTick();
			test.assertTrue(!base.isVehicle(), "Sneaking carrier did not release the passenger");
		} finally {
			base.setShiftKeyDown(false);
			PlayerRiding.detach(base);
			for (var player : riders) {
				PlayerRiding.detach(player);
				server.getPlayerList().remove(player);
			}
			channels.forEach(EmbeddedChannel::finishAndReleaseAll);
		}
	}
}
