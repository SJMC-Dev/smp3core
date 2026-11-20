package club.sjmc.smp3.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;

import club.sjmc.smp3.shop.ShopPayloads;
import club.sjmc.smp3.economy.Smp3Currency;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

final class ShopScreen extends Screen {
	private final ShopState state = Smp3Client.SHOP;
	private final Set<String> expanded = new HashSet<>();
	private final Map<String, Integer> pages = new HashMap<>();
	private final Map<String, String> selections = new HashMap<>();
	private List<String> path = List.of();
	private record TreeRow(ShopPayloads.Node node, int depth, boolean children) {}
	private final List<TreeRow> tree = new ArrayList<>();
	private final List<PaperButton> breadcrumbs = new ArrayList<>();
	private int x, y, panelWidth, panelHeight, sidebar, contentLeft, contentWidth, bodyTop, bodyBottom, footer,
	    pageSize, rowHeight, treeScroll, treeBottom, headerExtra;
	private String current = "", quantity = "1";
	private boolean narrow;
	private EditBox batches;
	private PaperButton submit, minus, plus, maximum;
	private long lastFrame = System.nanoTime();

	ShopScreen() { super(Component.literal("商店")); }
	void catalogChanged() {
		expanded.retainAll(state.nodes.keySet());
		pages.keySet().retainAll(state.nodes.keySet());
		selections.keySet().retainAll(state.nodes.keySet());
		if (!state.nodes.containsKey(current)) {
			current = "";
			for (String id : path)
				if (state.nodes.containsKey(id))
					current = id;
		}
		selectNode(current, false);
	}
	@Override
	protected void init() {
		x = Math.max(6, (width - 650) / 2);
		y = Math.max(6, (height - 390) / 2);
		panelWidth = width - 2 * x;
		panelHeight = height - 2 * y;
		narrow = panelWidth < 440;
		sidebar = narrow ? panelWidth - 32 : Math.min(160, panelWidth / 3);
		contentLeft = narrow ? x + 18 : x + sidebar + 24;
		contentWidth = x + panelWidth - 18 - contentLeft;
		headerExtra = headerExtra();
		bodyTop = y + 50 + headerExtra;
		footer = y + panelHeight - 63;
		bodyBottom = footer - 20;
		treeBottom = footer - 4;
		rowHeight = narrow ? 18 : 24;
		pageSize = Math.max(1, (bodyBottom - bodyTop - 12) / rowHeight);
		tree.clear();
		for (var node : state.children.getOrDefault("", List.of()))
			appendTree(node, 0);
		treeScroll = Math.clamp(treeScroll, 0, Math.max(0, tree.size() * 24 - (treeBottom - bodyTop)));
		addBreadcrumbs();
		var entries = entries();
		int count = childNodes().size() + entries.size();
		int pageCount = Math.max(1, (count + pageSize - 1) / pageSize);
		for (int i = 0; i < entries.size(); i++)
			if (entries.get(i).id().equals(state.selected))
				pages.put(current, (childNodes().size() + i) / pageSize);
		int page = Math.min(pages.getOrDefault(current, 0), pageCount - 1);
		pages.put(current, page);
		if (page > 0)
			addRenderableWidget(
			    new PaperButton(contentLeft, footer - 21, 60, "上一页", true, () -> { changePage(page - 1); }));
		if (page + 1 < pageCount)
			addRenderableWidget(new PaperButton(contentLeft + contentWidth - 60, footer - 21, 60, "下一页", true,
			                                    () -> { changePage(page + 1); }));
		int controls = footer + 17, left = x + 18;
		minus = addRenderableWidget(
		    new PaperButton(left + 28, controls, 22, "−", false, () -> changeQuantity(Math.max(1, count() - 1))));
		batches = new EditBox(font, left + 55, controls + 6, 44, 13, Component.literal("交易批数"));
		batches.setMaxLength(10);
		batches.setBordered(false);
		batches.setCentered(true);
		batches.setTextShadow(false);
		batches.setTextColor(PaperStyle.INK);
		batches.setTooltip(Tooltip.create(Component.literal("输入交易批数")));
		batches.setValue(quantity);
		batches.setResponder(value -> {
			quantity = value;
			state.message = "";
			stateChanged();
		});
		addRenderableWidget(batches);
		plus = addRenderableWidget(
		    new PaperButton(left + 103, controls, 22, "+", false, () -> changeQuantity(count() + 1)));
		maximum =
		    addRenderableWidget(new PaperButton(left + 131, controls, 40, "最大", false,
			                                    () -> changeQuantity(state.quote == null ? 0 : state.quote.maximum())));
		int actionWidth = Math.min(170, Math.max(100, panelWidth - 226));
		submit = addRenderableWidget(
		    new PaperButton(x + panelWidth - 18 - actionWidth, controls, actionWidth, "请选择商品", false, () -> {
			    state.trade(count());
			    stateChanged();
		    }));
		stateChanged();
		if (!state.selected.isEmpty() && state.quote == null && !state.pending)
			state.select(state.selected);
	}
	private void changePage(int page) {
		pages.put(current, page);
		selections.remove(current);
		state.select("");
		quantity = "1";
		rebuildWidgets();
	}

