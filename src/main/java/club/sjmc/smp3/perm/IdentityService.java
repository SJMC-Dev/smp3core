package club.sjmc.smp3.perm;

import club.sjmc.smp3.Smp3Core;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.util.Tristate;
import net.minecraft.server.level.ServerPlayer;

/**
 * 身份判定：是否拥有进阶节点 {@value #ADVANCED_NODE}。
 *
 * <p>通过 LuckPerms 的 {@code CachedData} 实时查询，不缓存玩家组，权限变更后下一次交易立即生效。
 * 未安装 LuckPerms 时降级为普通玩家并告警一次。</p>
 */
public final class IdentityService {

	/** 进阶玩家节点。 */
	public static final String ADVANCED_NODE = "smp.shop.advanced";

	private static boolean warned;

	private IdentityService() {
	}

	public static boolean isAdvanced(ServerPlayer player) {
		try {
			LuckPerms luckPerms = LuckPermsProvider.get();
			User user = luckPerms.getUserManager().getUser(player.getUUID());
			if (user == null) {
				return false;
			}
			Tristate result = user.getCachedData().getPermissionData().checkPermission(ADVANCED_NODE);
			return result.asBoolean();
		} catch (Throwable t) {
			if (!warned) {
				warned = true;
				Smp3Core.LOGGER.warn("[SMP3] LuckPerms 不可用，全部玩家按普通身份处理：{}", t.toString());
			}
			return false;
		}
	}
}
