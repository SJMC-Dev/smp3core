package club.sjmc.smp3.ui.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import club.sjmc.smp3.notice.NoticePayloads;
import club.sjmc.smp3.ui.themes.PaperStyle;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

public final class BookText {
	private static final Pattern INLINE = Pattern.compile("\\*\\*(.+?)\\*\\*|`([^`]+)`|\\[([^]]+)]\\(([^)]+)\\)");
	private static final Pattern LIST = Pattern.compile("^([ \\t]*)-[ \\t]+");
	private static final Pattern NUMBERED = Pattern.compile("^([ \\t]*\\d+[.)、])[ \\t]+");
	private BookText() {}

	public static Layout layout(NoticePayloads.Body body, Font font, int width) {
		var result = new ArrayList<Line>();
		int top = 0;
		for (String line : body.text().split("\n", -1)) {
			float scale = 1F;
			MutableComponent text;
			if (!body.markdown()) {
				line = NUMBERED.matcher(line).replaceFirst("$1\u00A0");
				text = Component.literal(line);
			} else {
				boolean heading = line.matches("#{1,4} .*");
				if (heading) {
					scale = switch (line.indexOf(' ')) {
						case 1 -> 1.5F;
						case 2 -> 1.25F;
						default -> 1F;
					};
					line = line.substring(line.indexOf(' ') + 1);
				} else {
					line = LIST.matcher(line).replaceFirst("$1•\u00A0");
					line = NUMBERED.matcher(line).replaceFirst("$1\u00A0");
				}
				text = inline(line);
				if (heading)
					text.withStyle(style -> style.withBold(true).withColor(0x6B4326));
			}
			int height = (int)Math.ceil((font.lineHeight + 4) * scale);
			var wrapped =
			    line.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : font.split(text, (int)(width / scale));
			for (var part : wrapped) {
				result.add(new Line(part, top, scale));
				top += height;
			}
		}
		return new Layout(List.copyOf(result), top);
	}

	public record Line(FormattedCharSequence text, int top, float scale) {}

	public record Layout(List<Line> lines, int height) {
		public static final Layout EMPTY = new Layout(List.of(), 0);

		public void render(GuiGraphicsExtractor graphics, Font font, int left, int top, int bottom, int scroll) {
			int low = 0, high = lines.size();
			while (low < high) {
				int mid = (low + high) >>> 1;
				if (lines.get(mid).top() < scroll)
					low = mid + 1;
				else
					high = mid;
			}
			for (int i = Math.max(0, low - 1); i < lines.size(); i++) {
				var line = lines.get(i);
				int y = top + line.top() - scroll;
				if (y >= bottom)
					break;
				graphics.pose().pushMatrix();
				graphics.pose().translate(left, y);
				graphics.pose().scale(line.scale(), line.scale());
				graphics.text(font, line.text(), 0, 0, PaperStyle.INK, false);
				graphics.pose().popMatrix();
			}
		}
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
