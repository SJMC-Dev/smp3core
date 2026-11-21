package club.sjmc.smp3.block;

import club.sjmc.smp3.daily.QuestStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class NetherReactorCoreBlock extends Block {
	public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 2);
	public static final int ACTIVE_TICKS = 900;

	public NetherReactorCoreBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(PHASE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PHASE);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
	                                           BlockHitResult hit) {
		if (state.getValue(PHASE) != 0)
			return InteractionResult.PASS;
		return player instanceof ServerPlayer serverPlayer ? QuestStructures.activate(serverPlayer, pos)
		                                                   : InteractionResult.SUCCESS;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(PHASE) == 1)
			level.setBlockAndUpdate(pos, state.setValue(PHASE, 2));
	}
}
