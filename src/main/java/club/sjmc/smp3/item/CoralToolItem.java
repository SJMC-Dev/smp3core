package club.sjmc.smp3.item;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;

public final class CoralToolItem extends Item {
	private static final Map<Block, Block> PAIRS = new HashMap<>();
	static {
		for (String species : new String[] {"tube", "brain", "bubble", "fire", "horn"})
			for (String shape : new String[] {"coral", "coral_fan", "coral_wall_fan", "coral_block"}) {
				var living = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(species + "_" + shape));
				var dead =
				    BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("dead_" + species + "_" + shape));
				PAIRS.put(living, dead);
				PAIRS.put(dead, living);
			}
	}
	public CoralToolItem(Properties properties) { super(properties); }

	@Override
	public InteractionResult useOn(UseOnContext context) {
		var level = context.getLevel();
		var pos = context.getClickedPos();
		var state = level.getBlockState(pos);
		var replacement = PAIRS.get(state.getBlock());
		if (replacement == null)
			return InteractionResult.PASS;
		var player = context.getPlayer();
		var tool = context.getItemInHand();
		if (player == null || player.isSpectator() || !level.mayInteract(player, pos) ||
		    !player.mayUseItemAt(pos, context.getClickedFace(), tool))
			return InteractionResult.FAIL;
		var next = replacement.defaultBlockState().withPropertiesOf(state);
		boolean reviving = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("dead_");
		boolean water =
		    state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
		for (var direction : Direction.values())
			water |= level.getFluidState(pos.relative(direction)).is(FluidTags.WATER);
		if (reviving && (!water || !next.canSurvive(level, pos))) {
			if (player instanceof ServerPlayer serverPlayer)
				serverPlayer.sendSystemMessage(Component.translatable("item.smp3.coral_tool.needs_water"));
			return InteractionResult.FAIL;
		}
		if (player instanceof ServerPlayer serverPlayer && level.setBlockAndUpdate(pos, next)) {
			tool.hurtAndBreak(1, serverPlayer, context.getHand());
			level.playSound(null, pos, SoundEvents.AXE_SCRAPE.value(), SoundSource.BLOCKS, 0.7f,
			                reviving ? 1.2f : 0.8f);
			level.gameEvent(serverPlayer, GameEvent.BLOCK_CHANGE, pos);
		}
		return InteractionResult.SUCCESS;
	}
}
