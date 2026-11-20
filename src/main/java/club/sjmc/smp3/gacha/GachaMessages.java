package club.sjmc.smp3.gacha;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.UUID;

import club.sjmc.smp3.time.ServerDates;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

final class GachaMessages {
	private GachaMessages() {}
	static ChatFormatting color(GachaConfig.Tier tier) {
		return switch (tier) {
			case S -> ChatFormatting.RED;
			case A -> ChatFormatting.YELLOW;
			case B -> ChatFormatting.BLUE;
		};
	}
	private static MutableComponent draw(GachaLedger.Draw draw, boolean details) {
		var text = Component.literal("[" + draw.tier() + "]" + (details ? " " + draw.label() : " "))
		               .withStyle(color(draw.tier()));
		return text.withStyle(style
		                      -> style.withHoverEvent(new HoverEvent.ShowText(
		                          Component.literal(draw.label() + (draw.hardPity()       ? "（保底）"
								                                    : draw.tenGuarantee() ? "（十连保障）"
								                                                          : "")))));
	}
	static void reveal(MinecraftServer server, UUID owner, GachaLedger.Round round) {
		var player = server.getPlayerList().getPlayer(owner);
		if (player != null) {
			player.sendSystemMessage(Component.literal("抽奖结果 " + round.pool).withStyle(ChatFormatting.GOLD));
			round.draws.forEach(d -> player.sendSystemMessage(draw(d, true)));
		}
		var wins = new LinkedHashMap<String, Integer>();
		round.draws.stream()
		    .filter(d -> d.tier() == GachaConfig.Tier.S)
		    .forEach(d -> wins.merge(d.label(), 1, Integer::sum));
		if (wins.isEmpty())
			return;
		var message = Component.literal("[抽奖] " + round.playerName + " 在 " + round.pool + " 抽中 ")
		                  .withStyle(ChatFormatting.GOLD);
		wins.forEach(
		    (label, count)
		        -> message.append(Component.literal("[S] " + label + (count > 1 ? "（" + count + " 次）" : "") + " ")
				                      .withStyle(ChatFormatting.RED)));
		server.getPlayerList().broadcastSystemMessage(message, false);
		if (player == null)
			return;
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
		player.connection.send(
		    new ClientboundSetSubtitleTextPacket(Component.literal(round.pool).withStyle(ChatFormatting.GOLD)));
		player.connection.send(new ClientboundSetTitleTextPacket(
		    Component.literal("恭喜抽中 S 级！").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
		player.connection.send(new ClientboundSoundPacket(
		    Holder.direct(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE), SoundSource.PLAYERS, player.getX(), player.getY(),
		    player.getZ(), 1f, 1f, server.overworld().getRandom().nextLong()));
	}
	static void history(ServerPlayer player, String pool, int page, GachaFiles.Page result, boolean details) {
		player.sendSystemMessage(
		    Component.literal("抽奖历史 " + pool + "  " + page + "/" + result.pages()).withStyle(ChatFormatting.GOLD));
		var format =
		    DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ServerDates.resetAt(ServerDates.today()).getZone());
		for (var round : result.rounds()) {
			var line = Component.literal(format.format(Instant.ofEpochMilli(round.time())) + "  ")
			               .withStyle(ChatFormatting.GRAY);
			if (details) {
				player.sendSystemMessage(line);
				for (int i = 0; i < round.draws().size(); i++)
					player.sendSystemMessage(
					    Component.literal((round.firstDraw() + i) + ". ").append(draw(round.draws().get(i), true)));
			} else {
				round.draws().forEach(d -> line.append(draw(d, false)));
				player.sendSystemMessage(line);
			}
		}
		var navigation = Component.empty();
		if (page > 1)
			navigation.append(page(pool, page - 1, details, "上一页  "));
		if (page < result.pages())
			navigation.append(page(pool, page + 1, details, "下一页"));
		if (!navigation.getString().isEmpty())
			player.sendSystemMessage(navigation);
	}
	private static Component page(String pool, int page, boolean details, String label) {
		return Component.literal(label).withStyle(
		    style
		    -> style.withColor(ChatFormatting.YELLOW)
		           .withClickEvent(new ClickEvent.RunCommand("/smp3 gacha history " + pool +
				                                             (details ? " details" : "") + " " + page)));
	}
}
