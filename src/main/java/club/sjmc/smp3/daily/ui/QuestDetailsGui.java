package club.sjmc.smp3.daily.ui;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonPrimitive;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.gui.SimpleGui;

import club.sjmc.smp3.daily.QuestDay;
import club.sjmc.smp3.daily.QuestDefinition;
import club.sjmc.smp3.daily.QuestField;
import club.sjmc.smp3.ui.GuiStyle;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

final class QuestDetailsGui extends SimpleGui {
	private final List<GuiElement> entries = new ArrayList<>();
	private final String title;
	private int page;

	QuestDetailsGui(ServerPlayer player, QuestDay.Assignment task) {
		super(MenuType.GENERIC_9x3, player, false);
		title = task.definition.name();
		add("任务说明", "目标：" + (task.definition.completion().equals("any") ? "任选一项" : "全部完成"),
		    QuestText.lore(task));
		for (int i = 0; i < task.definition.objectives().size(); i++) {
			var goal = task.definition.objectives().get(i);
			String progress = goal.name() + " (" + task.goalProgress(i) + "/" + goal.count() + ")";
			if (goal.countBy().equals("distinct")) {
				for (String target : QuestDefinition.values(goal.target().values())
				                         .stream()
				                         .map(id -> QuestDefinition.identity(goal.target().field(), id))
				                         .distinct()
				                         .toList()) {
					boolean done = task.completedTargets(i).contains(target);
					add(progress, done ? "已完成" : "未完成",
					    QuestText.condition(goal.target().field(), new JsonPrimitive(target)));
				}
			}
			if (goal.event().equals("entity_converted"))
				add(progress, "转化归属",
				    List.of(GuiStyle.text("计入16格内最近的、持有对应未完成任务的玩家", GuiStyle.MUTED)));

			goal.objective().conditions().entrySet().forEach(condition -> {
				if (goal.countBy().equals("distinct") && condition.getKey().equals(goal.target().field()))
					return;
				String label = QuestField.find(condition.getKey()).label();
				if (condition.getValue().isJsonArray() && condition.getValue().getAsJsonArray().size() > 1)
					label += "（任选其一）";
				add(progress, label, QuestText.condition(condition.getKey(), condition.getValue()));
			});
		}
		refresh();
	}

	private void add(String name, String label, List<Component> lines) {
		for (int start = 0; start < lines.size(); start += 6) {
			var icon = GuiStyle.item(Items.PAPER, name, GuiStyle.HEADING)
			               .addLoreLine(GuiStyle.text(
			                   label + (lines.size() > 6 ? " " + (start / 6 + 1) + "/" + ((lines.size() + 5) / 6) : ""),
			                   GuiStyle.HEADING))
			               .addLoreLine(Component.empty());
			lines.subList(start, Math.min(start + 6, lines.size())).forEach(icon::addLoreLine);
			entries.add(icon.build());
		}
	}

	private void refresh() {
		setTitle(GuiStyle.windowTitle(title + " (" + (page + 1) + "/" + ((entries.size() + 17) / 18) + ")"));
		GuiStyle.fill(this, 27);
		for (int i = 0; i < 18 && page * 18 + i < entries.size(); i++)
			setSlot(i, entries.get(page * 18 + i));
		if (page > 0)
			setSlot(18, GuiStyle.button(Items.ARROW, "上一页", GuiStyle.ACTIVE, () -> {
				page--;
				refresh();
			}));
		if ((page + 1) * 18 < entries.size())
			setSlot(26, GuiStyle.button(Items.ARROW, "下一页", GuiStyle.ACTIVE, () -> {
				page++;
				refresh();
			}));
		setSlot(22, GuiStyle.back(() -> DailyQuestGui.show(player)));
	}
}
