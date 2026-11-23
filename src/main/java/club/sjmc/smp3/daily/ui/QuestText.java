package club.sjmc.smp3.daily.ui;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import club.sjmc.smp3.daily.QuestDay;
import club.sjmc.smp3.daily.QuestDefinition;
import club.sjmc.smp3.daily.QuestField;
import club.sjmc.smp3.ui.GuiStyle;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

final class QuestText {
	private QuestText() {}

	record Detail(String name, String label, List<Component> lines) {}

	static List<Detail> details(QuestDay.Assignment task) {
		List<Detail> details = new ArrayList<>();
		details.add(new Detail(
		    "任务说明", "目标：" + (task.definition.completion().equals("any") ? "任选一项" : "全部完成"), lore(task)));
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
					details.add(new Detail(progress, done ? "已完成" : "未完成",
					                       condition(goal.target().field(), new JsonPrimitive(target))));
				}
			}
			if (goal.event().equals("entity_converted"))
				details.add(
				    new Detail(progress, "转化归属",
					           List.of(GuiStyle.text("计入16格内最近的、持有对应未完成任务的玩家", GuiStyle.MUTED))));

			goal.objective().conditions().entrySet().forEach(condition -> {
				if (goal.countBy().equals("distinct") && condition.getKey().equals(goal.target().field()))
					return;
				String label = QuestField.find(condition.getKey()).label();
				if (condition.getValue().isJsonArray() && condition.getValue().getAsJsonArray().size() > 1)
					label += "（任选其一）";
				details.add(new Detail(progress, label, condition(condition.getKey(), condition.getValue())));
			});
		}
		return details;
	}

	private static List<Component> lore(QuestDay.Assignment task) {
		List<Component> parts = new ArrayList<>();
		if (task.definition.objectives().size() > 1) {
			if (task.definition.description() != null)
				parts.add(Component.literal(task.definition.description()));
			else
				parts.add(Component.literal(task.definition.completion().equals("any") ? "完成任意一个子目标。"
				                                                                       : "完成全部子目标。"));
			return wrap(parts);
		}
		var goal = task.definition.objectives().getFirst();
		var conditions = goal.objective().conditions();
		String description = task.definition.description();
		if (description != null && goal.target() != null &&
		    (goal.selection().equals("random") || goal.countBy().equals("distinct")) &&
		    !description.contains("{" + goal.target().field() + "}")) {
			parts.add(Component.literal(goal.countBy().equals("distinct") ? "不同目标（需 " + goal.count() + " 种）："
			                                                              : "本次目标："));
			parts.addAll(targets(goal.target().field(), goal.target().values()));
			parts.add(Component.literal("\n"));
		}
		if (description == null) {
			conditions.entrySet().forEach(condition -> {
				parts.add(Component.literal(QuestField.find(condition.getKey()).label() + "："));
				parts.addAll(targets(condition.getKey(), condition.getValue()));
				parts.add(Component.literal("\n"));
			});
		} else {
			var fields = QuestDefinition.DESCRIPTION_FIELD.matcher(description);
			int offset = 0;
			while (fields.find()) {
				parts.add(Component.literal(description.substring(offset, fields.start())));
				String field = fields.group(1);
				if (field.equals("count"))
					parts.add(Component.literal(Integer.toString(task.target)));
				else
					parts.addAll(targets(field, conditions.get(field)));
				offset = fields.end();
			}
			parts.add(Component.literal(description.substring(offset)));
		}
		return wrap(parts);
	}

	private static List<Component> condition(String key, JsonElement value) { return wrap(targets(key, value)); }

	private static List<Component> targets(String key, JsonElement value) {
		List<Component> parts = new ArrayList<>();
		if (key.equals("block_state")) {
			value.getAsJsonObject().entrySet().forEach(
			    p
			    -> parts.add(Component.literal((p.getKey().equals("age") ? "生长阶段" : p.getKey()) + "=" +
				                               p.getValue().getAsString() + " ")));
			return parts;
		}
		for (String target : QuestDefinition.values(value)) {
			if (!parts.isEmpty())
				parts.add(Component.literal(" / "));
			if (target.startsWith("#")) {
				parts.add(Component.literal("标签 " + target.substring(1)));
				continue;
			}
			if (key.equals("source")) {
				parts.add(Component.literal(switch (target) {
					case "crafting" -> "合成";
					case "smelting" -> "熔炼";
					case "brewing" -> "酿造";
					case "smithing" -> "锻造";
					case "stonecutting" -> "切石";
					case "loom" -> "织布";
					default -> target;
				}));
				continue;
			}
			Identifier id = Identifier.parse(target);
			parts.add(switch (key) {
				case "dimension" ->
					Component.literal(switch (target) {
						case "minecraft:overworld" -> "主世界";
						case "minecraft:the_nether" -> "下界";
						case "minecraft:the_end" -> "末地";
						default -> target;
					});
				case "potion", "pattern", "structure", "variant" -> Component.literal(target);
				case "profession" -> Component.translatable("entity.minecraft.villager." + id.getPath());
				case "result" -> BuiltInRegistries.ENTITY_TYPE.getValue(id).getDescription();
				case "movement" ->
					Component.literal(switch (target) {
						case "sprint" -> "疾跑";
						case "swim" -> "游泳";
						case "elytra" -> "鞘翅滑翔";
						default -> "爬行";
					});
				case "action" ->
					Component.literal(switch (target) {
						case "talk" -> "交谈";
						case "repair" -> "修复";
						default -> "刷取";
					});
				case "item", "template" -> new ItemStack(BuiltInRegistries.ITEM.getValue(id)).getHoverName();
				default -> Component.translatable(key + "." + id.getNamespace() + "." + id.getPath().replace('/', '.'));
			});
		}
		return parts;
	}

	private static List<Component> wrap(List<Component> parts) {
		List<Component> tokens = new ArrayList<>();
		for (Component part : parts) {
			if (part.getContents() instanceof PlainTextContents literal) {
				literal.text().codePoints().forEach(c -> tokens.add(Component.literal(Character.toString(c))));
			} else
				tokens.add(part);
		}
		List<Component> lines = new ArrayList<>();
		var line = Component.empty().withStyle(GuiStyle.MUTED);
		int width = 0;
		StringBuilder text = new StringBuilder();
		for (Component token : tokens) {
			String value = token.getString();
			int size = value.codePoints().map(c -> c < 128 ? 1 : 2).sum();
			boolean newline = value.equals("\n");
			if (newline || width > 0 && width + size > 40) {
				line.append(text.toString());
				text.setLength(0);
				lines.add(line);
				line = Component.empty().withStyle(GuiStyle.MUTED);
				width = 0;
			}
			if (newline)
				continue;
			if (token.getContents() instanceof PlainTextContents)
				text.append(value);
			else {
				line.append(text.toString()).append(token);
				text.setLength(0);
			}
			width += size;
		}
		if (width > 0)
			lines.add(line.append(text.toString()));
		return lines;
	}
}
