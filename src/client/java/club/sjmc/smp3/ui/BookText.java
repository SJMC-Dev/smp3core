package club.sjmc.smp3.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import club.sjmc.smp3.notice.NoticePayloads;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

final class BookText {
	private static final Pattern INLINE = Pattern.compile("\\*\\*(.+?)\\*\\*|`([^`]+)`|\\[([^\\]]+)\\]\\(([^)]+)\\)");
	private static final Pattern LIST = Pattern.compile("^([ \\t]*)-[ \\t]+");
	private BookText() {}

	static List<FormattedCharSequence> lines(NoticePayloads.Body body, Font font, int width) {
		var result = new ArrayList<FormattedCharSequence>();
		for (String line : body.text().split("\n", -1)) {
			MutableComponent text;
			if (!body.markdown()) {
				text = Component.literal(line);
			} else {
				boolean heading = line.matches("#{1,3} .*");
				if (heading)
					line = line.substring(line.indexOf(' ') + 1);
				else
					line = LIST.matcher(line).replaceFirst("$1• ");
				text = inline(line);
				if (heading)
					text.withStyle(style -> style.withBold(true));
			}
			if (line.isEmpty())
				result.add(FormattedCharSequence.EMPTY);
			else
				result.addAll(font.split(text, width));
		}
		return List.copyOf(result);
	}

	private static MutableComponent inline(String line) {
		MutableComponent text = Component.empty();
		var matcher = INLINE.matcher(line);
		int end = 0;
		while (matcher.find()) {
			text.append(line.substring(end, matcher.start()));
			if (matcher.group(1) != null)
				text.append(Component.literal(matcher.group(1)).withStyle(style -> style.withBold(true)));
			else if (matcher.group(2) != null)
				text.append(Component.literal(matcher.group(2)).withStyle(style -> style.withColor(0x78522E)));
			else
				text.append(matcher.group(3) + " (" + matcher.group(4) + ")");
			end = matcher.end();
		}
		return text.append(line.substring(end));
	}
}
