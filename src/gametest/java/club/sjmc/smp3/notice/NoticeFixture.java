package club.sjmc.smp3.notice;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

public final class NoticeFixture {
	public static final Path INDEX = FabricLoader.getInstance().getConfigDir().resolve("smp3/notices.json");
	public static void install() {
		try {
			Path root = INDEX.getParent().resolve("notices");
			Files.createDirectories(root);
			Files.writeString(root.resolve("event.md"), """
                # 周末活动

                一起建设**公共花园**。

                ## 活动安排
                ### 准备材料
                #### 分工说明
                - 带上花卉和木材。
                - 与其他玩家商量分工。

                使用 `/smp3 dailyquest` 查看今日任务。
                [活动指南](https://example.com/event)
                """);
			Files.writeString(root.resolve("rules.txt"), "尊重其他玩家及其劳动成果。\n\n离开前关闭高频红石设备。");
			Files.writeString(INDEX, """
                {"title":"社区公告与指南","categories":[{"id":"news","name":"公告"},{"id":"rules","name":"服规"},{"id":"help","name":"帮助"}],"entries":[
                  {"id":"event","category":"news","title":"周末活动","date":"2026-11-17","file":"event.md"},
                  {"id":"older","category":"rules","title":"设施使用规则","file":"rules.txt","acknowledgement":true,"pinned":true},
                  {"id":"rules","category":"rules","title":"社区规则","date":"2026-11-16","file":"rules.txt","acknowledgement":true,"notify":false,"pinned":true}
                ]}
                """);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static void join(ServerPlayer player) {
		NoticeModule.current().leave(player.getUUID());
		NoticeModule.current().join(player);
	}
}