	private int headerExtra() {
		return Smp3Client.BALANCES.width() + font.width("商店") + 26 > panelWidth - 36 ? 20 : 0;
	}
	void balancesChanged() {
		if (headerExtra != headerExtra())
			rebuildWidgets();
	}
	private ShopPayloads.Node node() { return state.nodes.get(current); }
	private List<ShopPayloads.Entry> entries() { return node() == null ? List.of() : node().entries(); }
	private void addBreadcrumbs() {
		breadcrumbs.clear();
		var trail = new ArrayList<String>();
		trail.add("");
		if (path.size() > 2)
			trail.add(path.get(path.size() - 3));
		trail.addAll(path.subList(Math.max(0, path.size() - 2), path.size()));
		int left = contentLeft, available = contentWidth - (trail.size() - 1) * 10;
		for (int i = 0; i < trail.size(); i++) {
			String id = trail.get(i);
			String name = id.isEmpty() ? "商店" : state.nodes.get(id).name();
			String label = path.size() > 3 && i == 1 ? "…" : name;
			int size = Math.min(font.width(label) + 12, available / (trail.size() - i));
			var button =
			    addRenderableWidget(new PaperButton(left, bodyTop - 23, size, label, true, () -> selectNode(id, true)));
			breadcrumbs.add(button);
			button.active = !id.equals(current);
			button.setTooltip(Tooltip.create(Component.literal(name)));
			left += size + 10;
			available -= size;
		}
	}
	private List<ShopPayloads.Node> childNodes() { return state.children.getOrDefault(current, List.of()); }
	private boolean hasChildren(String id) { return state.children.containsKey(id); }
	private void appendTree(ShopPayloads.Node node, int depth) {
		boolean children = hasChildren(node.id());
		tree.add(new TreeRow(node, depth, children));
		if (expanded.contains(node.id()))
			for (var child : state.children.getOrDefault(node.id(), List.of()))
				appendTree(child, depth + 1);
	}
	void selectNode(String id, boolean resetQuantity) {
		current = id;
		var ancestors = new ArrayList<String>();
		for (var node = node(); node != null; node = state.nodes.get(node.parent()))
			ancestors.addFirst(node.id());
		path = List.copyOf(ancestors);
		expanded.addAll(path);
		var node = node();
		String selected = selections.getOrDefault(id, "");
		state.selected = node != null && node.entries().stream().anyMatch(e -> e.id().equals(selected)) ? selected : "";
		state.quote = null;
		state.pending = false;
		state.message = "";
		if (resetQuantity)
			quantity = "1";
		rebuildWidgets();
		if (state.selected.isEmpty())
			state.select("");
	}
	void selectEntry(String id) {
		selections.put(current, id);
		quantity = "1";
		state.select(id);
		rebuildWidgets();
	}
	private int count() {
		try {
			return Integer.parseInt(quantity);
		} catch (NumberFormatException error) {
			return 0;
		}
	}
	private void changeQuantity(int count) { batches.setValue(Integer.toString(count)); }
	void stateChanged() {
		if (submit == null || batches == null)
			return;
		var entry = state.entries.get(state.selected);
		int count = count(), max = state.quote == null ? 0 : state.quote.maximum();
		for (var widget : List.of(minus, plus, maximum, batches, submit))
			widget.visible = entry != null;
		boolean ready = entry != null && state.quote != null && !state.pending;
		boolean title = entry != null && entry.title();
		minus.active = ready && !title && count > 1;
		plus.active = ready && !title && count < max;
		maximum.active = ready && !title && max > 0;
		batches.setEditable(entry != null && !title && !state.pending);
		submit.active = ready && count > 0 && count <= max;
		String label = entry == null                                                ? "请选择商品"
		               : title && state.quote != null && state.quote.pendingTitle() ? "发放中"
		               : title && state.quote != null && state.quote.owned() > 0    ? "已拥有"
		               : state.pending                                              ? "正在更新…"
		               : count < 1
		                   ? "输入批数"
		                   : (entry.buy() ? "花费 " : "获得 ") + (long)count * entry.price() + " " + currency(entry);
		submit.setMessage(Component.literal(label));
		submit.setTooltip(Tooltip.create(Component.literal(label)));
	}
	private static String currency(ShopPayloads.Entry entry) {
		return entry.currency().equals("smp3:coffee_stamp") ? "印章" : "通宝";
	}
	@Override
	public void removed() {
		state.close();
	}
	@Override
	public boolean isPauseScreen() {
		return false;
	}
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		float seconds = Math.min(.05F, (System.nanoTime() - lastFrame) / 1e9F);
		lastFrame = System.nanoTime();
		graphics.fill(0, 0, width, height, 0x66000000);
		PaperStyle.frame(graphics, x, y, panelWidth, panelHeight);

