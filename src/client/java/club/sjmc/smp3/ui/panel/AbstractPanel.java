package club.sjmc.smp3.ui.panel;

import java.util.ArrayList;
import java.util.List;

import club.sjmc.smp3.ui.widget.PanelContext;
import club.sjmc.smp3.ui.widget.Rect;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * 提供面板边界、坐标上下文、布局钩子与自管 widget 列表。
 */
public abstract class AbstractPanel implements Panel {
	protected Rect bounds = new Rect(0, 0, 0, 0);
	protected final List<AbstractWidget> widgets = new ArrayList<>();

	@Override
	public void layout(Rect bounds, PanelContext context) {
		this.bounds = bounds;
		onLayout();
	}

	protected void onLayout() {}

	protected int x() { return bounds.x(); }

	protected int y() { return bounds.y(); }

	protected int width() { return bounds.width(); }

	protected int height() { return bounds.height(); }

	protected int right() { return bounds.right(); }

	protected int bottom() { return bounds.bottom(); }

	protected <T extends AbstractWidget> T addWidget(T widget) {
		widgets.add(widget);
		return widget;
	}

	protected void clearWidgets() { widgets.clear(); }

	protected void renderWidgets(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		for (var widget : widgets)
			if (widget.visible)
				widget.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	protected boolean mouseClickedWidgets(MouseButtonEvent event, boolean doubleClick) {
		for (var widget : List.copyOf(widgets))
			if (widget.visible && widget.mouseClicked(event, doubleClick)) {
				widgets.forEach(child -> child.setFocused(child == widget));
				return true;
			}
		widgets.forEach(widget -> widget.setFocused(false));
		return false;
	}

	protected boolean mouseReleasedWidgets(MouseButtonEvent event) {
		for (var widget : List.copyOf(widgets))
			if (widget.visible && widget.isFocused() && widget.mouseReleased(event))
				return true;
		return false;
	}

	protected boolean mouseDraggedWidgets(MouseButtonEvent event, double dx, double dy) {
		for (var widget : List.copyOf(widgets))
			if (widget.visible && widget.isFocused() && widget.mouseDragged(event, dx, dy))
				return true;
		return false;
	}

	protected boolean keyPressedWidgets(KeyEvent event) {
		for (var widget : List.copyOf(widgets))
			if (widget.visible && widget.isFocused() && widget.keyPressed(event))
				return true;
		return false;
	}

	protected boolean charTypedWidgets(CharacterEvent event) {
		for (var widget : List.copyOf(widgets))
			if (widget.visible && widget.isFocused() && widget.charTyped(event))
				return true;
		return false;
	}
}
