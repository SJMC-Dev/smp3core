package club.sjmc.smp3.daily;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import club.sjmc.smp3.block.NetherReactorCoreBlock;
import club.sjmc.smp3.block.Smp3Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;

public final class QuestStructures {
	private static final Map<GlobalPos, ConduitCheck> conduits = new HashMap<>();
	private record ConduitCheck(UUID player, long after) {}
	private QuestStructures() {}
	static void tick(MinecraftServer server) {
		var iterator = conduits.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			if (server.getTickCount() < entry.getValue().after)
				continue;
			iterator.remove();
			var location = entry.getKey();
			var level = server.getLevel(location.dimension());
			var player = server.getPlayerList().getPlayer(entry.getValue().player);
			if (player != null && level != null && level.hasChunkAt(location.pos()) &&
			    level.getBlockEntity(location.pos()) instanceof ConduitBlockEntity conduit && conduit.isActive())
				DailyQuestEvents.emit(player, "conduit_activated",
				                      DailyQuestEvents.context(player, level, location.pos()));
		}
	}
	public static void placed(ServerPlayer player, BlockPos at) {
		var level = player.level();
		var block = level.getBlockState(at);
		if (!(block.is(Blocks.CONDUIT) || block.is(Blocks.PRISMARINE) || block.is(Blocks.PRISMARINE_BRICKS) ||
		      block.is(Blocks.DARK_PRISMARINE) || block.is(Blocks.SEA_LANTERN)))
			return;
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-2, -2, -2), at.offset(2, 2, 2))) {
			if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof ConduitBlockEntity conduit &&
			    !conduit.isActive())
				conduits.put(GlobalPos.of(level.dimension(), pos.immutable()),
				             new ConduitCheck(player.getUUID(), level.getServer().getTickCount() + 45));
		}
	}

	static void forget(UUID player) { conduits.values().removeIf(check -> check.player.equals(player)); }
	static void clear() { conduits.clear(); }

	public static InteractionResult activate(ServerPlayer player, BlockPos core) {
		if (!player.isAlive() || player.isCreative() || player.isSpectator() ||
		    !DailyQuestEvents.active(player, "reactor_activated"))
			return InteractionResult.PASS;
		var level = player.level();
		var state = level.getBlockState(core);
		if (!state.is(Smp3Blocks.NETHER_REACTOR_CORE) || state.getValue(NetherReactorCoreBlock.PHASE) != 0)
			return InteractionResult.PASS;
		var positions = new ArrayList<BlockPos>(18);
		for (int y = -1; y <= 1; y++)
			for (int x = -1; x <= 1; x++)
				for (int z = -1; z <= 1; z++) {
					int distance = Math.abs(x) + Math.abs(z);
					Block expected = y == -1  ? (distance == 2 ? Blocks.GOLD_BLOCK : Blocks.COBBLESTONE)
					                 : y == 0 ? (distance == 0   ? Smp3Blocks.NETHER_REACTOR_CORE
					                             : distance == 2 ? Blocks.COBBLESTONE
					                                             : Blocks.AIR)
					                          : (distance < 2 ? Blocks.COBBLESTONE : Blocks.AIR);
					var pos = core.offset(x, y, z);
					if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(expected)) {
						player.sendSystemMessage(Component.literal("下界反应器结构不完整"));
						return InteractionResult.SUCCESS;
					}
					if (expected != Blocks.AIR && !pos.equals(core))
						positions.add(pos);
				}
		positions.forEach(pos -> level.setBlockAndUpdate(pos, Blocks.NETHERRACK.defaultBlockState()));
		level.setBlockAndUpdate(core, state.setValue(NetherReactorCoreBlock.PHASE, 1));
		level.scheduleTick(core, Smp3Blocks.NETHER_REACTOR_CORE, NetherReactorCoreBlock.ACTIVE_TICKS);
		level.levelEvent(2001, core, Block.getId(Blocks.NETHERRACK.defaultBlockState()));
		DailyQuestEvents.emit(player, "reactor_activated", DailyQuestEvents.context(player, level, core));
		return InteractionResult.SUCCESS;
	}
}
