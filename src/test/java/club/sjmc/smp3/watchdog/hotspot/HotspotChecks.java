package club.sjmc.smp3.watchdog.hotspot;

import java.util.List;

import club.sjmc.smp3.watchdog.WatchdogConfig;

public final class HotspotChecks {
	public static void run() {
		var defaults = WatchdogConfig.parse(WatchdogConfig.DEFAULTS);
		assert !defaults.enabled() && defaults.rules().getFirst().entitiesPerChunk() == 256;
		for (String invalid :
		     List.of(WatchdogConfig.DEFAULTS.replace("false", "0"), WatchdogConfig.DEFAULTS.replace("128", "256"),
		             WatchdogConfig.DEFAULTS.replace("240", "-1"), WatchdogConfig.DEFAULTS.replace("240", "1.5"),
		             WatchdogConfig.DEFAULTS.replace("blacklist", "mode"))) {
			try {
				WatchdogConfig.parse(invalid);
				throw new AssertionError("Invalid watchdog configuration accepted");
			} catch (IllegalArgumentException expected) {
			}
		}
		HotspotRecorder data = new HotspotRecorder(10);
		data.groups.add(new HotspotRecorder.Group("minecraft:overworld", -1L, "minecraft:hopper",
		                                          HotspotRecorder.Kind.BLOCK_ENTITY));
		data.groups.add(
		    new HotspotRecorder.Group("minecraft:overworld", 0, "minecraft:item", HotspotRecorder.Kind.ENTITY));
		data.samples = 2;
		data.tickStarts[0] = 0;
		data.tickEnds[0] = 100;
		data.tickStarts[1] = 200;
		data.tickEnds[1] = 300;
		data.segments = 3;
		data.starts[0] = 10;
		data.ends[0] = 30;
		data.groupIds[0] = 0;
		data.invocations[0] = true;
		data.starts[1] = 30;
		data.ends[1] = 70;
		data.groupIds[1] = 1;
		data.invocations[1] = true;
		data.starts[2] = 70;
		data.ends[2] = 90;
		data.groupIds[2] = 0;
		var result =
		    HotspotReport.summarize(data, List.of(new HotspotReport.Pause(20, 40), new HotspotReport.Pause(35, 50)));
		assert result.total() == 200 && result.jvm() == 30 && result.unattributed() == 120;
		assert result.rows().getFirst().nanos() == 30 && result.rows().getFirst().updates() == 1;
		assert result.rows().get(1).nanos() == 20 && result.rows().get(1).maximum() == 20;
		assert result.samples() == 2;
		assert HotspotReport.summarize(data, List.of()).jvm() == 0;
		assert HotspotReport.format(data, List.of(), 0, true).stream().anyMatch(line -> line.contains("X=-16 Z=-16"));

		HotspotRecorder nested = new HotspotRecorder(10);
		nested.beginTick(true);
		int parent = nested.enter("world", 0, "parent", HotspotRecorder.Kind.BLOCK);
		int child = nested.enter("world", 0, "child", HotspotRecorder.Kind.NEIGHBOR);
		nested.exit(child);
		nested.exit(parent);
		nested.endTick();
		var calls = HotspotReport.summarize(nested, List.of());
		assert calls.rows().size() == 2 && calls.rows().stream().allMatch(row -> row.updates() == 1);
		assert calls.rows().stream().mapToLong(HotspotReport.Row::nanos).sum() <= calls.total();
		nested.beginTick(true);
		for (int i = 0; i < 20_001; i++) {
			int token = nested.enter("world", 0, "parent", HotspotRecorder.Kind.BLOCK);
			nested.exit(token);
		}
		nested.endTick();
		assert nested.samples == 1 && nested.discarded == 1 && nested.segments == 7;
	}
}
