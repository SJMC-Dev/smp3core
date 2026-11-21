package club.sjmc.smp3.daily;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class QuestStructures {
	private static final Map<GlobalPos, ConduitCheck> conduits = new HashMap<>();
	private record ConduitCheck(UUID player, long after) {}
	/** 下界反应器方块的放置者，用于把任务记在建结构的人身上。 */
	private static final Map<GlobalPos, Placed> placements = new HashMap<>();
	private record Placed(UUID player, long at) {}
	private static final long PLACEMENT_MEMORY = 12000L;
	private QuestStructures() {}
	static void register() {
		UseBlockCallback.EVENT.register((actor, level, hand, hit) -> {
			if (!(actor instanceof ServerPlayer player))
				return InteractionResult.PASS;
			ItemStack held = player.getItemInHand(hand);
			if (level.getBlockState(hit.getBlockPos()).is(Blocks.DIAMOND_BLOCK)) {
				ServerPlayer credited = builder(level, hit.getBlockPos());
				if (credited == null || !DailyQuestEvents.active(credited, "reactor_activated"))
					credited = DailyQuestEvents.active(player, "reactor_activated") ? player : null;
				if (credited != null)
					return reactor(credited, player, hit.getBlockPos(),
					               player.isShiftKeyDown() && held.isEmpty());
			}
			return InteractionResult.PASS;
		});
	}

	private static boolean reactorMaterial(Block block) {
		return block == Blocks.DIAMOND_BLOCK || block == Blocks.GOLD_BLOCK || block == Blocks.COBBLESTONE;
	}

	/** 结构里每个位置该放什么方块，纯几何，不读世界。 */
	private static Block reactorBlock(int x, int y, int z) {
		int distance = Math.abs(x) + Math.abs(z);
		if (y == -1)
			return distance == 2 ? Blocks.GOLD_BLOCK : Blocks.COBBLESTONE;
		if (y == 0)
			return distance == 0 ? Blocks.DIAMOND_BLOCK : distance == 2 ? Blocks.COBBLESTONE : Blocks.AIR;
		if (y == 1)
			return distance < 2 ? Blocks.COBBLESTONE : Blocks.AIR;
		return Blocks.AIR;
	}

	private static List<BlockPos> reactorBlocks(BlockPos core) {
		var positions = new ArrayList<BlockPos>(13);
		for (int y = -1; y <= 1; y++)
			for (int x = -1; x <= 1; x++)
				for (int z = -1; z <= 1; z++)
					if (reactorBlock(x, y, z) != Blocks.AIR)
						positions.add(core.offset(x, y, z));
		return positions;
	}

	/** 谁建的就记给谁：整个结构里放置方块最多的那个人，平手取更近的一次。 */
	private static ServerPlayer builder(Level level, BlockPos core) {
		var server = level.getServer();
		if (server == null)
			return null;
		long now = server.getTickCount();
		var counts = new HashMap<UUID, Integer>();
		var latest = new HashMap<UUID, Long>();
		for (BlockPos pos : reactorBlocks(core)) {
			var placed = placements.get(GlobalPos.of(level.dimension(), pos));
			if (placed == null || now - placed.at > PLACEMENT_MEMORY)
				continue;
			counts.merge(placed.player, 1, Integer::sum);
			latest.merge(placed.player, placed.at, Math::max);
		}
		UUID best = null;
		int bestCount = 0;
		long bestAt = Long.MIN_VALUE;
		for (var entry : counts.entrySet()) {
			long at = latest.getOrDefault(entry.getKey(), 0L);
			if (entry.getValue() > bestCount || entry.getValue() == bestCount && at > bestAt) {
				best = entry.getKey();
				bestCount = entry.getValue();
				bestAt = at;
			}
		}
		return best == null ? null : server.getPlayerList().getPlayer(best);
	}
	static void tick(MinecraftServer server) {
		placements.entrySet().removeIf(entry -> server.getTickCount() - entry.getValue().at > PLACEMENT_MEMORY);
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
		if (reactorMaterial(block.getBlock()))
			placements.put(GlobalPos.of(level.dimension(), at.immutable()),
			               new Placed(player.getUUID(), level.getServer().getTickCount()));
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

	static void forget(UUID player) {
		conduits.values().removeIf(check -> check.player.equals(player));
		placements.values().removeIf(placed -> placed.player.equals(player));
	}
	static void clear() {
		conduits.clear();
		placements.clear();
	}
	private static InteractionResult reactor(ServerPlayer credited, ServerPlayer actor, BlockPos core, boolean hint) {
		var level = actor.level();
		var positions = new HashMap<BlockPos, BlockState>();
		for (int y = -1; y <= 1; y++)
			for (int x = -1; x <= 1; x++)
				for (int z = -1; z <= 1; z++) {
					Block block = reactorBlock(x, y, z);
					var pos = core.offset(x, y, z);
					if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(block)) {
						if (hint)
							actor.sendSystemMessage(Component.literal("下界反应器结构不完整"));
						return hint ? InteractionResult.SUCCESS : InteractionResult.PASS;
					}
					if (block != Blocks.AIR)
						positions.put(pos, Blocks.NETHERRACK.defaultBlockState());
				}
		positions.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
		level.levelEvent(2001, core, Block.getId(Blocks.NETHERRACK.defaultBlockState()));
		DailyQuestEvents.emit(credited, "reactor_activated", DailyQuestEvents.context(credited, level, core));
		return InteractionResult.SUCCESS;
	}
}
