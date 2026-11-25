package club.sjmc.smp3.ui.panel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;

import club.sjmc.smp3.economy.Smp3Currency;
import club.sjmc.smp3.shop.ShopPayloads;
import club.sjmc.smp3.ui.Smp3Client;
import club.sjmc.smp3.ui.state.ShopState;
import club.sjmc.smp3.ui.themes.PaperStyle;
import club.sjmc.smp3.ui.widget.ItemIcon;
import club.sjmc.smp3.ui.widget.PanelContext;
import club.sjmc.smp3.ui.widget.PaperButton;
import club.sjmc.smp3.ui.widget.ScrollView;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 商店面板：左侧树、右侧商品网格与交易模态框。
 */
public final class ShopPanel extends AbstractPanel implements ShopState.Listener {
	private static final int GRID_GAP = 0;
	private static final int NAME_CENTER_OFFSET = 1;
	private static final int MODAL_HEIGHT = 112;
	private static final int TREE_ROW = 20;
	private final ShopState state;
	private final Set<String> expanded = new HashSet<>();
	private final Map<String, String> selections = new HashMap<>();
	private List<String> path = List.of();
	private record TreeRow(ShopPayloads.Node node, int depth, boolean children) {}
	private final List<TreeRow> tree = new ArrayList<>();
	private int panelX, panelY, panelWidth, panelHeight, sidebar, contentLeft, contentWidth, bodyTop, bodyBottom,
	    treeTop, treeBottom, gridContent, modalX, modalY, modalWidth;
	private final ScrollView treeScroll = new ScrollView();
	private final ScrollView gridScroll = new ScrollView();
	private String current = "", quantity = "1";
	private boolean narrow, modal;
	private EditBox batches;
	private PaperButton minus, plus, maximum, confirm;
	private long lastFrame = System.nanoTime();

	public ShopPanel(ShopState state) {
		this.state = state;
		state.addListener(this);
	}

