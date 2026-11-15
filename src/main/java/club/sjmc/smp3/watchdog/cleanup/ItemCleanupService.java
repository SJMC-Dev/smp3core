package club.sjmc.smp3.watchdog.cleanup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.PriorityQueue;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import club.sjmc.smp3.watchdog.WatchdogConfig;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

public final class ItemCleanupService {
	private static final int INSPECTIONS = 128;
	private static final int REMOVALS = 32;
	private static final long BUDGET_NANOS = 500_000;
	private final Map<ItemEntity, Entry> entries = new IdentityHashMap<>();
	private final Map<ServerLevel, Long2ObjectOpenHashMap<Bucket>> worlds = new IdentityHashMap<>();
	private final ArrayList<Bucket> buckets = new ArrayList<>();
	private final PriorityQueue<Candidate> candidates =
	    new PriorityQueue<>(Comparator.comparingLong(Candidate::born)
		                        .reversed()
		                        .thenComparing(Comparator.comparingInt(Candidate::count).reversed()));
	private WatchdogConfig.Policy policy;
	private Bucket scanning;
	private int cursor, offset, scanLimit;
	private long removedEntities, removedItems, processingNanos;

	private static final class Bucket {
		final ServerLevel level;
		final long chunk;
		final ArrayList<Entry> items = new ArrayList<>();
		int index;
		Bucket(ServerLevel level, long chunk, int index) {
			this.level = level;
			this.chunk = chunk;
			this.index = index;
		}
	}
	private static final class Entry {
		final ItemEntity item;
		Bucket bucket;
		int index;
		Entry(ItemEntity item) { this.item = item; }
	}
	private record Candidate(ItemEntity item, long born, int count) {}
	public record Statistics(int tracked, long removedEntities, long removedItems, long processingNanos) {}

	public void configure(WatchdogConfig.Policy policy) {
		this.policy = policy;
		scanning = null;
		candidates.clear();
	}

	public Statistics statistics() {
		return new Statistics(entries.size(), removedEntities, removedItems, processingNanos);
	}

	public void add(ItemEntity item) {
		if (entries.containsKey(item))
			return;
		Entry entry = new Entry(item);
		entries.put(item, entry);
		attach(entry);
	}

	public void remove(ItemEntity item) {
		Entry entry = entries.remove(item);
		if (entry != null)
			detach(entry);
	}

	public void moved(ItemEntity item) {
		Entry entry = entries.get(item);
		if (entry != null &&
		    (entry.bucket.chunk != item.chunkPosition().pack() || entry.bucket.level != item.level())) {
			detach(entry);
			attach(entry);
		}
	}

	private void attach(Entry entry) {
		ServerLevel level = (ServerLevel)entry.item.level();
		long chunk = entry.item.chunkPosition().pack();
		var world = worlds.computeIfAbsent(level, ignored -> new Long2ObjectOpenHashMap<>());
		Bucket bucket = world.get(chunk);
		if (bucket == null) {
			bucket = new Bucket(level, chunk, buckets.size());
			world.put(chunk, bucket);
			buckets.add(bucket);
		}
		entry.bucket = bucket;
		entry.index = bucket.items.size();
		bucket.items.add(entry);
	}

	private void detach(Entry entry) {
		Bucket bucket = entry.bucket;
		Entry last = bucket.items.removeLast();
		if (last != entry) {
			bucket.items.set(entry.index, last);
			last.index = entry.index;
		}
		if (bucket.items.isEmpty()) {
			worlds.get(bucket.level).remove(bucket.chunk);
			if (worlds.get(bucket.level).isEmpty())
				worlds.remove(bucket.level);
			Bucket tail = buckets.removeLast();
			if (tail != bucket) {
				buckets.set(bucket.index, tail);
				tail.index = bucket.index;
			}
		}
	}

	public void tick() {
		if (policy == null || !policy.enabled() || buckets.isEmpty())
			return;
		long start = System.nanoTime();
		int inspected = 0, removed = 0, visited = 0;
		while (!buckets.isEmpty() && inspected < INSPECTIONS && removed < REMOVALS &&
		       System.nanoTime() - start < BUDGET_NANOS) {
			if (scanning == null || scanning.items.isEmpty()) {
				if (visited++ == buckets.size())
					break;
				scanning = buckets.get(Math.floorMod(cursor++, buckets.size()));
				scanLimit = scanning.items.size();
				offset = 0;
				candidates.clear();
			}
			Bucket bucket = scanning;
			int age = policy.minimumAge(bucket.items.size());
			if (age == Integer.MAX_VALUE ||
			    !bucket.level.isPositionEntityTicking(bucket.items.getFirst().item.blockPosition())) {
				scanning = null;
				inspected++;
				continue;
			}
			if (offset < Math.min(scanLimit, bucket.items.size())) {
				ItemEntity item = bucket.items.get(offset++).item;
				inspected++;
				if (eligible(item, age)) {
					Candidate candidate =
					    new Candidate(item, bucket.level.getGameTime() - item.getAge(), item.getItem().getCount());
					if (candidates.size() < REMOVALS)
						candidates.add(candidate);
					else if (candidates.comparator().compare(candidate, candidates.peek()) > 0) {
						candidates.remove();
						candidates.add(candidate);
					}
				}
				continue;
			}
			var ordered = new ArrayList<>(candidates);
			ordered.sort(candidates.comparator().reversed());
			for (Candidate candidate : ordered) {
				if (removed == REMOVALS || System.nanoTime() - start >= BUDGET_NANOS)
					break;
				ItemEntity item = candidate.item();
				Entry entry = entries.get(item);
				age = policy.minimumAge(bucket.items.size());
				if (entry == null || entry.bucket != bucket || item.chunkPosition().pack() != bucket.chunk ||
				    !eligible(item, age))
					continue;
				removedItems += item.getItem().getCount();
				removedEntities++;
				removed++;
				item.discard();
				remove(item);
			}
			scanning = null;
			candidates.clear();
		}
		processingNanos += System.nanoTime() - start;
	}

	private boolean eligible(ItemEntity item, int age) {
		return item.isAlive() && item.getAge() >= age &&
		    ((ServerLevel)item.level()).isPositionEntityTicking(item.blockPosition()) &&
		    policy.eligible(item.getItem().getItem());
	}

	public void clear() {
		entries.clear();
		worlds.clear();
		buckets.clear();
		candidates.clear();
		scanning = null;
	}
}
