package club.sjmc.smp3.ui.panel;

import club.sjmc.smp3.ui.widget.PanelContext;
import club.sjmc.smp3.ui.widget.Rect;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * 可嵌入菜单或全屏宿主的界面面板。坐标均为面板局部坐标。
 */
public interface Panel {
	void layout(Rect bounds, PanelContext context);

	void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, PanelContext context);

	default boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) { return false; }

	default boolean mouseReleased(MouseButtonEvent event) { return false; }

	default boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { return false; }

	default boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) { return false; }

	default boolean keyPressed(KeyEvent event) { return false; }

	default boolean charTyped(CharacterEvent event) { return false; }

	default void mouseMoved(double mouseX, double mouseY) {}

	default void close() {}
}
