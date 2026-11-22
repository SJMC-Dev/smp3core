package club.sjmc.smp3.ui.screen;

import com.mojang.blaze3d.platform.InputConstants;

import club.sjmc.smp3.ui.Smp3Client;
import club.sjmc.smp3.ui.hud.HudLayout;
import club.sjmc.smp3.ui.widget.PaperButton;
import club.sjmc.smp3.ui.widget.Rect;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public final class HudSettingsScreen extends Screen {
	private HudLayout.Placement dragging;
	private HudLayout.Box dragBox;
	private double grabX, grabY;
	private int panelX, panelY;

	public HudSettingsScreen() { super(Component.translatable("smp3.hud.settings")); }

	@Override
	protected void init() {
		dragging = null;
		panelX = (width - 296) / 2;
		panelY = height - 126;
		addRenderableWidget(new PaperButton(panelX + 10, panelY + 38, 134,
		                                    text(Smp3Client.hud().visible ? "smp3.hud.hide" : "smp3.hud.show"), false,
		                                    () -> {
			                                    Smp3Client.toggleHud();
			                                    rebuildWidgets();
		                                    }));
		addRenderableWidget(
		    new PaperButton(panelX + 152, panelY + 38, 134,
			                text(Smp3Client.layout().followQuests ? "smp3.hud.detach" : "smp3.hud.attach"), false, () -> {
			                    if (Smp3Client.layout().followQuests) {
				                    var notice = Smp3Client.hud().noticeBox(width, height, true);
				                    Smp3Client.layout().notices.scale = Smp3Client.layout().quests.scale;
				                    Smp3Client.layout().notices.move(notice.x(), notice.y(), notice, width, height);
			                    }
			                    Smp3Client.layout().followQuests = !Smp3Client.layout().followQuests;
			                    Smp3Client.savePreferences();
			                    rebuildWidgets();
		                    }));
		addRenderableWidget(
		    new PaperButton(panelX + 10, panelY + 64, 134, text("smp3.hud.bindings"), false,
			                () -> minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options))));
		addRenderableWidget(new PaperButton(panelX + 152, panelY + 64, 134, text("smp3.hud.reset"), false, () -> {
			var defaults = new HudLayout();
			Smp3Client.layout().quests = defaults.quests;
			Smp3Client.layout().notices = defaults.notices;
			Smp3Client.layout().followQuests = true;
			Smp3Client.savePreferences();
			rebuildWidgets();
		}));
		addRenderableWidget(new PaperButton(panelX + 10, panelY + 90, 276, text("gui.done"), false, this::onClose));
	}

	@Override
	public void removed() {
		Smp3Client.savePreferences();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0x44000000);
		var quests = Smp3Client.hud().box(width, height, true);
		var notices = Smp3Client.hud().noticeBox(width, height, true);
		panel(graphics, quests, text("smp3.hud.quests"), mouseX, mouseY);
		panel(graphics, notices, text("smp3.hud.notices"), mouseX, mouseY);
		graphics.fill(panelX, panelY, panelX + 296, panelY + 118, 0xFFEBDDBF);
		graphics.outline(panelX, panelY, 296, 118, 0xFF8C5D38);
		graphics.text(font, title, panelX + 10, panelY + 9, 0xFF443323, false);
		graphics.text(font, text("smp3.hud.drag"), panelX + 10, panelY + 23, 0xFF827058, false);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private void panel(GuiGraphicsExtractor graphics, HudLayout.Box box, String label, int mouseX, int mouseY) {
		graphics.fill(box.x(), box.y(), box.x() + box.width(), box.y() + box.height(), HudLayout.PANEL);
		graphics.outline(box.x(), box.y(), box.width(), box.height(),
		                 box.contains(mouseX, mouseY) ? HudLayout.TEXT : HudLayout.MUTED);
		graphics.pose().pushMatrix();
		graphics.pose().translate(box.x(), box.y());
		graphics.pose().scale(box.scale(), box.scale());
		graphics.text(font, font.plainSubstrByWidth(label, Math.round(box.width() / box.scale()) - 8), 4, 4,
		              HudLayout.TEXT);
		graphics.pose().popMatrix();
	}

	@Override
	public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick))
			return true;
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
			return false;
		if (new Rect(panelX, panelY, 296, 118).contains(event.x(), event.y()))
			return true;
		var quests = Smp3Client.hud().box(width, height, true);
		var notices = Smp3Client.hud().noticeBox(width, height, true);
		if (!Smp3Client.layout().followQuests && notices.contains(event.x(), event.y())) {
			dragging = Smp3Client.layout().notices;
			dragBox = notices;
		} else if (quests.contains(event.x(), event.y())) {
			dragging = Smp3Client.layout().quests;
			dragBox = quests;
		} else
			return false;
		grabX = event.x() - dragBox.x();
		grabY = event.y() - dragBox.y();
		return true;
	}

	@Override
	public boolean mouseDragged(@NotNull MouseButtonEvent event, double dx, double dy) {
		if (dragging == null)
			return super.mouseDragged(event, dx, dy);
		dragging.move(event.x() - grabX, event.y() - grabY, dragBox, width, height);
		return true;
	}

	@Override
	public boolean mouseReleased(@NotNull MouseButtonEvent event) {
		if (dragging == null)
			return super.mouseReleased(event);
		dragging = null;
		Smp3Client.savePreferences();
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
	@Override
	public void extractBackground(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {}

	private static String text(String key) { return Component.translatable(key).getString(); }
}
