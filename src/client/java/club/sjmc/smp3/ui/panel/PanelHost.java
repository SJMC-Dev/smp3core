package club.sjmc.smp3.ui.panel;

import club.sjmc.smp3.ui.widget.PanelContext;

/**
 * 面板宿主需要提供的能力：坐标上下文与关闭请求。
 */
public interface PanelHost {
	PanelContext context();

	void requestClose();
}
