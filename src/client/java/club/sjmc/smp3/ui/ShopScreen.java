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
import net.minecraft.world.item.Items;

final class ShopScreen extends Screen {
	private static final int GRID_GAP = 0;
	private static final int NAME_CENTER_OFFSET = 1;
	private static final int MODAL_HEIGHT = 112;
	private static final int TREE_ROW = 20;
	private final ShopState state = Smp3Client.SHOP;
	private final Set<String> expanded = new HashSet<>();
	private final Map<String, String> selections = new HashMap<>();
	private final ItemStack coinIcon = new ItemStack(Items.IRON_PICKAXE);
	private final ItemStack stampIcon = new ItemStack(Items.PAPER);
	private List<String> path = List.of();
	private record TreeRow(ShopPayloads.Node node, int depth, boolean children) {}
	private final List<TreeRow> tree = new ArrayList<>();
	private int x, y, panelWidth, panelHeight, sidebar, contentLeft, contentWidth, bodyTop, bodyBottom, treeTop,
	    treeBottom, headerExtra, treeScroll, gridScroll, gridMax, gridContent, modalX, modalY, modalWidth;
	private String current = "", quantity = "1";
	private boolean narrow, modal, embedded;
	private int originX, originY;
	private EditBox batches;
	private PaperButton minus, plus, maximum, confirm;
	private PaperStyle.Scrollbar draggedScrollbar;
	private boolean draggingGrid, draggingTree;
	private double scrollGrab;
	private long lastFrame = System.nanoTime();

	ShopScreen() { super(Component.literal("商店")); }
	void catalogChanged() {
		expanded.retainAll(state.nodes.keySet());
		selections.keySet().retainAll(state.nodes.keySet());
		if (!state.nodes.containsKey(current)) {
			current = "";
			for (String id : path)
				if (state.nodes.containsKey(id))
					current = id;
		}
		if (!state.entries.containsKey(state.selected))
			modal = false;
		selectNode(current, false);
	}

