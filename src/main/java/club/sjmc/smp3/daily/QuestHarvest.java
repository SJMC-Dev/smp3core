package club.sjmc.smp3.daily;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gamerules.GameRules;

public final class QuestHarvest {
	private static final Set<String> CROPS =
	    Set.of("wheat", "potatoes", "carrots", "beetroots", "melon", "pumpkin", "sweet_berry_bush", "sugar_cane",
		       "kelp", "kelp_plant", "nether_wart", "cave_vines", "cave_vines_plant", "cocoa", "cactus", "pitcher_crop",
		       "pitcher_plant", "torchflower", "torchflower_crop", "sea_pickle");
	private QuestHarvest() {}
	public static void harvest(Level level, BlockPos pos, ItemStack stack) {
		var player = QuestActions.player();
		if (player == null || level != player.level() || !player.level().getGameRules().get(GameRules.BLOCK_DROPS) ||
		    stack.isEmpty() || !DailyQuestEvents.active(player, "crop_harvested"))
			return;
		BlockState state = QuestActions.crop();
		if (state == null)
			state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof CropBlock) &&
		    !CROPS.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath()))
			return;
		if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state))
			return;
		if (state.is(Blocks.NETHER_WART) && state.getValue(BlockStateProperties.AGE_3) < 3 ||
		    state.is(Blocks.COCOA) && state.getValue(BlockStateProperties.AGE_2) < 2 ||
		    state.is(Blocks.PITCHER_CROP) && state.getValue(BlockStateProperties.AGE_4) < 4)
			return;
		String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (item.endsWith("_seeds") || item.equals("poisonous_potato"))
			return;
		var facts = DailyQuestEvents.context(player, player.level(), pos);
		DailyQuestEvents.blockFacts(facts, state);
		DailyQuestEvents.target(facts, "item", BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()));
		DailyQuestEvents.emit(player, new QuestDefinition.Event("crop_harvested", facts, stack.getCount()));
	}
}
