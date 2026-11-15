package club.sjmc.smp3.client;

import java.util.List;
import java.util.stream.IntStream;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

final class ServerBookScreen extends Screen {
	private static final int INK = 0xFF443323, MUTED = 0xFF827058;
	private static final List<String> CATEGORIES = List.of("全部", "公告", "服规", "帮助");
	private final HudPreview preview = Smp3Client.PREVIEW;
	private int x, y, bookWidth, bookHeight, sidebar, bodyTop, bodyBottom, listScroll, textScroll;
	private boolean narrow, detail;
	private List<Integer> entries;
	private Button acknowledge;

	ServerBookScreen() { super(Component.literal("服务器手册")); }

	@Override
	protected void init() {
		x = Math.max(8, (width - 600) / 2);
		y = Math.max(8, (height - 354) / 2);
		bookWidth = width - 2 * x;
		bookHeight = height - 2 * y;
		narrow = bookWidth < 400;
		sidebar = narrow ? bookWidth - 28 : Math.min(174, bookWidth / 3);
		bodyTop = y + 65;
		bodyBottom = y + bookHeight - 47;
		entries = IntStream.range(0, preview.messages.size())
		              .filter(i
		                      -> preview.category == 0 ||
		                             preview.messages.get(i).category().equals(CATEGORIES.get(preview.category)))
		              .boxed()
		              .toList();
		if (!entries.contains(preview.selected)) {
			preview.selected = entries.getFirst();
			textScroll = 0;
		}
		listScroll = Math.clamp(listScroll, 0, Math.max(0, entries.size() * 40 - (bodyBottom - bodyTop)));
		int tabWidth = (sidebar - 8) / 4;
		if (!narrow || !detail) {
			for (int i = 0; i < CATEGORIES.size(); i++) {
				int category = i;
				var button = Button
				                 .builder(Component.literal(CATEGORIES.get(i)),
				                          ignored -> {
					                          preview.category = category;
					                          listScroll = 0;
					                          rebuildWidgets();
				                          })
				                 .bounds(x + 14 + i * tabWidth, y + 38, tabWidth - 2, 18)
				                 .build();
				button.active = preview.category != i;
				addRenderableWidget(button);
			}
		}
		if (narrow && detail)
			addRenderableWidget(Button
			                        .builder(Component.literal("返回目录"),
			                                 button -> {
				                                 detail = false;
				                                 rebuildWidgets();
			                                 })
			                        .bounds(x + 14, y + 38, 78, 18)
			                        .build());
		int footer = y + bookHeight - 31;
		addRenderableWidget(
		    Button.builder(Component.literal("HUD 设置"), button -> minecraft.gui.setScreen(new HudLayoutScreen(this)))
		        .bounds(x + 14, footer, 76, 20)
		        .build());
		addRenderableWidget(Button
		                        .builder(Component.literal("退出预览"),
		                                 button -> {
			                                 preview.clear();
			                                 onClose();
		                                 })
		                        .bounds(x + 96, footer, 76, 20)
		                        .build());
		addRenderableWidget(Button.builder(Component.literal("关闭"), button -> onClose())
		                        .bounds(x + bookWidth - 72, footer, 58, 20)
		                        .build());
		acknowledge = Button
		                  .builder(Component.literal("我已阅读并确认"),
		                           button -> {
			                           preview.acknowledged.add(preview.selected);
			                           updateAcknowledgement();
		                           })
		                  .bounds(contentLeft(), bodyBottom - 22, Math.min(134, contentWidth()), 20)
		                  .build();
		addRenderableWidget(acknowledge);
		if (!narrow || detail)
			preview.opened.add(preview.selected);
		updateAcknowledgement();
	}

	private int contentLeft() { return narrow ? x + 22 : x + sidebar + 30; }
	private int contentWidth() { return x + bookWidth - 24 - contentLeft(); }

