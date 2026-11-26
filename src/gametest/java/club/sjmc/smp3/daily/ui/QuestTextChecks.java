package club.sjmc.smp3.daily.ui;

import java.util.stream.Collectors;

import club.sjmc.smp3.daily.QuestDay;

import net.minecraft.network.chat.Component;

public final class QuestTextChecks {
	public static String render(QuestDay.Assignment task) {
		return QuestText.conditions(task).stream().map(Component::getString).collect(Collectors.joining());
	}
}
