package club.sjmc.smp3.ui.screen;

import club.sjmc.smp3.ui.panel.Panel;
import club.sjmc.smp3.ui.widget.PanelContext;
import club.sjmc.smp3.ui.widget.Rect;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 把任意 {@link Panel} 作为全屏界面的薄壳。
 */
public final class PanelScreen extends Screen {
	private final Panel panel;
	private final PanelContext context = PanelContext.fullscreen();

	public PanelScreen(Panel panel) {
		super(Component.empty());
		this.panel = panel;
	}

	public Panel panel() { return panel; }

	@Override
	protected void init() {
		panel.layout(new Rect(0, 0, width, height), context);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		panel.render(graphics, mouseX, mouseY, delta, context);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return panel.mouseClicked(event, doubleClick) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return panel.mouseReleased(event) || super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return panel.mouseDragged(event, dx, dy) || super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		return panel.mouseScrolled(mouseX, mouseY, horizontal, vertical) ||
		    super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return panel.keyPressed(event) || super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return panel.charTyped(event) || super.charTyped(event);
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		panel.mouseMoved(mouseX, mouseY);
		super.mouseMoved(mouseX, mouseY);
	}

	@Override
	public void removed() {
		panel.close();
	}

	@Override
	public boolean isPauseScreen() { return false; }

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {}
}