	private void updateAcknowledgement() {
		acknowledge.visible = (!narrow || detail) && preview.messages.get(preview.selected).acknowledgement();
		acknowledge.active = !preview.acknowledged.contains(preview.selected);
		acknowledge.setMessage(Component.literal(acknowledge.active ? "我已阅读并确认" : "已确认"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0x66000000);
		graphics.fill(x + 4, y + 5, x + bookWidth + 4, y + bookHeight + 5, 0x77000000);
		graphics.fillGradient(x, y, x + bookWidth, y + bookHeight, 0xFF79523A, 0xFF3F2A20);
		graphics.outline(x + 1, y + 1, bookWidth - 2, bookHeight - 2, 0xFFAD8053);
		graphics.fill(x + 5, y + 4, x + 9, y + bookHeight - 4, 0xFF39271F);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BookViewScreen.BOOK_LOCATION, x + 11, y + 8, 32, 20, bookWidth - 20,
		              bookHeight - 16, 128, 140, 256, 256);
		graphics.text(font, "SMP3  服务器手册", x + 22, y + 20, INK, false);
		graphics.text(font, "界面预览", x + bookWidth - 72, y + 20, MUTED, false);
		graphics.horizontalLine(x + 20, x + bookWidth - 20, y + 33, 0xFFB7A17D);
		graphics.horizontalLine(x + 20, x + bookWidth - 20, bodyBottom + 9, 0xFFB7A17D);
		if (!narrow) {
			graphics.fill(x + sidebar + 14, y + 39, x + sidebar + 16, bodyBottom, 0x66836B48);
			graphics.fill(x + sidebar + 16, y + 39, x + sidebar + 19, bodyBottom, 0x22765A37);
		}
		if (!narrow || !detail)
			drawList(graphics, mouseX, mouseY);
		if (!narrow || detail)
			drawMessage(graphics);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private void drawList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int left = x + 16, right = x + sidebar + 7;
		graphics.enableScissor(left, bodyTop, right, bodyBottom);
		for (int row = 0; row < entries.size(); row++) {
			int index = entries.get(row), top = bodyTop + row * 40 - listScroll;
			var message = preview.messages.get(index);
			boolean selected = index == preview.selected;
			if (selected || mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + 37)
				graphics.fill(left, top, right - 4, top + 37, selected ? 0x447F5936 : 0x227F5936);
			if (selected)
				graphics.fill(left, top + 3, left + 2, top + 34, 0xFF8C4B30);
			if (preview.unread(index))
				graphics.fill(right - 13, top + 9, right - 9, top + 13, 0xFFB54536);
			graphics.text(font, font.plainSubstrByWidth(message.title(), right - left - 23), left + 7, top + 7, INK,
			              false);
			graphics.text(font, message.category() + (preview.unread(index) ? "  未读" : "  已读"), left + 7, top + 23,
			              MUTED, false);
		}
		graphics.disableScissor();
		scrollbar(graphics, right - 2, bodyTop, bodyBottom, entries.size() * 40, listScroll);
	}

	private void drawMessage(GuiGraphicsExtractor graphics) {
		var message = preview.messages.get(preview.selected);
		int left = contentLeft();
		graphics.text(font, font.plainSubstrByWidth(message.title(), contentWidth()), left, narrow ? bodyTop : y + 42,
		              INK, false);
		graphics.text(font, message.date(), left, bodyTop + (narrow ? 16 : 0), MUTED, false);
		int top = bodyTop + (narrow ? 34 : 20), bottom = bodyBottom - (message.acknowledgement() ? 30 : 0);
		var lines = font.split(Component.literal(message.body()), contentWidth() - 10);
		textScroll = Math.clamp(textScroll, 0, Math.max(0, lines.size() * 14 - (bottom - top)));
		graphics.enableScissor(left, top, left + contentWidth(), bottom);
		for (int i = 0; i < lines.size(); i++)
			graphics.text(font, lines.get(i), left, top + i * 14 - textScroll, INK, false);
		graphics.disableScissor();
		scrollbar(graphics, left + contentWidth() - 3, top, bottom, lines.size() * 14, textScroll);
	}

	private static void scrollbar(GuiGraphicsExtractor graphics, int x, int top, int bottom, int content, int scroll) {
		int height = bottom - top;
		if (content <= height || height <= 0)
			return;
		int thumb = Math.max(12, height * height / content);
		int y = top + (height - thumb) * scroll / (content - height);
		graphics.fill(x, top, x + 2, bottom, 0x44765A37);
		graphics.fill(x, y, x + 2, y + thumb, 0xFF9B7B51);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick))
			return true;
		if (event.button() == 0 && (!narrow || !detail) && event.x() >= x + 16 && event.x() < x + sidebar + 7 &&
		    event.y() >= bodyTop && event.y() < bodyBottom) {
			int row = ((int)event.y() - bodyTop + listScroll) / 40;
			if (row < entries.size()) {
				preview.selected = entries.get(row);
				preview.opened.add(preview.selected);
				textScroll = 0;
				detail = true;
				rebuildWidgets();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (mouseY < bodyTop || mouseY > bodyBottom)
			return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
		if ((!narrow || !detail) && mouseX >= x + 16 && mouseX < x + sidebar + 7)
			listScroll = Math.clamp(listScroll - (int)(vertical * 28), 0,
			                        Math.max(0, entries.size() * 40 - (bodyBottom - bodyTop)));
		else if ((!narrow || detail) && mouseX >= contentLeft() && mouseX < contentLeft() + contentWidth())
			textScroll = Math.max(0, textScroll - (int)(vertical * 28));
		else
			return false;
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {}
}
