package club.sjmc.smp3.shop;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import club.sjmc.smp3.economy.BalanceSync;
import club.sjmc.smp3.item.ItemDefinitions;
import club.sjmc.smp3.time.ServerDates;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// A screen keeps one current quote. Replacing it invalidates repeated or outdated trade requests.
final class ShopSessions {
	private static final ShopService.Quote UNAVAILABLE = new ShopService.Quote(-1, 0, 0, 0, 0, false, false);
	private static final class Session {
		final long id;
		String selected = "";
		long version;
		ShopService.Quote quote = UNAVAILABLE;
		Session(long id) { this.id = id; }
	}
	private final Map<UUID, Session> viewers = new HashMap<>();
	private long serial;
	private List<String> parts = List.of();

	static List<String> encode(ShopCatalog catalog, RegistryAccess registries) {
		var nodes = new ArrayList<ShopPayloads.Node>();
		catalog.shops().forEach(node -> append(nodes, node, "", catalog.refreshes(), registries));
		String json = ShopPayloads.JSON.toJson(new ShopPayloads.View(nodes));
		var parts = new ArrayList<String>();
		for (int start = 0; start < json.length();) {
			int end = Math.min(json.length(), start + ShopPayloads.PART_SIZE);
			if (end < json.length() && Character.isHighSurrogate(json.charAt(end - 1)))
				end--;
			parts.add(json.substring(start, end));
			start = end;
		}
		if (parts.size() > ShopPayloads.MAX_PARTS)
			throw new IllegalArgumentException("Shop display data exceeds 1 MiB characters");
		return List.copyOf(parts);
	}
	private static void append(List<ShopPayloads.Node> result, ShopNode node, String parent,
	                           Map<String, LocalDate> refreshes, RegistryAccess registries) {
		var entries = new ArrayList<ShopPayloads.Entry>();
		for (ShopEntry entry : node.entries()) {
			ItemStack icon = entry.icon() == entry.template().getItem() ? entry.template().copyWithCount(1)
			                                                            : new ItemStack(entry.icon());
			icon.set(DataComponents.CUSTOM_NAME, entry.name());
			entries.add(new ShopPayloads.Entry(entry.id(), ItemDefinitions.json(icon, registries), entry.description(),
			                                   entry.direction() == TradeDirection.BUY, entry.amount(), entry.price(),
			                                   entry.currency().id().toString(), !entry.title().isEmpty(),
			                                   refreshes.containsKey(entry.id())
			                                       ? "轮换商品，下次刷新 " +
			                                             ServerDates.resetAt(refreshes.get(entry.id()))
			                                                 .format(DateTimeFormatter.ofPattern("MM-dd HH:mm z"))
												   : "常驻商品"));
		}
		result.add(new ShopPayloads.Node(node.id(), parent, node.name(),
		                                 BuiltInRegistries.ITEM.getKey(node.icon()).toString(), entries));
		node.children().forEach(child -> append(result, child, node.id(), refreshes, registries));
	}
	void catalog(MinecraftServer server, List<String> parts) {
		this.parts = parts;
		for (UUID id : List.copyOf(viewers.keySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				Session session = viewers.get(id);
				session.selected = "";
				session.quote = UNAVAILABLE;
				session.version++;
				sendCatalog(player, session);
			}
		}
	}
	boolean open(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, ShopPayloads.Catalog.TYPE))
			return false;
		ShopTitles.deliver(player);
		Session session = new Session(++serial);
		viewers.put(player.getUUID(), session);
		sendCatalog(player, session);
		BalanceSync.subscribe(player, BalanceSync.View.SHOP, true);
		return true;
	}
	private void sendCatalog(ServerPlayer player, Session session) {
		for (int i = 0; i < parts.size(); i++)
			ServerPlayNetworking.send(player, new ShopPayloads.Catalog(session.id, i, parts.size(), parts.get(i)));
	}
	void close(ServerPlayer player) {
		viewers.remove(player.getUUID());
		BalanceSync.subscribe(player, BalanceSync.View.SHOP, false);
	}
	void request(ServerPlayer player, ShopPayloads.Request request) {
		Session session = viewers.get(player.getUUID());
		if (session == null || session.id != request.session())
			return;
		if (request.action() == ShopPayloads.Action.CLOSE) {
			close(player);
			return;
		}
		if (!ShopModule.refresh()) {
			send(player, session, "商店刷新中，请稍后重试");
			return;
		}
		ShopEntry entry = ShopModule.catalog().entries().get(request.entry());
		if (request.action() == ShopPayloads.Action.SELECT) {
			session.selected = entry == null ? "" : entry.id();
			session.quote = quote(player, entry);
			send(player, session, "");
			return;
		}
		if (entry == null) {
			send(player, session, "商品已更新，请重新选择");
			return;
		}
		var next = quote(player, entry);
		if (!session.selected.equals(entry.id()) || request.version() != session.version ||
		    !next.equals(session.quote)) {
			session.quote = quote(player, ShopModule.catalog().entries().get(session.selected));
			send(player, session, "交易条件已变化，请确认数量后重试");
			return;
		}
		var result = ShopService.trade(player, ShopModule.catalog(), entry.id(), request.batches());
		String message = switch (result) {
			case BOUGHT -> "购买成功";
			case SOLD -> "出售成功";
			case NO_MONEY -> "余额不足";
			case QUOTA, LOCKED -> next.weekly() ? "本周额度不足" : "今日额度不足";
			case OWNED -> "已拥有该称号";
			case PENDING -> "称号发放中";
			case NOT_ENOUGH_ITEMS -> "符合要求的物品不足";
			case INVALID_QUANTITY -> "请输入有效批数";
			case STALE -> "商品已更新，请重新选择";
			case FAILED -> "交易失败，请稍后重试";
		};
		session.quote = quote(player, entry);
		send(player, session, message);
		BalanceSync.refresh(player);
	}
	void tick(MinecraftServer server) {
		for (var viewer : viewers.entrySet()) {
			Session session = viewer.getValue();
			if (session.selected.isEmpty())
				continue;
			ServerPlayer player = server.getPlayerList().getPlayer(viewer.getKey());
			if (player == null)
				continue;
			var next = quote(player, ShopModule.catalog().entries().get(session.selected));
			if (!next.equals(session.quote)) {
				session.quote = next;
				send(player, session, "");
			}
		}
	}
	private static ShopService.Quote quote(ServerPlayer player, ShopEntry entry) {
		if (entry == null)
			return UNAVAILABLE;
		try {
			return ShopService.quote(player, entry);
		} catch (RuntimeException error) {
			return UNAVAILABLE;
		}
	}
	private void send(ServerPlayer player, Session session, String message) {
		ServerPlayNetworking.send(
		    player, new ShopPayloads.State(session.id, session.selected, ++session.version, session.quote, message));
	}
}
