package club.sjmc.smp3.ui.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import club.sjmc.smp3.shop.ShopPayloads;
import club.sjmc.smp3.shop.ShopService;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ShopState {
	public final Map<String, ShopPayloads.Node> nodes = new LinkedHashMap<>();
	public final Map<String, List<ShopPayloads.Node>> children = new LinkedHashMap<>();
	public final Map<String, ShopPayloads.Entry> entries = new LinkedHashMap<>();
	private final StringBuilder incoming = new StringBuilder();
	private long incomingSession, closedSession;
	private int part, total;
	public long session, version;
	public String selected = "", message = "";
	public ShopService.Quote quote;
	public boolean pending, pendingOpen;
	private final List<Listener> listeners = new CopyOnWriteArrayList<>();

	public interface Listener {
		default void shopCatalogChanged() {}

		default void shopQuoteChanged() {}
	}

	public void addListener(Listener listener) {
		listeners.remove(listener);
		listeners.add(listener);
	}

	public void removeListener(Listener listener) { listeners.remove(listener); }

	private void notifyShopCatalogChanged() { listeners.forEach(Listener::shopCatalogChanged); }

	private void notifyShopQuoteChanged() { listeners.forEach(Listener::shopQuoteChanged); }

	public void accept(ShopPayloads.Catalog payload) {
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
		for (var node : view.nodes()) {
			nodes.put(node.id(), node);
			children.computeIfAbsent(node.parent(), _ -> new ArrayList<>()).add(node);
			for (var entry : node.entries())
				entries.put(entry.id(), entry);
		}
		session = payload.session();
		version = 0;
		quote = null;
		pending = false;
		message = "";
		if (!entries.containsKey(selected))
			selected = "";
		pendingOpen = payload.open();
		notifyShopCatalogChanged();
	}
	public void accept(ShopPayloads.State payload) {
		if (payload.session() != session || !payload.entry().equals(selected) || payload.version() <= version)
			return;
		version = payload.version();
		quote = payload.quote();
		message = payload.message();
		pending = false;
		notifyShopQuoteChanged();
	}
	public void select(String id) {
		selected = id;
		quote = null;
		pending = true;
		message = "";
		request(ShopPayloads.Action.SELECT, 0);
	}
	public void trade(int batches) {
		if (pending || quote == null || batches < 1 || batches > quote.maximum())
			return;
		pending = true;
		message = "正在交易…";
		request(ShopPayloads.Action.TRADE, batches);
	}
	private void request(ShopPayloads.Action action, int batches) {
		ClientPlayNetworking.send(new ShopPayloads.Request(session, action, selected, version, batches));
	}
	public void close() {
		if (ClientPlayNetworking.canSend(ShopPayloads.Request.TYPE))
			request(ShopPayloads.Action.CLOSE, 0);
		long closing = session;
		clear();
		closedSession = closing;
	}
	public void clear() {
		nodes.clear();
		children.clear();
		entries.clear();
		incoming.setLength(0);
		session = version = incomingSession = closedSession = 0;
		part = total = 0;
		selected = message = "";
		quote = null;
		pending = pendingOpen = false;
	}
}
