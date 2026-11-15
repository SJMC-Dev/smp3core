package club.sjmc.smp3.watchdog.hotspot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public final class HotspotRecorder {
	public enum Kind { ENTITY, BLOCK_ENTITY, BLOCK, BLOCK_EVENT, FLUID, NEIGHBOR, SHAPE, PLAYER }
	record Group(String dimension, long chunk, String type, Kind kind) {}
	private static final int MAX_SEGMENTS = 262_144;
	private static final int MAX_GROUPS = 8192;
	private static final int MAX_UPDATES_PER_TICK = 20_000;
	final ArrayList<Group> groups = new ArrayList<>();
	private final Map<Object, Long2ObjectOpenHashMap<Object2IntOpenHashMap<Object>[]>> lookup = new HashMap<>();
	final long[] starts = new long[MAX_SEGMENTS], ends = new long[MAX_SEGMENTS];
	final int[] groupIds = new int[MAX_SEGMENTS], ticks = new int[MAX_SEGMENTS];
	final boolean[] invocations = new boolean[MAX_SEGMENTS];
	final long[] tickStarts = new long[4096], tickEnds = new long[4096];
	private final int[] stack = new int[128];
	private final boolean[] first = new boolean[128];
	final Instant originInstant = Instant.now();
	final long originNanos = System.nanoTime();
	private final long deadline;
	private int depth, tickOffset, updates;
	int segments, samples, discarded, observedTicks;
	private long resumed;
	long finishedNanos;
	private boolean selected, invalid;
	boolean limited;
	public HotspotRecorder(int seconds) { deadline = originNanos + seconds * 1_000_000_000L; }

	public boolean expired() {
		finishedNanos = System.nanoTime();
		return limited || finishedNanos >= deadline;
	}
	public boolean active() { return selected && !invalid; }

	public void beginTick() {
		observedTicks++;
		beginTick(ThreadLocalRandom.current().nextInt(10) == 0);
	}

	void beginTick(boolean select) {
		if (samples == tickStarts.length)
			limited = true;
		selected = select && !limited;
		if (!selected)
			return;
		tickStarts[samples] = System.nanoTime();
		tickOffset = segments;
		depth = 0;
		updates = 0;
		invalid = false;
	}

	// Callers enter only while active; exclusive segments account for nested updates and JVM pauses once.
	public void enter(Object dimension, long chunk, Object type, Kind kind) {
		long start = System.nanoTime();
		if (++updates > MAX_UPDATES_PER_TICK || depth == stack.length) {
			invalid = true;
			limited = true;
			return;
		}
		closeSegment(start);
		int group = group(dimension, chunk, type, kind);
		if (group < 0 || invalid) {
			invalid = true;
			limited = true;
			return;
		}
		int token = depth++;
		stack[token] = group;
		first[token] = true;
		resumed = System.nanoTime();
		append(start, resumed, -1, false);
	}

	public void exit() {
		// Cancellable injections from other mods can leave an unmatched return hook.
		if (invalid || depth == 0) {
			invalid = true;
			return;
		}
		long start = System.nanoTime();
		closeSegment(start);
		depth--;
		resumed = System.nanoTime();
		append(start, resumed, -1, false);
	}

	private void closeSegment(long end) {
		if (depth == 0)
			return;
		append(resumed, end, stack[depth - 1], first[depth - 1]);
		first[depth - 1] = false;
	}

	private void append(long start, long end, int group, boolean invocation) {
		if (invalid)
			return;
		if (segments == MAX_SEGMENTS) {
			invalid = true;
			limited = true;
			return;
		}
		starts[segments] = start;
		ends[segments] = end;
		groupIds[segments] = group;
		ticks[segments] = samples;
		invocations[segments] = invocation;
		segments++;
	}

	@SuppressWarnings("unchecked")
	private int group(Object dimension, long chunk, Object type, Kind kind) {
		var world = lookup.computeIfAbsent(dimension, ignored -> new Long2ObjectOpenHashMap<>());
		var kinds = world.get(chunk);
		if (kinds == null) {
			kinds = new Object2IntOpenHashMap[Kind.values().length];
			world.put(chunk, kinds);
		}
		var types = kinds[kind.ordinal()];
		if (types == null) {
			types = new Object2IntOpenHashMap<>();
			types.defaultReturnValue(-1);
			kinds[kind.ordinal()] = types;
		}
		int id = types.getInt(type);
		if (id >= 0)
			return id;
		if (groups.size() == MAX_GROUPS)
			return -1;
		id = groups.size();
		groups.add(new Group(dimension.toString(), chunk, type.toString(), kind));
		types.put(type, id);
		return id;
	}

	public void endTick() {
		if (!selected)
			return;
		if (invalid || depth != 0) {
			segments = tickOffset;
			discarded++;
		} else {
			tickEnds[samples++] = System.nanoTime();
		}
		selected = false;
	}

	String coverage() {
		return samples + " sampled ticks / " + observedTicks + ", discarded=" + discarded +
		    (limited ? " (INCOMPLETE: recording limit)" : "");
	}
}