		graphics.text(font, "商店", x + 18, y + 15, PaperStyle.INK, false);
		Smp3Client.BALANCES.render(graphics, x + panelWidth - 18, y + 9 + headerExtra,
		                           panelWidth - 36 - (headerExtra == 0 ? font.width("商店") + 18 : 0), seconds);
		graphics.horizontalLine(contentLeft, x + panelWidth - 18, bodyTop - 3, PaperStyle.LINE);
		for (int i = 0; i + 1 < breadcrumbs.size(); i++)
			graphics.text(font, ">", breadcrumbs.get(i).getRight() + 2, bodyTop - 18, PaperStyle.MUTED, false);
		if (!narrow)
			drawTree(graphics, mouseX, mouseY);
		drawProducts(graphics, mouseX, mouseY);
		drawFooter(graphics);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}
	private void drawTree(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int left = x + 18, right = x + 14 + sidebar;
		graphics.verticalLine(right + 1, bodyTop, treeBottom, PaperStyle.LINE);
		graphics.enableScissor(left, bodyTop, right, footer - 4);
		for (int i = 0; i < tree.size(); i++) {
			TreeRow row = tree.get(i);
			int top = bodyTop + i * 24 - treeScroll;
			if (top + 24 < bodyTop || top >= treeBottom)
				continue;
			boolean selected = row.node.id().equals(current);
			if (selected || mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + 23)
				graphics.fill(left, top, right - 4, top + 23, selected ? 0x447F5936 : 0x227F5936);
			int indent = Math.min(row.depth * 10, sidebar - 72);
			if (row.children)
				graphics.text(font, expanded.contains(row.node.id()) ? "−" : "+", left + indent, top + 8,
				              PaperStyle.MUTED, false);
			graphics.item(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(row.node.icon()))),
			              left + indent + 10, top + 3);
			graphics.text(font, font.plainSubstrByWidth(row.node.name(), right - left - indent - 33),
			              left + indent + 30, top + 8, PaperStyle.INK, false);
		}
		graphics.disableScissor();
		PaperStyle.scrollbar(graphics, right - 2, bodyTop, treeBottom, tree.size() * 24, treeScroll);
	}
	private void drawProducts(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		var entries = entries();
		var folders = childNodes();
		int size = folders.size() + entries.size();
		String label = (pages.getOrDefault(current, 0) + 1) + " / " + Math.max(1, (size + pageSize - 1) / pageSize);
		graphics.text(font, label, contentLeft + (contentWidth - font.width(label)) / 2, footer - 16, PaperStyle.MUTED,
		              false);
		if (size == 0) {
			graphics.text(font, "暂无商品", contentLeft, bodyTop + 12, PaperStyle.MUTED, false);
			return;
		}
		graphics.text(font, entries.isEmpty() ? "分类" : "商品", contentLeft + 4, bodyTop, PaperStyle.MUTED, false);
		if (!entries.isEmpty()) {
			graphics.text(font, "每批", contentLeft + contentWidth - 132, bodyTop, PaperStyle.MUTED, false);
			graphics.text(font, "价格", contentLeft + contentWidth - 76, bodyTop, PaperStyle.MUTED, false);
		}
		int page = pages.getOrDefault(current, 0), start = page * pageSize;
		for (int i = start; i < Math.min(size, start + pageSize); i++) {
			int top = bodyTop + 12 + (i - start) * rowHeight;
			if (i < folders.size()) {
				var child = folders.get(i);
				graphics.fill(contentLeft, top, contentLeft + contentWidth, top + rowHeight - 1, 0x227F5936);
				graphics.item(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(child.icon()))),
				              contentLeft + 4, top + (rowHeight - 16) / 2);
				graphics.text(font, font.plainSubstrByWidth(child.name(), contentWidth - 44), contentLeft + 25,
				              top + (rowHeight - 8) / 2, PaperStyle.INK, false);
				graphics.text(font, ">", contentLeft + contentWidth - 12, top + (rowHeight - 8) / 2, PaperStyle.MUTED,
				              false);
				continue;
			}
			var entry = entries.get(i - folders.size());
			boolean hovered = mouseX >= contentLeft && mouseX < contentLeft + contentWidth && mouseY >= top &&
			                  mouseY < top + rowHeight - 1;
			if (entry.id().equals(state.selected) || hovered)
				graphics.fill(contentLeft, top, contentLeft + contentWidth, top + rowHeight - 1,
				              entry.id().equals(state.selected) ? 0x447F5936 : 0x227F5936);
			ItemStack icon = state.icons.get(entry.id());
			graphics.item(icon, contentLeft + 4, top + (rowHeight - 16) / 2);
			graphics.text(font,
			              font.plainSubstrByWidth(icon.getHoverName().getString(), Math.max(25, contentWidth - 166)),
			              contentLeft + 25, top + (rowHeight - 8) / 2, PaperStyle.INK, false);
			graphics.text(font, entry.title() ? "称号" : entry.amount() + " 个", contentLeft + contentWidth - 132,
			              top + (rowHeight - 8) / 2, PaperStyle.MUTED, false);
			graphics.text(font, entry.price() + " " + currency(entry), contentLeft + contentWidth - 76,
			              top + (rowHeight - 8) / 2, PaperStyle.INK, false);
			if (hovered) {
				var lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, icon));
				entry.description().forEach(line -> lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY)));
				lines.add(Component.literal(entry.title() ? "解锁称号"
				                            : entry.buy() ? "购买物品"
				                                          : "出售物品")
				              .withStyle(ChatFormatting.GRAY));
				lines.add(Component.literal(entry.availability()).withStyle(ChatFormatting.GRAY));
				graphics.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
			}
		}
	}
	private void drawFooter(GuiGraphicsExtractor graphics) {
		graphics.horizontalLine(x + 18, x + panelWidth - 18, footer - 2, PaperStyle.LINE);
		var entry = state.entries.get(state.selected);
		if (entry == null) {
			graphics.text(font, "选择商品后查看交易信息", x + 18, footer + 8, PaperStyle.MUTED, false);
			return;
		}
		var quote = state.quote;
		String owned = quote == null ? "正在读取…" : quote.owned() < 0 ? "暂不可用" : quote.owned() + " 个";
		String used = quote == null ? "—" : quote.used() + (quote.limit() < 0 ? " 个" : " / " + quote.limit() + " 个");
		String stock = entry.title() ? quote == null          ? "正在读取…"
		                               : quote.pendingTitle() ? "发放中"
		                               : quote.owned() > 0    ? "已拥有"
		                                                      : "未拥有"
		                             : "持有 " + owned;
		String traded = (quote != null && quote.weekly() ? "本周交易 " : "今日交易 ") + used;
		graphics.text(font, font.plainSubstrByWidth(stock, (panelWidth - 48) / 2), x + 18, footer + 4, PaperStyle.INK,
		              false);
		graphics.text(font, font.plainSubstrByWidth(traded, (panelWidth - 48) / 2), x + panelWidth / 2, footer + 4,
		              PaperStyle.INK, false);
		int controls = footer + 17;
		graphics.text(font, "批数", x + 18, controls + 6, PaperStyle.MUTED, false);
		graphics.horizontalLine(x + 73, x + 117, controls + 19, PaperStyle.LINE);
		String message = state.message;
		if (message.isEmpty()) {
			if (quote != null && quote.owned() < 0)
				message = "账户暂不可用";
			else if (entry.title() && quote != null && (quote.pendingTitle() || quote.owned() > 0))
				message = quote.pendingTitle() ? "称号发放中，请稍候" : "已拥有该称号";
			else if (quote != null && quote.maximum() == 0)
				message = quote.limit() >= 0 && quote.limit() - quote.used() < entry.amount()
				              ? (quote.weekly() ? "本周" : "今日") + "额度不足一批"
				          : entry.buy() && quote.balance() < Smp3Currency.rawUnits(entry.price())
				              ? "余额不足"
				              : "符合要求的物品不足";
			else if (count() < 1)
				message = "请输入有效批数";
			else if (quote != null && count() > quote.maximum())
				message = "当前最多可交易 " + quote.maximum() + " 批";
			else
				message = entry.title() ? "解锁后可在称号菜单中佩戴" : "共 " + (long)count() * entry.amount() + " 个";
		}
		graphics.text(font, font.plainSubstrByWidth(message, panelWidth - 36), x + 18, controls + 23, PaperStyle.MUTED,
		              false);
	}
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick))
			return true;
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
			return false;
		if (!narrow && event.x() >= x + 18 && event.x() < x + 14 + sidebar && event.y() >= bodyTop &&
		    event.y() < treeBottom) {
			int row = ((int)event.y() - bodyTop + treeScroll) / 24;
			if (row < tree.size()) {
				var selected = tree.get(row);
				int indent = Math.min(selected.depth * 10, sidebar - 72);
				if (selected.children && event.x() < x + 28 + indent) {
					if (!expanded.remove(selected.node.id()))
						expanded.add(selected.node.id());
					rebuildWidgets();
				} else
					selectNode(selected.node.id(), true);
			}
			return true;
		}
		if (event.x() < contentLeft || event.x() >= contentLeft + contentWidth || event.y() < bodyTop + 12 ||
		    event.y() >= bodyBottom)
			return false;
		int row = ((int)event.y() - bodyTop - 12) / rowHeight;
		int index = pages.getOrDefault(current, 0) * pageSize + row;
		var folders = childNodes();
		if (row < pageSize && index < folders.size())
			selectNode(folders.get(index).id(), true);
		else if (row < pageSize && index - folders.size() < entries().size())
			selectEntry(entries().get(index - folders.size()).id());
		return true;
	}
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (!narrow && mouseX >= x + 18 && mouseX < x + 14 + sidebar && mouseY >= bodyTop && mouseY < treeBottom) {
			treeScroll = Math.clamp(treeScroll - (int)(vertical * 24), 0,
			                        Math.max(0, tree.size() * 24 - (treeBottom - bodyTop)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}
}
