package club.sjmc.smp3.watchdog.hotspot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

public final class HotspotReport {
	private HotspotReport() {}
	public record Pause(long start, long end) {}
	record Row(HotspotRecorder.Group group, long nanos, long maximum, long updates) {}
	record Result(List<Row> rows, int samples, long total, long jvm, long overhead, long unattributed) {}

	static Result summarize(HotspotRecorder data, List<Pause> rawPauses) {
		List<Pause> pauses = merge(rawPauses);
		long[] total = new long[data.groups.size()], maximum = new long[total.length], updates = new long[total.length];
		long[] current = new long[total.length];
		int tick = -1;
		long overhead = 0;
		for (int i = 0; i < data.segments; i++) {
			if (tick != data.ticks[i]) {
				for (int g = 0; g < total.length; g++) {
					maximum[g] = Math.max(maximum[g], current[g]);
					current[g] = 0;
				}
				tick = data.ticks[i];
			}
			int group = data.groupIds[i];
			long duration = data.ends[i] - data.starts[i] - overlap(pauses, data.starts[i], data.ends[i]);
			if (group < 0) {
				overhead += duration;
				continue;
			}
			total[group] += duration;
			current[group] += duration;
			if (data.invocations[i])
				updates[group]++;
		}
		List<Row> rows = new ArrayList<>();
		long attributed = 0;
		for (int g = 0; g < total.length; g++) {
			maximum[g] = Math.max(maximum[g], current[g]);
			attributed += total[g];
			if (updates[g] > 0)
				rows.add(new Row(data.groups.get(g), total[g], maximum[g], updates[g]));
		}
		rows.sort(Comparator.comparingLong(Row::nanos).reversed());
		long elapsed = 0, jvm = 0;
		for (int i = 0; i < data.samples; i++) {
			elapsed += data.tickEnds[i] - data.tickStarts[i];
			jvm += overlap(pauses, data.tickStarts[i], data.tickEnds[i]);
		}
		return new Result(List.copyOf(rows), data.samples, elapsed, jvm, overhead,
		                  Math.max(0, elapsed - jvm - attributed));
	}

	static List<Pause> merge(List<Pause> input) {
		List<Pause> sorted = new ArrayList<>(input);
		sorted.sort(Comparator.comparingLong(Pause::start));
		List<Pause> result = new ArrayList<>();
		for (Pause pause : sorted) {
			if (pause.end <= pause.start)
				continue;
			if (!result.isEmpty() && pause.start <= result.getLast().end) {
				Pause previous = result.removeLast();
				result.add(new Pause(previous.start, Math.max(previous.end, pause.end)));
			} else
				result.add(pause);
		}
		return result;
	}

	static long overlap(List<Pause> pauses, long start, long end) {
		long sum = 0;
		for (Pause pause : pauses) {
			if (pause.start >= end)
				break;
			sum += Math.max(0, Math.min(end, pause.end) - Math.max(start, pause.start));
		}
		return sum;
	}

	public static List<Component> format(HotspotRecorder data, List<Pause> pauses, double minimum,
	                                     boolean jvmAvailable) {
		Result result = summarize(data, pauses);
		List<Component> lines = new ArrayList<>();
		String coverage = data.coverage() + "\nEntity/block-entity ticks, scheduled/neighbor updates and block events."
		                  + "\nHover entries for IDs, peak times and update counts.";
		lines.add(
		    Component
		        .literal(String.format(Locale.ROOT, "SMP3 top: %.1fs", (data.finishedNanos - data.originNanos) / 1e9))
		        .withStyle(ChatFormatting.AQUA)
		        .append(Component.literal("  " + data.samples + "/" + data.observedTicks + " ticks")
				            .withStyle(ChatFormatting.GRAY))
		        .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(coverage)))));

		if (data.discarded > 0 || data.limited)
			lines.add(Component
			              .literal("Incomplete: " + data.discarded + " discarded ticks" +
			                       (data.limited ? "; recording limit reached" : ""))
			              .withStyle(ChatFormatting.YELLOW));
		if (result.samples == 0) {
			lines.add(
			    Component.literal("No complete samples. Try a longer recording.").withStyle(ChatFormatting.YELLOW));
			return lines;
		}
		double divisor = result.samples * 1e6;
		String accounting = String.format(Locale.ROOT,
		                                  "All values are ms per sampled tick.\nJVM: observed GC pauses only."
		                                      + "\nOther: unattributed game work and external delays."
		                                      + "\nProbe bookkeeping: %.3f ms/t (included in Other).",
		                                  result.overhead / divisor);
		lines.add(
		    Component
		        .literal(String.format(Locale.ROOT, "Sampled %.2f ms/t | JVM %s | Other %.2f", result.total / divisor,
				                       jvmAvailable ? String.format(Locale.ROOT, "%.2f", result.jvm / divisor) : "n/a",
				                       result.unattributed / divisor))
		        .withStyle(ChatFormatting.GRAY)
		        .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(accounting)))));

		int shown = 0;
		for (Row row : result.rows) {
			if (row.nanos / divisor < minimum || shown == 10)
				break;
			shown++;
			GroupPosition position = position(row.group.chunk());
			String dimension = row.group.dimension().startsWith("minecraft:")
			                       ? row.group.dimension().substring("minecraft:".length())
			                       : row.group.dimension();
			String details =
			    String.format(Locale.ROOT, "%s (%s) — %s\n%s X=%d Z=%d\n%.3f ms/t (%.1f%%), max %.3f ms, %d updates",
				              name(row.group), row.group.type(), operation(row.group.kind()), row.group.dimension(),
				              position.x, position.z, row.nanos / divisor,
				              result.total == 0 ? 0 : row.nanos * 100.0 / result.total, row.maximum / 1e6, row.updates);
			lines.add(
			    Component.literal(shown + ". ")
			        .withStyle(ChatFormatting.DARK_GRAY)
			        .append(Component.literal(name(row.group)).withStyle(ChatFormatting.WHITE))
			        .append(Component.literal(" / " + operation(row.group.kind())).withStyle(ChatFormatting.GRAY))
			        .append(Component.literal(String.format(Locale.ROOT, "  %.2f ms/t", row.nanos / divisor))
					            .withStyle(ChatFormatting.AQUA))
			        .append(Component.literal("\n   " + dimension + "  X=" + position.x + " Z=" + position.z)
					            .withStyle(ChatFormatting.GRAY))
			        .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(details)))));
		}
		if (shown == 0)
			lines.add(Component.literal("No hotspots above the display threshold.").withStyle(ChatFormatting.GRAY));
		return List.copyOf(lines);
	}

	private record GroupPosition(int x, int z) {}
	private static GroupPosition position(long chunk) {
		return new GroupPosition((int)chunk * 16, (int)(chunk >>> 32) * 16);
	}
	private static String name(HotspotRecorder.Group group) {
		return group.type().equals("minecraft:item")
		    ? "Dropped item"
			: group.type().substring(group.type().indexOf(':') + 1).replace('_', ' ');
	}

	private static String operation(HotspotRecorder.Kind kind) {
		return switch (kind) {
			case ENTITY -> "entity tick";
			case PLAYER -> "player update";
			case BLOCK_ENTITY -> "block-entity tick";
			case BLOCK -> "scheduled block tick";
			case BLOCK_EVENT -> "block event";
			case FLUID -> "scheduled fluid tick";
			case NEIGHBOR -> "neighbor update";
			case SHAPE -> "neighbor shape update";
		};
	}
}
