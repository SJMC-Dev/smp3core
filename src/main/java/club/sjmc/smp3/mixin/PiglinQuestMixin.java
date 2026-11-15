package club.sjmc.smp3.mixin;

import java.util.List;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import club.sjmc.smp3.daily.BarterAttribution;
import club.sjmc.smp3.daily.DailyQuestEvents;
import club.sjmc.smp3.daily.QuestEventType;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Mixin(PiglinAi.class)
public abstract class PiglinQuestMixin {

	@Shadow
	private static void throwItems(Piglin piglin, List<ItemStack> items) {
		throw new AssertionError();
	}

	@Inject(method = "mobInteract", at = @At("RETURN"))
	private static void smp3$offered(ServerLevel level, Piglin piglin, Player player, InteractionHand hand,
	                                 CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.getReturnValue() == InteractionResult.SUCCESS && player instanceof ServerPlayer &&
		    !player.isCreative() && !player.isSpectator())
			piglin.setAttached(BarterAttribution.OWNER, player.getUUID());
	}

	@Inject(method = "pickUpItem",
	        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/piglin/"
	                                            +
	                                            "PiglinAi;admireGoldItem(Lnet/minecraft/world/entity/LivingEntity;)V"))
	private static void
	smp3$accepted(ServerLevel level, Piglin piglin, ItemEntity item, CallbackInfo ci) {
		UUID owner = item.getAttached(BarterAttribution.OWNER);
		if (piglin.isAdult() && piglin.getOffhandItem().is(Items.GOLD_INGOT) && owner != null)
			piglin.setAttached(BarterAttribution.OWNER, owner);
	}

	@Inject(method = "cancelAdmiring", at = @At("TAIL"))
	private static void smp3$clear(ServerLevel level, Piglin piglin, CallbackInfo ci) {
		piglin.removeAttached(BarterAttribution.OWNER);
	}

	@Inject(method = "holdInOffhand", at = @At("TAIL"))
	private static void smp3$newItem(ServerLevel level, Piglin piglin, ItemStack stack, CallbackInfo ci) {
		piglin.removeAttached(BarterAttribution.OWNER);
	}

	@Inject(method = "stopHoldingOffHandItem", at = @At("TAIL"))
	private static void smp3$stopped(ServerLevel level, Piglin piglin, boolean barter, CallbackInfo ci) {
		piglin.removeAttached(BarterAttribution.OWNER);
	}

	@Redirect(method = "stopHoldingOffHandItem",
	          at = @At(value = "INVOKE",
	                   target = "Lnet/minecraft/world/entity/monster/piglin/PiglinAi;throwItems(Lnet/minecraft/world/"
	                            + "entity/monster/piglin/Piglin;Ljava/util/List;)V"))
	private static void
	smp3$completed(Piglin piglin, List<ItemStack> items) {
		throwItems(piglin, items);
		UUID customer = piglin.removeAttached(BarterAttribution.OWNER);
		if (customer == null)
			return;
		ServerPlayer player = piglin.level().getServer().getPlayerList().getPlayer(customer);
		if (player != null)
			DailyQuestEvents.items(player, QuestEventType.PIGLIN_BARTER.id(), piglin, items);
	}
}
