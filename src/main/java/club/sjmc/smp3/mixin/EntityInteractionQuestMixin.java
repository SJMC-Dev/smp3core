package club.sjmc.smp3.mixin;

import java.util.Set;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

import club.sjmc.smp3.daily.DailyQuestEvents;
import club.sjmc.smp3.daily.QuestActions;
import club.sjmc.smp3.daily.QuestDefinition;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

@Mixin(Mob.class)
public abstract class EntityInteractionQuestMixin {
	@WrapMethod(method = "interact")
	private InteractionResult smp3$interact(Player actor, InteractionHand hand, Vec3 hit,
	                                        Operation<InteractionResult> original) {
		if (!(actor instanceof ServerPlayer player))
			return original.call(actor, hand, hit);
		var mob = (Mob)(Object)this;
		float before = mob.getHealth();
		var result = QuestActions.player(player, () -> original.call(actor, hand, hit));
		boolean repair = mob.getType() == EntityTypes.IRON_GOLEM && mob.getHealth() > before;
		boolean talk =
		    mob instanceof Villager villager &&
		    villager.getVillagerData().profession().unwrapKey().orElseThrow().identifier().getPath().equals("nitwit");
		if (repair || talk) {
			var facts = DailyQuestEvents.context(player, player.level(), mob.blockPosition());
			DailyQuestEvents.target(facts, "entity", BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(mob.getType()));
			if (mob instanceof Villager villager)
				DailyQuestEvents.target(facts, "profession", villager.getVillagerData().profession());
			facts.put("action", Set.of(repair ? "repair" : "talk"));
			DailyQuestEvents.emit(player,
			                      new QuestDefinition.Event("entity_interacted", facts,
			                                                repair ? Math.max(1, (int)(mob.getHealth() - before)) : 1));
		}
		return result;
	}
}
