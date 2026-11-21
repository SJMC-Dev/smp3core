package club.sjmc.smp3.mixin;

import java.util.Locale;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

import club.sjmc.smp3.daily.QuestConversions;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.cow.MushroomCow;

@Mixin(MushroomCow.class)
public abstract class LightningQuestMixin {
	@WrapMethod(method = "thunderHit")
	private void smp3$lightning(ServerLevel level, LightningBolt bolt, Operation<Void> original) {
		var cow = (Object)this instanceof MushroomCow value ? value : null;
		var variant = cow == null ? null : cow.getVariant();
		original.call(level, bolt);
		if (cow != null && variant != cow.getVariant())
			QuestConversions.converted(cow, cow, cow.getVariant().name().toLowerCase(Locale.ROOT));
	}
}
