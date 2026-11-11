package club.sjmc.smp3.loot;

import java.util.List;

import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.sign.SignReward;
import club.sjmc.smp3.sign.SignRewardGranter;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * 拦截命名空间为 {@code dailyquests} 的战利品表：不产出原掉落，
 * 改为给触发它的玩家发放 10 通宝 + 1 咖啡印章。
 */
public final class DailyQuestsLootHandler {

	private static final String NAMESPACE = "dailyquests";
	private static final int TONGBAO_REWARD = 10;
	private static final int COFFEE_STAMP_REWARD = 1;

	private DailyQuestsLootHandler() {
	}

	/** 注册拦截；调用前必须确认 Fuji 已加载（补偿依赖 common-economy-api）。 */
	public static void register() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			ResourceKey<LootTable> key = table.unwrapKey().orElse(null);
			if (key == null) {
				return;
			}
			Identifier id = key.identifier();
			if (!NAMESPACE.equals(id.getNamespace())) {
				return;
			}

			// 阻止原战利品表产出
			drops.clear();

			Entity entity = context.getOptional(LootContextParams.THIS_ENTITY);
			if (entity instanceof ServerPlayer player) {
				SignRewardGranter.grant(player, List.of(
						new SignReward.Currency(Smp3Currency.TONGBAO, TONGBAO_REWARD),
						new SignReward.Currency(Smp3Currency.COFFEE_STAMP, COFFEE_STAMP_REWARD)));
			}
		});
	}
}
