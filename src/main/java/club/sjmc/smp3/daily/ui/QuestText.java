package club.sjmc.smp3.daily.ui;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;

import club.sjmc.smp3.daily.QuestDay;
import club.sjmc.smp3.daily.QuestDefinition;
import club.sjmc.smp3.daily.QuestField;
import club.sjmc.smp3.gui.GuiStyle;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

final class QuestText {
	private QuestText() {}

	static List<Component> lore(QuestDay.Assignment task) {
		List<Component> parts = new ArrayList<>();
		if (!task.definition.objectives().isEmpty()) {
			if (task.definition.description() != null)
				parts.add(Component.literal(task.definition.description()));
			else
				parts.add(Component.literal(task.definition.completion().equals("any") ? "完成任意一个子目标。"
				                                                                       : "完成全部子目标。"));
			return wrap(parts);
		}
		var conditions = task.definition.objective().conditions();
		String description = task.definition.description();
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

	static List<Component> condition(String key, JsonElement value) { return wrap(targets(key, value)); }

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
				case "potion", "pattern", "structure" -> Component.literal(target);
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
