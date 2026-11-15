package club.sjmc.smp3.watchdog.hotspot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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

	public static List<String> format(HotspotRecorder data, List<Pause> pauses, double minimum, boolean jvmAvailable) {
		Result result = summarize(data, pauses);
		List<String> lines = new ArrayList<>();
		lines.add(String.format(Locale.ROOT, "SMP3 top: %.1fs; %s", (data.finishedNanos - data.originNanos) / 1e9,
		                        data.coverage()));
		if (result.samples == 0) {
			lines.add("No complete sampled ticks; check the coverage and recording limit above before retrying.");
			return lines;
		}
		double divisor = result.samples * 1e6;
		lines.add(String.format(
		    Locale.ROOT, "Sampled tick time %.3f ms/tick; probe bookkeeping %.3f ms/tick (included in Unattributed)",
		    result.total / divisor, result.overhead / divisor));
		lines.add(String.format(Locale.ROOT, "JVM — observed GC pause: %s; Unattributed: %.3f ms/tick",
		                        jvmAvailable ? String.format(Locale.ROOT, "%.3f ms/tick", result.jvm / divisor)
		                                     : "unavailable",
		                        result.unattributed / divisor));
		int shown = 0;
		for (Row row : result.rows) {
			if (row.nanos / divisor < minimum || shown == 10)
				break;
			shown++;
			GroupPosition position = position(row.group.chunk());
			lines.add(String.format(
			    Locale.ROOT, "%d. %s X=%d Z=%d | %s | %.3f ms/tick (%.1f%%), max %.3f ms, %d updates", shown,
			    row.group.dimension(), position.x, position.z, label(row.group), row.nanos / divisor,
			    result.total == 0 ? 0 : row.nanos * 100.0 / result.total, row.maximum / 1e6, row.updates));
		}
		if (shown == 0)
			lines.add("No measured groups above the display threshold.");
		lines.add("Coverage: entity/block-entity and scheduled/neighbor updates; other game work and external delays "
		          + "remain Unattributed. Counts are update invocations.");
		return List.copyOf(lines);
	}

	private record GroupPosition(int x, int z) {}
	private static GroupPosition position(long chunk) {
		return new GroupPosition((int)chunk * 16, (int)(chunk >>> 32) * 16);
	}
	private static String label(HotspotRecorder.Group group) {
		String name = group.type().equals("minecraft:item")
		                  ? "Dropped item"
		                  : group.type().substring(group.type().indexOf(':') + 1).replace('_', ' ');
		String operation = switch (group.kind()) {
			case ENTITY -> "entity tick";
			case PLAYER -> "player update";
			case BLOCK_ENTITY -> "block-entity tick";
			case BLOCK -> "scheduled block tick";
			case FLUID -> "scheduled fluid tick";
			case NEIGHBOR -> "neighbor update";
			case SHAPE -> "neighbor shape update";
		};
		return name + " (" + group.type() + ") — " + operation;
	}
}
