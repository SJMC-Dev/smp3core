package club.sjmc.smp3.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import club.sjmc.smp3.item.ItemDefinitions;
import club.sjmc.smp3.shop.ShopPayloads;
import club.sjmc.smp3.shop.ShopService;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

final class ShopState {
	final Map<String, ShopPayloads.Node> nodes = new LinkedHashMap<>();
	final Map<String, List<ShopPayloads.Node>> children = new LinkedHashMap<>();
	final Map<String, ShopPayloads.Entry> entries = new LinkedHashMap<>();
	final Map<String, ItemStack> icons = new LinkedHashMap<>();
	private final StringBuilder incoming = new StringBuilder();
	private long incomingSession, closedSession;
	private int part, total;
	long session, version;
	String selected = "", message = "";
	ShopService.Quote quote;
	boolean pending, pendingOpen;

	void accept(ShopPayloads.Catalog payload) {
		if (payload.session() <= closedSession)
			return;
		if (payload.total() < 1 || payload.total() > ShopPayloads.MAX_PARTS)
			return;
		if (payload.part() == 0) {
			incoming.setLength(0);
			incomingSession = payload.session();
			part = 0;
			total = payload.total();
		}
		if (payload.session() != incomingSession || payload.part() != part || payload.total() != total)
			return;
		incoming.append(payload.text());
		if (++part != total)
			return;
		var view = ShopPayloads.JSON.fromJson(incoming.toString(), ShopPayloads.View.class);
		incoming.setLength(0);
		nodes.clear();
		children.clear();
		entries.clear();
		icons.clear();
		var registries = Minecraft.getInstance().getConnection().registryAccess();
		for (var node : view.nodes()) {
			nodes.put(node.id(), node);
			children.computeIfAbsent(node.parent(), id -> new ArrayList<>()).add(node);
			for (var entry : node.entries()) {
				entries.put(entry.id(), entry);
				icons.put(entry.id(), ItemDefinitions.stack(entry.icon(), registries));
			}
		}
		session = payload.session();
		version = 0;
		quote = null;
		pending = false;
		message = "";
		if (!entries.containsKey(selected))
			selected = "";
		pendingOpen = payload.open();
		if (Minecraft.getInstance().gui.screen() instanceof MenuScreen menu)
			menu.shopReady();
	}
	void accept(ShopPayloads.State payload) {
		if (payload.session() != session || !payload.entry().equals(selected) || payload.version() <= version)
			return;
		version = payload.version();
		quote = payload.quote();
		message = payload.message();
		pending = false;
		var screen = Minecraft.getInstance().gui.screen();
		if (screen instanceof ShopScreen shop)
			shop.stateChanged();
		else if (screen instanceof MenuScreen menu)
			menu.shopStateChanged();
	}
	void select(String id) {
		selected = id;
		quote = null;
		pending = true;
		message = "";
		request(ShopPayloads.Action.SELECT, 0);
	}
	void trade(int batches) {
		if (pending || quote == null || batches < 1 || batches > quote.maximum())
			return;
		pending = true;
		message = "正在交易…";
		request(ShopPayloads.Action.TRADE, batches);
	}
	private void request(ShopPayloads.Action action, int batches) {
		ClientPlayNetworking.send(new ShopPayloads.Request(session, action, selected, version, batches));
	}
	void close() {
		if (ClientPlayNetworking.canSend(ShopPayloads.Request.TYPE))
			request(ShopPayloads.Action.CLOSE, 0);
		long closing = session;
		clear();
		closedSession = closing;
	}
	void clear() {
		nodes.clear();
		children.clear();
		entries.clear();
		icons.clear();
		incoming.setLength(0);
		session = version = incomingSession = closedSession = 0;
		part = total = 0;
		selected = message = "";
		quote = null;
		pending = pendingOpen = false;
	}
}
