package club.sjmc.smp3.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import net.minecraft.network.chat.Component;

final class HudLayoutScreen extends Screen {
	private final Screen parent;
	private boolean currency = true, dragging;
	private double grabX, grabY;
	private long lastFrame = System.nanoTime();

	HudLayoutScreen(Screen parent) {
		super(Component.literal("HUD 布局预览"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int w = Math.min(84, (width - 24) / 4), top = height - 52;
		addRenderableWidget(
		    Button
		        .builder(Component.literal("货币显示"),
				         button -> { Smp3Client.layout.currencyVisible = !Smp3Client.layout.currencyVisible; })
		        .bounds(8, top, w, 20)
		        .build());
		addRenderableWidget(Button.builder(Component.literal("任务显示"), button -> Smp3Client.toggleQuests())
		                        .bounds(12 + w, top, w, 20)
		                        .build());
		addRenderableWidget(
		    Button
		        .builder(Component.literal("公告显示"),
				         button -> { Smp3Client.layout.noticesVisible = !Smp3Client.layout.noticesVisible; })
		        .bounds(16 + w * 2, top, w, 20)
		        .build());
		addRenderableWidget(Button.builder(Component.literal("恢复默认"), button -> Smp3Client.layout = new HudLayout())
		                        .bounds(20 + w * 3, top, w, 20)
		                        .build());
		addRenderableWidget(Button.builder(Component.literal("通宝 +125"), button -> Smp3Client.PREVIEW.coins.add(125))
		                        .bounds(8, top + 24, w, 20)
		                        .build());
		addRenderableWidget(Button.builder(Component.literal("通宝 -40"), button -> Smp3Client.PREVIEW.coins.add(-40))
		                        .bounds(12 + w, top + 24, w, 20)
		                        .build());
		addRenderableWidget(Button.builder(Component.literal("印章 +1"), button -> Smp3Client.PREVIEW.stamps.add(1))
		                        .bounds(16 + w * 2, top + 24, w, 20)
		                        .build());
		addRenderableWidget(Button.builder(Component.literal("印章 -1"), button -> Smp3Client.PREVIEW.stamps.add(-1))
		                        .bounds(20 + w * 3, top + 24, w, 20)
		                        .build());
		addRenderableWidget(
		    Button.builder(Component.literal("完成"), button -> onClose()).bounds(10, 46, 58, 20).build());
	}

	private HudLayout.Box currencyBox() {
		return Smp3Client.layout.currency.box(width, height, HudPreview.CURRENCY_WIDTH, 20, HudLayout.statusSpace());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float seconds = Math.min(.05F, (now - lastFrame) / 1e9F);
		lastFrame = now;
		graphics.fill(0, 0, width, height, 0x33000000);
		graphics.text(font, "HUD 布局预览", 10, 10, HudLayout.TEXT);
		graphics.text(font, "拖动区域，滚轮缩放。下方按钮模拟金额变化。", 10, 29, HudLayout.MUTED);
		Smp3Client.HUD.draw(graphics, seconds);
		outline(graphics, currencyBox(), currency, "货币");
		outline(graphics, Smp3Client.HUD.box(width, height), !currency, "任务与通知");
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private void outline(GuiGraphicsExtractor graphics, HudLayout.Box box, boolean selected, String label) {
		int color = selected ? 0xFFE6CA8A : HudLayout.MUTED;
		graphics.outline(box.x() - 3, box.y() - 3, box.width() + 6, box.height() + 6, color);
		graphics.text(font, label + " " + Math.round(box.scale() * 100) + "%", box.x(),
		              box.y() < 42 ? box.y() + box.height() + 5 : box.y() - 14, color);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick))
			return true;
		if (event.button() != 0)
			return false;
		var money = currencyBox();
		var quests = Smp3Client.HUD.box(width, height);
		if (!money.contains(event.x(), event.y()) && !quests.contains(event.x(), event.y()))
			return false;
		currency = money.contains(event.x(), event.y());
		var box = currency ? money : quests;
		grabX = event.x() - box.x();
		grabY = event.y() - box.y();
		dragging = true;
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (!dragging)
			return super.mouseDragged(event, dx, dy);
		var placement = currency ? Smp3Client.layout.currency : Smp3Client.layout.quests;
		placement.move(event.x() - grabX, event.y() - grabY, width, height,
		               currency ? currencyBox() : Smp3Client.HUD.box(width, height),
		               currency ? HudLayout.statusSpace() : 0);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		var placement = currency ? Smp3Client.layout.currency : Smp3Client.layout.quests;
		placement.scale = Math.clamp(placement.scale + (float)vertical * .1F, .6F, 1.4F);
		return true;
	}

	@Override
	public void removed() {
		Smp3Client.savePreferences();
	}
	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
	@Override
	public boolean isPauseScreen() {
		return false;
	}
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {}
}
