package club.sjmc.smp3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import club.sjmc.smp3.command.ShopCommand;
import club.sjmc.smp3.command.SignCommand;
import club.sjmc.smp3.economy.Smp3EconomyProvider;
import club.sjmc.smp3.item.Smp3Items;
import club.sjmc.smp3.shop.ShopCatalog;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * SMP3 核心模组入口。
 *
 * <p>本模组后续还会承载商店之外的功能，因此入口保持为「核心」语义；
 * 目前注册的是商店模块（条目数据 + {@code /shop} 命令）。</p>
 */
public final class Smp3Core implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("SMP3-Core");

	@Override
	public void onInitialize() {
		// 注册本模组的物品（必须在注册表冻结前）
		Smp3Items.init();

		// 条目数据硬编码，这里在注册表就绪后解析物品并建立条目表
		ShopCatalog.init();

		// 向 common-economy-api 注册我方占位货币（验证阶段）
		Smp3EconomyProvider.register();

		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> {
					ShopCommand.register(dispatcher);
					SignCommand.register(dispatcher);
				});

		LOGGER.info("[SMP3] 初始化完成");
	}
}
