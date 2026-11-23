package club.sjmc.smp3.title.ui;

import java.util.stream.Collectors;

import club.sjmc.smp3.Smp3Core;
import club.sjmc.smp3.title.TitlePayloads;

import mod.fuji.core.auxiliary.minecraft.TextHelper;
import mod.fuji.module.initializer.title.service.TitleService;
import mod.fuji.module.initializer.title.structure.TitleDescriptor;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class TitleView {
	private TitleView() {}

	public static void refresh(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, TitlePayloads.Catalog.TYPE))
			return;
		try {
			var titles = TitleService.getAllTitles();
			var obtained =
			    TitleService.getObtainedTitles(player).stream().map(TitleDescriptor::getId).collect(Collectors.toSet());
			String active = TitleService.getActiveTitle(player).map(TitleDescriptor::getId).orElse("");
			var entries = titles.stream()
			                  .limit(256)
			                  .map(title -> {
				                  var stack = title.toItemStack();
				                  var lore =
				                      TextHelper.getTextListByValue(player, title.getLore()).stream().limit(8).toList();
				                  return new TitlePayloads.Entry(
				                      limit(title.getId(), 128),
				                      limit(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), 128),
				                      TextHelper.getTextByValue(player, title.getDisplayName()), lore,
				                      obtained.contains(title.getId()));
			                  })
			                  .toList();
			ServerPlayNetworking.send(player, new TitlePayloads.Catalog(entries, limit(active, 128)));
		} catch (RuntimeException | LinkageError error) {
			Smp3Core.LOGGER.error("[SMP3] 称号数据不可用 player={}", player.getUUID(), error);
			player.sendSystemMessage(Component.literal("称号数据不可用，请联系管理员"));
		}
	}

	private static String limit(String value, int max) {
		return value.length() <= max ? value : value.substring(0, max);
	}
}