	void embed(int originX, int originY, int width, int height) {
		this.originX = originX;
		this.originY = originY;
		this.embedded = true;
		init(width, height);
	}
	@Override
	protected void init() {
		draggedScrollbar = null;
		draggingGrid = false;
		draggingTree = false;
		if (embedded) {
			x = originX;
			y = originY;
			panelWidth = width;
			panelHeight = height;
		} else {
			int localX = Math.max(6, (width - 650) / 2);
			int localY = Math.max(6, (height - 390) / 2);
			x = originX + localX;
			y = originY + localY;
			panelWidth = width - 2 * localX;
			panelHeight = height - 2 * localY;
		}
		narrow = panelWidth < 440;
		sidebar = Math.max(72, panelWidth / 3 - 14);
		contentLeft = x + sidebar + 24;
		contentWidth = x + panelWidth - 18 - contentLeft;
		headerExtra = headerExtra();
		bodyTop = y + 38 + headerExtra;
		bodyBottom = y + panelHeight - 18;
		treeTop = y + 18;
		treeBottom = bodyBottom;
		tree.clear();
		for (var node : state.children.getOrDefault("", List.of()))
			appendTree(node, 0);
		treeScroll = Math.clamp(treeScroll, 0, Math.max(0, tree.size() * TREE_ROW - (treeBottom - treeTop)));
		updateGridMetrics();
		modalWidth = Math.min(230, Math.max(176, contentWidth));
		modalWidth = Math.min(modalWidth, panelWidth - 32);
		modalX = x + (panelWidth - modalWidth) / 2;
		modalY = y + (panelHeight - MODAL_HEIGHT) / 2;
		int controls = modalY + 44;
		int controlsLeft = modalX + (modalWidth - 140) / 2;
		minus = addRenderableWidget(
		    new PaperButton(controlsLeft, controls, 22, "−", false, () -> changeQuantity(Math.max(1, count() - 1))));
		batches = new EditBox(font, controlsLeft + 26, controls + 6, 44, 13, Component.literal("交易批数"));
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
		    new PaperButton(controlsLeft + 74, controls, 22, "+", false, () -> changeQuantity(count() + 1)));
		maximum = addRenderableWidget(new PaperButton(controlsLeft + 100, controls, 40, "最大", false,
		                                                () -> changeQuantity(
		                                                    state.quote == null ? 0 : state.quote.maximum())));
		confirm = addRenderableWidget(new PaperButton(modalX + modalWidth - 60, modalY + 81, 40, "确认", false, () -> {
			state.trade(count());
			stateChanged();
		}));
		stateChanged();
		if (!state.selected.isEmpty() && state.quote == null && !state.pending)
			state.select(state.selected);
	}
	private int headerExtra() {
		return Smp3Client.BALANCES.width() + 26 > panelWidth - 60 ? 20 : 0;
	}
	void balancesChanged() {
		if (headerExtra != headerExtra())
			rebuildWidgets();
	}
	private ShopPayloads.Node node() { return state.nodes.get(current); }
	private List<ShopPayloads.Entry> entries() { return node() == null ? List.of() : node().entries(); }
	private List<ShopPayloads.Node> childNodes() { return state.children.getOrDefault(current, List.of()); }
	private boolean hasChildren(String id) { return state.children.containsKey(id); }
	private void appendTree(ShopPayloads.Node node, int depth) {
		boolean children = hasChildren(node.id());
		tree.add(new TreeRow(node, depth, children));
		if (expanded.contains(node.id()))
			for (var child : state.children.getOrDefault(node.id(), List.of()))
				appendTree(child, depth + 1);
	}
	private int cellSize() { return narrow ? 58 : 64; }
	private int gridColumns() { return Math.max(1, (contentWidth - 8 + GRID_GAP) / (cellSize() + GRID_GAP)); }
	private void updateGridMetrics() {
		int cell = cellSize(), columns = gridColumns();
		int count = childNodes().size() + entries().size();
		int rows = (count + columns - 1) / columns;
		gridContent = Math.max(0, rows * (cell + GRID_GAP) - GRID_GAP);
		gridMax = Math.max(0, gridContent - (bodyBottom - bodyTop));
		gridScroll = Math.clamp(gridScroll, 0, gridMax);
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
		modal = false;
		gridScroll = 0;
		if (resetQuantity)
			quantity = "1";
		rebuildWidgets();
		if (state.selected.isEmpty())
			state.select("");
	}
	void selectEntry(String id) {
		selections.put(current, id);
		quantity = "1";
		modal = true;
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
	private void changeQuantity(int count) {
		if (batches != null)
			batches.setValue(Integer.toString(count));
	}
	void stateChanged() {
		if (confirm == null || batches == null)
			return;
		var entry = state.entries.get(state.selected);
		boolean visible = modal && entry != null;
		if (visible && !state.pending &&
		    ("购买成功".equals(state.message) || "出售成功".equals(state.message))) {
			modal = false;
			rebuildWidgets();
			return;
		}
		for (var widget : List.of(minus, plus, maximum, batches, confirm))
			widget.visible = visible;
		if (!visible)
			return;
		int max = state.quote == null ? 0 : state.quote.maximum();
		boolean ready = state.quote != null && !state.pending;
		boolean title = entry.title();
		minus.active = ready && !title && count() > 1;
		plus.active = ready && !title && count() < max;
		maximum.active = ready && !title && max > 0;
		batches.setEditable(ready && !title);
		confirm.active = ready && count() > 0 && count() <= max;
		confirm.setMessage(Component.literal("确认"));
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
		if (!embedded) {
			graphics.fill(0, 0, width, height, 0x66000000);
			PaperStyle.frame(graphics, x, y, panelWidth, panelHeight);
		}

		Smp3Client.BALANCES.render(graphics, x + panelWidth - 30, y + 12 + headerExtra, panelWidth - 60, seconds);
		graphics.horizontalLine(x + sidebar + 14, x + panelWidth - 20, bodyTop - 3, PaperStyle.LINE);
		drawTree(graphics, mouseX, mouseY);
		drawProducts(graphics, mouseX, mouseY);
		if (modal)
			drawModal(graphics);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}
	private void drawTree(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int left = x + 20, right = x + 14 + sidebar, cellRight = right - 12;
		int dividerTop = y + 14;
		graphics.fill(x + sidebar + 14, dividerTop, x + sidebar + 16, treeBottom, 0x66836B48);
		graphics.fill(x + sidebar + 16, dividerTop, x + sidebar + 19, treeBottom, 0x22765A37);
		graphics.enableScissor(left - 2, treeTop, right, treeBottom);
		for (int i = 0; i < tree.size(); i++) {
			TreeRow row = tree.get(i);
			int top = treeTop + i * TREE_ROW - treeScroll;
			if (top + TREE_ROW < treeTop || top >= treeBottom)
				continue;
			boolean selected = row.node.id().equals(current);
			if (selected || mouseX >= left && mouseX < cellRight && mouseY >= top && mouseY < top + TREE_ROW)
				graphics.fill(left, top, cellRight, top + TREE_ROW, selected ? 0x447F5936 : 0x227F5936);
			int indent = Math.max(0, Math.min(row.depth * 10, sidebar - 72));
			if (row.children) {
				if (expanded.contains(row.node.id())) {
					graphics.pose().pushMatrix();
					graphics.pose().translate(left + indent + 2.5F, top + TREE_ROW / 2F);
					graphics.pose().rotate((float)(Math.PI / 2));
					graphics.text(font, ">", -2, -4, PaperStyle.MUTED, false);
					graphics.pose().popMatrix();
				} else
					graphics.text(font, ">", left + indent, top + 6, PaperStyle.MUTED, false);
			}
			drawSmallIcon(graphics,
			              new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(row.node.icon()))),
			              left + indent + 9, top + 4);
			drawTreeName(graphics, row.node.name(), left + indent + 22, top + 6,
			             cellRight - left - indent - 26);
		}
		graphics.disableScissor();
		PaperStyle.scrollbar(graphics, right - 2, treeTop, treeBottom, tree.size() * TREE_ROW, treeScroll);
	}

	private void drawSmallIcon(GuiGraphicsExtractor graphics, ItemStack icon, int x, int y) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(.75F, .75F);
		graphics.item(icon, 0, 0);
		graphics.pose().popMatrix();
	}

	private void drawTreeName(GuiGraphicsExtractor graphics, String name, int x, int y, int maxWidth) {
		float scale = .85F;
		String label = font.plainSubstrByWidth(name, Math.max(1, (int)(maxWidth / scale)));
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y + (font.lineHeight - font.lineHeight * scale) / 2);
		graphics.pose().scale(scale, scale);
		graphics.text(font, label, 0, 0, PaperStyle.INK, false);
		graphics.pose().popMatrix();
	}
	private void drawGridIcon(GuiGraphicsExtractor graphics, ItemStack icon, int iconX, int iconY) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(iconX, iconY);
		graphics.pose().scale(2, 2);
		graphics.item(icon, 0, 0);
		graphics.pose().popMatrix();
		graphics.pose().pushMatrix();
		graphics.pose().translate(iconX + 6.5F, iconY + 6.5F);
		graphics.pose().scale(1.5F, 1.5F);
		graphics.itemDecorations(font, icon, 0, 0);
		graphics.pose().popMatrix();
	}
	private void drawPrice(GuiGraphicsExtractor graphics, ShopPayloads.Entry entry, int px, int py, int cell) {
		String text = Integer.toString(entry.price());
		int width = 12 + font.width(text);
		int left = px + (cell - width) / 2;
		ItemStack icon = entry.currency().equals("smp3:coffee_stamp") ? stampIcon : coinIcon;
		graphics.pose().pushMatrix();
		graphics.pose().translate(left, py);
		graphics.pose().scale(10F / 16F, 10F / 16F);
		graphics.item(icon, 0, 0);
		graphics.pose().popMatrix();
		graphics.text(font, text, left + 12, py + 1, PaperStyle.INK, false);
	}

	private void drawFittedName(GuiGraphicsExtractor graphics, String text, int centerX, int y, int maxWidth) {
		int width = font.width(text);
		float scale = Math.min(1F, maxWidth / (float)Math.max(1, width));
		if (scale >= .995F) {
			graphics.text(font, text, centerX - width / 2, y, PaperStyle.INK, false);
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX - width * scale / 2, y + (font.lineHeight - font.lineHeight * scale) / 2);
		graphics.pose().scale(scale, scale);
		graphics.text(font, text, 0, 0, PaperStyle.INK, false);
		graphics.pose().popMatrix();
	}
	private void drawProducts(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		var entries = entries();
		var folders = childNodes();
		int size = folders.size() + entries.size();
		int cell = cellSize(), columns = gridColumns();
		int rows = (size + columns - 1) / columns;
		int contentHeight = Math.max(0, rows * (cell + GRID_GAP) - GRID_GAP);
		gridContent = contentHeight;
		gridMax = Math.max(0, contentHeight - (bodyBottom - bodyTop));
		gridScroll = Math.clamp(gridScroll, 0, gridMax);
		if (size == 0) {
			graphics.text(font, "暂无商品", contentLeft, bodyTop + 12, PaperStyle.MUTED, false);
			return;
		}
		graphics.enableScissor(contentLeft, bodyTop, contentLeft + contentWidth, bodyBottom);
		for (int i = 0; i < size; i++) {
			int col = i % columns, row = i / columns;
			int left = contentLeft + col * (cell + GRID_GAP);
			int top = bodyTop + row * (cell + GRID_GAP) - gridScroll;
			if (top + cell < bodyTop || top >= bodyBottom)
				continue;
			if (i < folders.size()) {
				var child = folders.get(i);
				boolean hovered = mouseX >= left && mouseX < left + cell && mouseY >= top && mouseY < top + cell;
				if (hovered)
					graphics.fill(left, top, left + cell, top + cell, 0x227F5936);
				drawGridIcon(graphics,
				             new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(child.icon()))),
				             left + (cell - 32) / 2, top + 6);
				String name = child.name();
				drawFittedName(graphics, name, left + cell / 2 + NAME_CENTER_OFFSET, top + 44, cell - 8);
				continue;
			}
			var entry = entries.get(i - folders.size());
			boolean selected = modal && entry.id().equals(state.selected);
			boolean hovered = mouseX >= left && mouseX < left + cell && mouseY >= top && mouseY < top + cell;
			if (selected || hovered)
				graphics.fill(left, top, left + cell, top + cell, selected ? 0x447F5936 : 0x227F5936);
			ItemStack icon = state.icons.get(entry.id());
			int iconX = left + (cell - 32) / 2, iconY = top + 5;
			ItemStack shown = entry.amount() > 1 && !entry.title() ? icon.copyWithCount(entry.amount()) : icon;
			drawGridIcon(graphics, shown, iconX, iconY);
			String name = icon.getHoverName().getString();
			drawFittedName(graphics, name, left + cell / 2 + NAME_CENTER_OFFSET, top + 40, cell - 8);
			drawPrice(graphics, entry, left, top + 51, cell);
			if (hovered && !modal) {
				var lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, icon));
				entry.description().forEach(line -> lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY)));
				lines.add(Component.literal(entry.title() ? "解锁称号"
				                            : entry.buy() ? "购买物品"
				                                          : "出售物品")
				              .withStyle(ChatFormatting.GRAY));
				lines.add(Component.literal(entry.availability()).withStyle(ChatFormatting.GRAY));
				graphics.setTooltipForNextFrame(font, lines, Optional.empty(), MenuScreen.tooltipX(mouseX),
				                                MenuScreen.tooltipY(mouseY));
			}
		}
		graphics.disableScissor();
		PaperStyle.scrollbar(graphics, contentLeft + contentWidth - 2, bodyTop, bodyBottom, contentHeight, gridScroll);
	}
	private void drawModal(GuiGraphicsExtractor graphics) {
		var entry = state.entries.get(state.selected);
		if (entry == null)
			return;
		graphics.fill(originX, originY, originX + width, originY + height, 0x99000000);
		graphics.fill(modalX, modalY, modalX + modalWidth, modalY + MODAL_HEIGHT, 0xFFF2E7CF);
		graphics.outline(modalX, modalY, modalWidth, MODAL_HEIGHT, 0xFF8C5D38);
		var quote = state.quote;
		boolean pending = state.pending || quote == null;
		String period = quote != null && quote.weekly() ? "本周" : "今日";
		String header = pending           ? period + "还可交易 …"
		                : quote.owned() < 0 ? "账户暂不可用"
		                                    : period + "还可交易 " + quote.maximum() + " 批";
		graphics.text(font, Component.literal(font.plainSubstrByWidth(header, modalWidth - 28)).withStyle(style -> style.withBold(true)),
		              modalX + 14, modalY + 12, PaperStyle.INK, false);
		graphics.text(font, entry.buy() ? "购买批数" : "出售批数", modalX + 14, modalY + 30, PaperStyle.MUTED, false);
		graphics.horizontalLine(modalX + 14, modalX + modalWidth - 14, modalY + 25, PaperStyle.LINE);
		graphics.horizontalLine(batches.getX(), batches.getX() + batches.getWidth(), batches.getY() + 14,
		                        PaperStyle.LINE);
		long total = (long)count() * entry.price();
		String totalText = (entry.buy() ? "花费 " : "获得 ") + total + " " + currency(entry);
		graphics.text(font, font.plainSubstrByWidth(totalText, modalWidth - 28), modalX + 14, modalY + 68,
		              PaperStyle.INK, false);
		String message = modalMessage(entry);
		if (!message.isEmpty()) {
			boolean info = message.startsWith("共 ") || message.startsWith("解锁后") || message.startsWith("正在") ||
			               message.contains("成功");
			graphics.text(font, font.plainSubstrByWidth(message, modalWidth - 28), modalX + 14, modalY + 88,
			              info ? PaperStyle.INK : 0xFFAD4434, false);
		}
	}
	private String modalMessage(ShopPayloads.Entry entry) {
		if (state.pending)
			return state.quote == null ? "正在读取…" : "正在交易…";
		if (!state.message.isEmpty())
			return state.message;
		var quote = state.quote;
		if (quote == null)
			return "正在读取…";
		if (quote.owned() < 0)
			return "账户暂不可用";
		if (count() < 1)
			return "请输入有效批数";
		String period = quote.weekly() ? "本周" : "今日";
		if (count() > quote.maximum())
			return period + "最多还能交易 " + quote.maximum() + " 批";
		if (quote.maximum() == 0)
			return quote.limit() >= 0 && quote.limit() - quote.used() < entry.amount()
			           ? period + "额度不足一批"
			       : entry.buy() && quote.balance() < Smp3Currency.rawUnits(count() * entry.price()) ? "余额不足"
			                                                                                           : "符合要求的物品不足";
		return entry.title() ? "解锁后可在称号菜单中佩戴" : "共 " + (long)count() * entry.amount() + " 个";
	}
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick))
			return true;
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
			return false;
		if (modal) {
			if (!insideModal(event.x(), event.y())) {
				modal = false;
				rebuildWidgets();
			}
			return true;
		}
		var gridBar = new PaperStyle.Scrollbar(contentLeft + contentWidth - 2, bodyTop, bodyBottom, gridContent);
		if (gridBar.contains(event.x(), event.y())) {
			draggedScrollbar = gridBar;
			draggingGrid = true;
			draggingTree = false;
			int thumb = gridBar.thumbTop(gridScroll);
			scrollGrab = event.y() >= thumb && event.y() < thumb + gridBar.thumbHeight()
			                 ? event.y() - thumb
			                 : gridBar.thumbHeight() / 2.0;
			return mouseDragged(event, 0, 0);
		}
		int treeLeft = x + 20, treeRight = x + 14 + sidebar, treeCellRight = treeRight - 12;
		var treeBar = new PaperStyle.Scrollbar(treeRight - 2, treeTop, treeBottom, tree.size() * TREE_ROW);
		if (treeBar.contains(event.x(), event.y())) {
			draggedScrollbar = treeBar;
			draggingTree = true;
			draggingGrid = false;
			int thumb = treeBar.thumbTop(treeScroll);
			scrollGrab = event.y() >= thumb && event.y() < thumb + treeBar.thumbHeight()
			                 ? event.y() - thumb
			                 : treeBar.thumbHeight() / 2.0;
			return mouseDragged(event, 0, 0);
		}
		if (event.x() >= treeLeft && event.x() < treeCellRight && event.y() >= treeTop &&
		    event.y() < treeBottom) {
			int row = ((int)event.y() - treeTop + treeScroll) / TREE_ROW;
			if (row < tree.size()) {
				var selected = tree.get(row);
				if (selected.children) {
					boolean collapse = expanded.contains(selected.node.id());
					if (!selected.node.id().equals(current))
						selectNode(selected.node.id(), true);
					if (collapse)
						expanded.remove(selected.node.id());
					else
						expanded.add(selected.node.id());
					rebuildWidgets();
				} else
					selectNode(selected.node.id(), true);
			}
			return true;
		}
		int index = gridIndexAt(event.x(), event.y());
		if (index < 0)
			return false;
		var folders = childNodes();
		if (index < folders.size())
			selectNode(folders.get(index).id(), true);
		else {
			var entries = entries();
			int entryIndex = index - folders.size();
			if (entryIndex < entries.size())
				selectEntry(entries.get(entryIndex).id());
		}
		return true;
	}
	private int gridIndexAt(double mouseX, double mouseY) {
		if (mouseX < contentLeft || mouseX >= contentLeft + contentWidth || mouseY < bodyTop || mouseY >= bodyBottom)
			return -1;
		int cell = cellSize(), columns = gridColumns();
		int col = (int)((mouseX - contentLeft) / (cell + GRID_GAP));
		int row = (int)((mouseY - bodyTop + gridScroll) / (cell + GRID_GAP));
		if (col >= columns || (mouseX - contentLeft) - col * (cell + GRID_GAP) >= cell)
			return -1;
		if ((mouseY - bodyTop + gridScroll) - row * (cell + GRID_GAP) >= cell)
			return -1;
		int index = row * columns + col;
		return index < childNodes().size() + entries().size() ? index : -1;
	}
	private boolean insideModal(double mouseX, double mouseY) {
		return mouseX >= modalX && mouseX < modalX + modalWidth && mouseY >= modalY && mouseY < modalY + MODAL_HEIGHT;
	}
	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || draggedScrollbar == null)
			return super.mouseDragged(event, dx, dy);
		int scroll = draggedScrollbar.position(event.y(), scrollGrab);
		if (draggingTree)
			treeScroll = scroll;
		else if (draggingGrid)
			gridScroll = scroll;
		return true;
	}
	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || draggedScrollbar == null)
			return super.mouseReleased(event);
		draggedScrollbar = null;
		draggingGrid = false;
		draggingTree = false;
		return true;
	}
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (modal)
			return true;
		if (mouseX >= x + 18 && mouseX < x + 14 + sidebar && mouseY >= treeTop && mouseY < treeBottom) {
			treeScroll = Math.clamp(treeScroll - (int)(vertical * TREE_ROW), 0,
			                        Math.max(0, tree.size() * TREE_ROW - (treeBottom - treeTop)));
			return true;
		}
		if (mouseX >= contentLeft && mouseX < contentLeft + contentWidth && mouseY >= bodyTop &&
		    mouseY < bodyBottom) {
			gridScroll = Math.clamp(gridScroll - (int)(vertical * 24), 0, gridMax);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}
}
