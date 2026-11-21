package club.sjmc.smp3.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public final class Smp3Blocks {
	public static final Identifier REACTOR_ID = Identifier.fromNamespaceAndPath("smp3", "nether_reactor_core");
	public static final Block NETHER_REACTOR_CORE =
	    Blocks.register(ResourceKey.create(Registries.BLOCK, REACTOR_ID), NetherReactorCoreBlock::new,
		                BlockBehaviour.Properties.of()
		                    .strength(3)
		                    .sound(SoundType.METAL)
		                    .requiresCorrectToolForDrops()
		                    .pushReaction(PushReaction.IMMOVEABLE));
	private Smp3Blocks() {}
}
