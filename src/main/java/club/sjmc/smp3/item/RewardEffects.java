package club.sjmc.smp3.item;

import java.util.UUID;

import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

final class RewardEffects {
	private RewardEffects() {}
	static BlockPos position(ServerPlayer player, EntityType<?> type) {
		var level = player.level();
		for (BlockPos pos :
		     BlockPos.betweenClosed(player.blockPosition().offset(-4, -2, -4), player.blockPosition().offset(4, 2, 4)))
			if (suitable(level, pos, type))
				return pos.immutable();
		return null;
	}
	private static boolean suitable(ServerLevel level, BlockPos pos, EntityType<?> type) {
		if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.offset(1, 0, 1)) ||
		    !level.hasChunkAt(pos.offset(-1, 0, -1)) || !level.getWorldBorder().isWithinBounds(pos))
			return false;
		boolean aquatic =
		    type.getCategory() == MobCategory.WATER_CREATURE || type.getCategory() == MobCategory.WATER_AMBIENT ||
		    type.getCategory() == MobCategory.UNDERGROUND_WATER_CREATURE || type.getCategory() == MobCategory.AXOLOTLS;
		if (aquatic
		        ? !level.getFluidState(pos).is(FluidTags.WATER) || !level.getFluidState(pos.above()).is(FluidTags.WATER)
				: !level.getFluidState(pos).isEmpty() ||
				      !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP))
			return false;
		return level.noCollision(type.getDimensions().makeBoundingBox(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
	}
	static boolean spawn(ServerPlayer player, EntityType<?> type, JsonObject effect) {
		var level = player.level().getServer().getLevel(
		    ResourceKey.create(Registries.DIMENSION, Identifier.parse(effect.get("dimension").getAsString())));
		var pos = BlockPos.of(effect.get("position").getAsLong());
		UUID id = UUID.fromString(effect.get("uuid").getAsString());
		if (level == null || !level.hasChunkAt(pos))
			return false;
		if (level.getEntity(id) != null)
			return true;
		if (!suitable(level, pos, type))
			return false;
		var entity = type.create(level, null, pos, EntitySpawnReason.EVENT, false, false);
		if (entity == null)
			throw new IllegalArgumentException("Entity reward cannot be created: " + type);
		entity.setUUID(id);
		entity.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
		return level.addFreshEntity(entity);
	}
}