	public void catalogChanged() {
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

	@Override
	protected void onLayout() {
		clearWidgets();
		var font = font();
		panelX = x();
		panelY = y();
		panelWidth = width();
		panelHeight = height();
		narrow = panelWidth < 440;
		sidebar = Math.max(72, panelWidth / 3 - 14);
		contentLeft = panelX + sidebar + 24;
		contentWidth = panelX + panelWidth - 18 - contentLeft;
		bodyTop = panelY + 38;
		bodyBottom = panelY + panelHeight - 18;
		treeTop = panelY + 18;
		treeBottom = bodyBottom;
		tree.clear();
		for (var node : state.children.getOrDefault("", List.of()))
			appendTree(node, 0);
		treeScroll.layout(treeTop, treeBottom, tree.size() * TREE_ROW);
		updateGridMetrics();
		modalWidth = Math.min(230, Math.max(176, contentWidth));
		modalWidth = Math.min(modalWidth, panelWidth - 32);
		modalX = panelX + (panelWidth - modalWidth) / 2;
		modalY = panelY + (panelHeight - MODAL_HEIGHT) / 2;
		int controls = modalY + 44;
		int controlsLeft = modalX + (modalWidth - 140) / 2;
		minus = addWidget(
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
		addWidget(batches);
		plus =
		    addWidget(new PaperButton(controlsLeft + 74, controls, 22, "+", false, () -> changeQuantity(count() + 1)));
		maximum = addWidget(new PaperButton(controlsLeft + 100, controls, 40, "最大", false,
		                                    () -> changeQuantity(state.quote == null ? 0 : state.quote.maximum())));
		confirm = addWidget(new PaperButton(modalX + modalWidth - 60, modalY + 81, 40, "确认", false, () -> {
			state.trade(count());
			stateChanged();
		}));
		stateChanged();
		if (!state.selected.isEmpty() && state.quote == null && !state.pending)
			state.select(state.selected);
	}

	private static Font font() { return Minecraft.getInstance().font; }

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
		gridScroll.layout(bodyTop, bodyBottom, gridContent);
	}

	/** 目录选择入口，同时作为客户端 GameTest 钩子。 */
	public void selectNode(String id, boolean resetQuantity) {
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
		gridScroll.setScroll(0);
		if (resetQuantity)
			quantity = "1";
		onLayout();
		if (state.selected.isEmpty())
			state.select("");
	}

	/** 商品选择入口，同时作为客户端 GameTest 钩子。 */
	public void selectEntry(String id) {
		selections.put(current, id);
		quantity = "1";
		modal = true;
		state.select(id);
		onLayout();
	}

	/** 客户端 GameTest 钩子，生产路径不直接调用。 */
	public EditBox quantityInput() { return batches; }

	/** 客户端 GameTest 钩子，生产路径不直接调用。 */
	public void confirmTrade() {
		state.trade(count());
		stateChanged();
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

	public void stateChanged() {
		if (confirm == null || batches == null)
			return;
		var entry = state.entries.get(state.selected);
		boolean visible = modal && entry != null;
		if (visible && !state.pending && ("购买成功".equals(state.message) || "出售成功".equals(state.message))) {
			modal = false;
			onLayout();
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
	public void shopCatalogChanged() {
		catalogChanged();
	}

	@Override
	public void shopQuoteChanged() {
		stateChanged();
	}

	public void detachListeners() { state.removeListener(this); }

	@Override
	public void close() {
		detachListeners();
		state.close();
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, PanelContext context) {
		float seconds = Math.min(.05F, (System.nanoTime() - lastFrame) / 1e9F);
		lastFrame = System.nanoTime();
		Smp3Client.currencyBadge().render(graphics, panelX + panelWidth - 30, panelY + 12, panelWidth - 60, seconds);
		graphics.horizontalLine(panelX + sidebar + 14, panelX + panelWidth - 20, bodyTop - 3, PaperStyle.LINE);
		drawTree(graphics, mouseX, mouseY);
		drawProducts(graphics, mouseX, mouseY, context);
		if (modal)
			drawModal(graphics);
		renderWidgets(graphics, mouseX, mouseY, delta);
	}

	private void drawTree(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		var font = font();
		int left = panelX + 20, right = panelX + 14 + sidebar, cellRight = right - 12;
		int dividerTop = panelY + 14;
		graphics.fill(panelX + sidebar + 14, dividerTop, panelX + sidebar + 16, treeBottom, 0x66836B48);
		graphics.fill(panelX + sidebar + 16, dividerTop, panelX + sidebar + 19, treeBottom, 0x22765A37);
		graphics.enableScissor(left - 2, treeTop, right, treeBottom);
		for (int i = 0; i < tree.size(); i++) {
			TreeRow row = tree.get(i);
			int top = treeTop + i * TREE_ROW - treeScroll.scroll();
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
			drawSmallIcon(graphics, ItemIcon.of(row.node.icon()), left + indent + 9, top + 4);
			drawTreeName(graphics, row.node.name(), left + indent + 22, top + 6, cellRight - left - indent - 26);
		}
		graphics.disableScissor();
		treeScroll.render(graphics, right - 2);
	}

	private void drawSmallIcon(GuiGraphicsExtractor graphics, ItemStack icon, int x, int y) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(.75F, .75F);
		graphics.item(icon, 0, 0);
		graphics.pose().popMatrix();
	}

	private void drawTreeName(GuiGraphicsExtractor graphics, String name, int x, int y, int maxWidth) {
		var font = font();
		float scale = .85F;
		String label = font.plainSubstrByWidth(name, Math.max(1, (int)(maxWidth / scale)));
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y + (font.lineHeight - font.lineHeight * scale) / 2);
		graphics.pose().scale(scale, scale);
		graphics.text(font, label, 0, 0, PaperStyle.INK, false);
		graphics.pose().popMatrix();
	}

	private void drawGridIcon(GuiGraphicsExtractor graphics, ItemStack icon, int iconX, int iconY) {
		var font = font();
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
		var font = font();
		String text = Integer.toString(entry.price());
		int width = 12 + font.width(text);
		int left = px + (cell - width) / 2;
		ItemStack icon = ItemIcon.currency(Smp3Currency.byId(entry.currency()));
		graphics.pose().pushMatrix();
		graphics.pose().translate(left, py);
		graphics.pose().scale(10F / 16F, 10F / 16F);
		graphics.item(icon, 0, 0);
		graphics.pose().popMatrix();
		graphics.text(font, text, left + 12, py + 1, PaperStyle.INK, false);
	}

	private void drawFittedName(GuiGraphicsExtractor graphics, String text, int centerX, int y, int maxWidth) {
		var font = font();
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

	private void drawProducts(GuiGraphicsExtractor graphics, int mouseX, int mouseY, PanelContext context) {
		var font = font();
		var entries = entries();
		var folders = childNodes();
		int size = folders.size() + entries.size();
		int cell = cellSize(), columns = gridColumns();
		int rows = (size + columns - 1) / columns;
		gridContent = Math.max(0, rows * (cell + GRID_GAP) - GRID_GAP);
		gridScroll.layout(bodyTop, bodyBottom, gridContent);
		if (size == 0) {
			graphics.text(font, "暂无商品", contentLeft, bodyTop + 12, PaperStyle.MUTED, false);
			return;
		}
		graphics.enableScissor(contentLeft, bodyTop, contentLeft + contentWidth, bodyBottom);
		for (int i = 0; i < size; i++) {
			int col = i % columns, row = i / columns;
			int left = contentLeft + col * (cell + GRID_GAP);
			int top = bodyTop + row * (cell + GRID_GAP) - gridScroll.scroll();
			if (top + cell < bodyTop || top >= bodyBottom)
				continue;
			if (i < folders.size()) {
				var child = folders.get(i);
				boolean hovered = mouseX >= left && mouseX < left + cell && mouseY >= top && mouseY < top + cell;
				if (hovered)
					graphics.fill(left, top, left + cell, top + cell, 0x227F5936);
				drawGridIcon(graphics, ItemIcon.of(child.icon()), left + (cell - 32) / 2, top + 6);
				String name = child.name();
				drawFittedName(graphics, name, left + cell / 2 + NAME_CENTER_OFFSET, top + 44, cell - 8);
				continue;
			}
			var entry = entries.get(i - folders.size());
			boolean selected = modal && entry.id().equals(state.selected);
			boolean hovered = mouseX >= left && mouseX < left + cell && mouseY >= top && mouseY < top + cell;
			if (selected || hovered)
				graphics.fill(left, top, left + cell, top + cell, selected ? 0x447F5936 : 0x227F5936);
			ItemStack icon = ItemIcon.of(entry.icon());
			int iconX = left + (cell - 32) / 2, iconY = top + 5;
			ItemStack shown = entry.amount() > 1 && !entry.title() ? icon.copyWithCount(entry.amount()) : icon;
			drawGridIcon(graphics, shown, iconX, iconY);
			String name = icon.getHoverName().getString();
			drawFittedName(graphics, name, left + cell / 2 + NAME_CENTER_OFFSET, top + 40, cell - 8);
			drawPrice(graphics, entry, left, top + 51, cell);
			if (hovered && !modal) {
				var lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), icon));
				entry.description().forEach(line -> lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY)));
				lines.add(Component.literal(entry.title() ? "解锁称号"
				                            : entry.buy() ? "购买物品"
				                                          : "出售物品")
				              .withStyle(ChatFormatting.GRAY));
				lines.add(Component.literal(entry.availability()).withStyle(ChatFormatting.GRAY));
				graphics.setTooltipForNextFrame(font, lines, Optional.empty(), context.toScreenX(mouseX),
				                                context.toScreenY(mouseY));
			}
		}
		graphics.disableScissor();
		gridScroll.render(graphics, contentLeft + contentWidth - 2);
	}

	private void drawModal(GuiGraphicsExtractor graphics) {
		var font = font();
		var entry = state.entries.get(state.selected);
		if (entry == null)
			return;
		graphics.fill(x(), y(), right(), bottom(), 0x99000000);
		graphics.fill(modalX, modalY, modalX + modalWidth, modalY + MODAL_HEIGHT, 0xFFF2E7CF);
		graphics.outline(modalX, modalY, modalWidth, MODAL_HEIGHT, 0xFF8C5D38);
		var quote = state.quote;
		boolean pending = state.pending || quote == null;
		String period = quote != null && quote.weekly() ? "本周" : "今日";
		String header = pending             ? period + "还可交易 …"
		                : quote.owned() < 0 ? "账户暂不可用"
		                                    : period + "还可交易 " + quote.maximum() + " 批";
		graphics.text(font,
		              Component.literal(font.plainSubstrByWidth(header, modalWidth - 28))
		                  .withStyle(style -> style.withBold(true)),
		              modalX + 14, modalY + 12, PaperStyle.INK, false);
		graphics.text(font, entry.buy() ? "购买批数" : "出售批数", modalX + 14, modalY + 30, PaperStyle.MUTED, false);
		graphics.horizontalLine(modalX + 14, modalX + modalWidth - 14, modalY + 25, PaperStyle.LINE);
		if (batches != null)
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
			graphics.text(font, font.plainSubstrByWidth(message, confirm.getX() - modalX - 20), modalX + 14,
			              modalY + 88, info ? PaperStyle.INK : 0xFFAD4434, false);
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
		return entry.title() ? "解锁后可在称号菜单中佩戴" : "共 " + (long)count() * entry.amount() + " 个";
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (mouseClickedWidgets(event, doubleClick))
			return true;
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
			return false;
		if (modal) {
			if (!insideModal(event.x(), event.y())) {
				modal = false;
				onLayout();
			}
			return true;
		}
		if (gridScroll.beginDrag(event.x(), event.y(), contentLeft + contentWidth - 2))
			return true;
		int treeLeft = panelX + 20, treeRight = panelX + 14 + sidebar, treeCellRight = treeRight - 12;
		if (treeScroll.beginDrag(event.x(), event.y(), treeRight - 2))
			return true;
		if (event.x() >= treeLeft && event.x() < treeCellRight && event.y() >= treeTop && event.y() < treeBottom) {
			int row = ((int)event.y() - treeTop + treeScroll.scroll()) / TREE_ROW;
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
					onLayout();
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
		int row = (int)((mouseY - bodyTop + gridScroll.scroll()) / (cell + GRID_GAP));
		if (col >= columns || (mouseX - contentLeft) - col * (cell + GRID_GAP) >= cell)
			return -1;
		if ((mouseY - bodyTop + gridScroll.scroll()) - row * (cell + GRID_GAP) >= cell)
			return -1;
		int index = row * columns + col;
		return index < childNodes().size() + entries().size() ? index : -1;
	}

	private boolean insideModal(double mouseX, double mouseY) {
		return mouseX >= modalX && mouseX < modalX + modalWidth && mouseY >= modalY && mouseY < modalY + MODAL_HEIGHT;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
			return mouseDraggedWidgets(event, dx, dy);
		if (treeScroll.mouseDragged(event.y()) || gridScroll.mouseDragged(event.y()))
			return true;
		return mouseDraggedWidgets(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
			return mouseReleasedWidgets(event);
		if (treeScroll.mouseReleased() || gridScroll.mouseReleased())
			return true;
		return mouseReleasedWidgets(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (modal)
			return true;
		if (mouseX >= panelX + 18 && mouseX < panelX + 14 + sidebar && mouseY >= treeTop && mouseY < treeBottom) {
			treeScroll.scrollWheel(vertical, TREE_ROW);
			return true;
		}
		if (mouseX >= contentLeft && mouseX < contentLeft + contentWidth && mouseY >= bodyTop && mouseY < bodyBottom) {
			gridScroll.scrollWheel(vertical, 24);
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return keyPressedWidgets(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return charTypedWidgets(event);
	}
}
