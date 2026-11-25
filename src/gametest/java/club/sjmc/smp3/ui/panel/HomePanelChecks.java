package club.sjmc.smp3.ui.panel;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import club.sjmc.smp3.home.HomePayloads;
import club.sjmc.smp3.ui.widget.PanelContext;
import club.sjmc.smp3.ui.widget.Rect;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

public final class HomePanelChecks {
	public static void run() {
		var home = new HomePayloads.Entry("河边的家", "minecraft:overworld", 10, 64, 20);
		var panel = new HomePanel(new HomePayloads.Catalog(List.of(home), true, "", 2));
		panel.layout(new Rect(0, 0, 300, 200), new PanelContext(0, 0));
		var left = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
		if (!panel.mouseClicked(new MouseButtonEvent(30, 40, left), false))
			throw new AssertionError("Left-click did not select a home");
		var input = (EditBox)panel.widgets.stream().filter(EditBox.class ::isInstance).findFirst().orElseThrow();
		if (!input.getValue().equals(home.name()))
			throw new AssertionError("Selected home did not populate the name field");
		var update = panel.widgets.stream()
		                 .filter(widget -> widget.getMessage().getString().equals("更新位置"))
		                 .findFirst()
		                 .orElseThrow();
		if (!update.active ||
		    !panel.mouseClicked(new MouseButtonEvent(update.getX() + 5, update.getY() + 5, left), false) ||
		    panel.widgets.stream().noneMatch(widget -> widget.getMessage().getString().equals("确认更新")))
			throw new AssertionError("Selected home action did not open its confirmation");
	}
}
